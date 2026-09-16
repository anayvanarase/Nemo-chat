import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-threaded TCP Chat Server supporting up to 100 concurrent clients.
 * Handles client registrations, broadcasting, direct messaging, and capacity limits.
 */
public class Server {
    public static final int DEFAULT_PORT = 5000;
    public static final int MAX_CLIENTS = 100;
    private static final int MAX_HISTORY = 20;

    // ANSI Color constants for server/client formatting
    public static final String COLOR_RESET = "\u001B[0m";
    public static final String COLOR_YELLOW = "\u001B[33m";
    public static final String COLOR_GREEN = "\u001B[32m";
    public static final String COLOR_CYAN = "\u001B[36m";
    public static final String COLOR_RED = "\u001B[31m";
    public static final String COLOR_BOLD = "\u001B[1m";

    private final int port;
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final AtomicInteger activeClients = new AtomicInteger(0);
    private final List<String> messageHistory = Collections.synchronizedList(new LinkedList<>());
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port: " + DEFAULT_PORT);
            }
        }
        new Server(port).start();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            printBanner();

            // Background thread to handle server console commands
            Thread consoleThread = new Thread(this::handleServerConsoleInput, "Server-Console-Thread");
            consoleThread.setDaemon(true);
            consoleThread.start();

            // Client connection accept loop
            while (running) {
                try {
                    Socket socket = serverSocket.accept();

                    // Strict check for maximum capacity
                    if (activeClients.get() >= MAX_CLIENTS) {
                        try (PrintWriter tempOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
                            tempOut.println(COLOR_RED + "[SERVER FULL] The chatroom is at maximum capacity (" 
                                    + MAX_CLIENTS + " clients). Please try again later." + COLOR_RESET);
                        }
                        socket.close();
                        log("[Capacity Exceeded] Rejected connection from " + socket.getRemoteSocketAddress());
                        continue;
                    }

                    // Hand off accepted connection to a ClientHandler
                    ClientHandler handler = new ClientHandler(socket, this);
                    clientThreadPool.execute(handler);

                } catch (SocketException e) {
                    if (!running) {
                        break; // Server is shutting down
                    }
                    log("Socket error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log("Error starting server: " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Registers a client with a validated unique username.
     */
    public synchronized boolean registerClient(String username, ClientHandler handler) {
        if (activeClients.get() >= MAX_CLIENTS) {
            return false;
        }

        // Case-insensitive check to avoid duplicate names like 'Alice' and 'alice'
        for (String existing : clients.keySet()) {
            if (existing.equalsIgnoreCase(username)) {
                return false;
            }
        }

        clients.put(username, handler);
        activeClients.incrementAndGet();
        log("Client registered: " + username + " (" + activeClients.get() + "/" + MAX_CLIENTS + " online)");
        return true;
    }

    /**
     * Removes a client upon disconnection.
     */
    public synchronized void removeClient(String username, ClientHandler handler) {
        if (username != null && clients.remove(username) != null) {
            activeClients.decrementAndGet();
            log("Client departed: " + username + " (" + activeClients.get() + "/" + MAX_CLIENTS + " online)");
            broadcastSystem(username + " has left the chatroom. (" + activeClients.get() + "/" + MAX_CLIENTS + " online)");
        }
    }

    /**
     * Broadcasts a regular chat message to all connected clients.
     */
    public void broadcast(String message, String senderUsername) {
        String timestamp = LocalTime.now().format(timeFormatter);
        String formatted = "[" + timestamp + "] " + COLOR_GREEN + "[" + senderUsername + "]" + COLOR_RESET + ": " + message;
        
        addToHistory(formatted);
        log("[CHAT] [" + senderUsername + "]: " + message);

        for (ClientHandler client : clients.values()) {
            client.sendMessage(formatted);
        }
    }

    /**
     * Broadcasts a system announcement or notification to all connected clients.
     */
    public void broadcastSystem(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        String formatted = COLOR_YELLOW + "[" + timestamp + "] [SYSTEM]: " + message + COLOR_RESET;
        
        addToHistory(formatted);
        log("[SYSTEM]: " + message);

        for (ClientHandler client : clients.values()) {
            client.sendMessage(formatted);
        }
    }

    /**
     * Sends a private direct message between two users.
     */
    public boolean sendPrivateMessage(String sender, String recipientUsername, String message) {
        ClientHandler recipientHandler = null;
        String resolvedRecipientName = null;

        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(recipientUsername)) {
                resolvedRecipientName = entry.getKey();
                recipientHandler = entry.getValue();
                break;
            }
        }

        if (recipientHandler == null || resolvedRecipientName == null) {
            return false;
        }

        String timestamp = LocalTime.now().format(timeFormatter);
        String toRecipient = COLOR_CYAN + "[" + timestamp + "] [Whisper from " + sender + "]: " + message + COLOR_RESET;
        String toSender = COLOR_CYAN + "[" + timestamp + "] [Whisper to " + resolvedRecipientName + "]: " + message + COLOR_RESET;

        recipientHandler.sendMessage(toRecipient);
        ClientHandler senderHandler = clients.get(sender);
        if (senderHandler != null) {
            senderHandler.sendMessage(toSender);
        }

        log("[PM] " + sender + " -> " + resolvedRecipientName + ": " + message);
        return true;
    }

    /**
     * Returns a snapshot of online usernames.
     */
    public List<String> getOnlineUsernames() {
        return new ArrayList<>(clients.keySet());
    }

    /**
     * Returns the active client count.
     */
    public int getActiveCount() {
        return activeClients.get();
    }

    /**
     * Returns a copy of the recent chat message history.
     */
    public List<String> getRecentHistory() {
        synchronized (messageHistory) {
            return new ArrayList<>(messageHistory);
        }
    }

    private void addToHistory(String message) {
        synchronized (messageHistory) {
            if (messageHistory.size() >= MAX_HISTORY) {
                messageHistory.remove(0);
            }
            messageHistory.add(message);
        }
    }

    /**
     * Kicks a user from the server.
     */
    public boolean kickUser(String username, String reason) {
        ClientHandler handler = null;
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(username)) {
                handler = entry.getValue();
                break;
            }
        }

        if (handler != null) {
            String kickMessage = COLOR_RED + "[SYSTEM] You have been kicked by the server admin." 
                    + (reason != null && !reason.isEmpty() ? " Reason: " + reason : "") + COLOR_RESET;
            handler.sendMessage(kickMessage);
            handler.closeConnection();
            broadcastSystem(username + " was kicked from the chatroom.");
            return true;
        }
        return false;
    }

    /**
     * Interactive console commands for the server administrator.
     */
    private void handleServerConsoleInput() {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (running && (line = consoleReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("/stop") || line.equalsIgnoreCase("/exit")) {
                    log("Stopping server...");
                    stop();
                    System.exit(0);
                } else if (line.equalsIgnoreCase("/users")) {
                    List<String> users = getOnlineUsernames();
                    System.out.println("Online users (" + users.size() + "/" + MAX_CLIENTS + "): " + String.join(", ", users));
                } else if (line.toLowerCase().startsWith("/broadcast ")) {
                    String msg = line.substring(11).trim();
                    if (!msg.isEmpty()) {
                        broadcastSystem("[Announcement] " + msg);
                    }
                } else if (line.toLowerCase().startsWith("/kick ")) {
                    String[] parts = line.substring(6).trim().split("\\s+", 2);
                    String userToKick = parts[0];
                    String reason = parts.length > 1 ? parts[1] : "No reason specified";
                    if (!kickUser(userToKick, reason)) {
                        System.out.println("User '" + userToKick + "' not found.");
                    }
                } else if (line.equalsIgnoreCase("/help")) {
                    printServerHelp();
                } else {
                    System.out.println("Unknown command. Type /help for server commands.");
                }
            }
        } catch (IOException e) {
            if (running) {
                log("Server console error: " + e.getMessage());
            }
        }
    }

    private void printBanner() {
        System.out.println("==================================================");
        System.out.println("          TCP CHAT SERVER STARTED                ");
        System.out.println("  Port: " + port);
        System.out.println("  Max Clients: " + MAX_CLIENTS);
        System.out.println("  Type /help in this console for server commands  ");
        System.out.println("==================================================");
    }

    private void printServerHelp() {
        System.out.println("Server Console Commands:");
        System.out.println("  /users                  - List currently connected users");
        System.out.println("  /broadcast <message>    - Broadcast an announcement to all clients");
        System.out.println("  /kick <username> [msg]  - Disconnect a user with an optional reason");
        System.out.println("  /stop                   - Gracefully shutdown the server");
        System.out.println("  /help                   - Show this help menu");
    }

    /**
     * Gracefully stops the server, closing all client connections and releasing resources.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;

        broadcastSystem("Server is shutting down. All sessions closed.");

        // Disconnect all clients
        for (ClientHandler handler : clients.values()) {
            handler.closeConnection();
        }
        clients.clear();

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }

        clientThreadPool.shutdownNow();
        log("Server stopped successfully.");
    }

    private void log(String message) {
        String timestamp = LocalTime.now().format(timeFormatter);
        System.out.println("[" + timestamp + "] " + message);
    }
}
