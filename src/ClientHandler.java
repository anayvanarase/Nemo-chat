import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Handles communication with an individual connected TCP client.
 * Manages username authentication handshake, chat commands, and message dispatching.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Step 1: Username Handshake
            if (!performUsernameHandshake()) {
                return;
            }

            // Step 2: Deliver recent chat history backlog (if any)
            sendRecentHistory();

            // Step 3: Broadcast arrival to all participants
            server.broadcastSystem(username + " has joined the chatroom. (" 
                    + server.getActiveCount() + "/" + Server.MAX_CLIENTS + " online)");

            // Step 4: Active Chat Message Loop
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("/exit")) {
                    sendMessage(Server.COLOR_YELLOW + "[SYSTEM] You have disconnected. Goodbye!" + Server.COLOR_RESET);
                    break;
                } else if (line.equalsIgnoreCase("/users") || line.equalsIgnoreCase("/list")) {
                    sendUserList();
                } else if (line.toLowerCase().startsWith("/w ") || line.toLowerCase().startsWith("/msg ")) {
                    handleWhisper(line);
                } else if (line.equalsIgnoreCase("/help")) {
                    sendHelpMenu();
                } else if (line.equalsIgnoreCase("/history")) {
                    sendRecentHistory();
                } else if (line.startsWith("/")) {
                    sendMessage(Server.COLOR_RED + "[SYSTEM] Unknown command: " + line + ". Type /help for commands." + Server.COLOR_RESET);
                } else {
                    server.broadcast(line, this.username);
                }
            }

        } catch (IOException e) {
            // Connection dropped or socket closed
        } finally {
            cleanup();
        }
    }

    /**
     * Conducts username negotiation with the client, rejecting invalid or duplicate names.
     */
    private boolean performUsernameHandshake() throws IOException {
        out.println("AUTH_REQ Welcome to the Chatroom! Please enter your username:");

        while (isRunning) {
            String candidate = in.readLine();
            if (candidate == null) {
                return false; // Client closed socket before finishing handshake
            }

            candidate = candidate.trim();

            if (candidate.isEmpty()) {
                out.println("AUTH_ERR Username cannot be blank. Enter your username:");
                continue;
            }
            if (candidate.contains(" ")) {
                out.println("AUTH_ERR Username cannot contain spaces. Enter your username:");
                continue;
            }
            if (candidate.length() > 20) {
                out.println("AUTH_ERR Username must be 20 characters or fewer. Enter your username:");
                continue;
            }
            if (candidate.startsWith("/")) {
                out.println("AUTH_ERR Username cannot start with '/'. Enter your username:");
                continue;
            }
            if (candidate.equalsIgnoreCase("system") || candidate.equalsIgnoreCase("admin") || candidate.equalsIgnoreCase("server")) {
                out.println("AUTH_ERR '" + candidate + "' is a reserved name. Enter another username:");
                continue;
            }

            if (!server.registerClient(candidate, this)) {
                out.println("AUTH_ERR Username '" + candidate + "' is already taken or server is full. Try another:");
                continue;
            }

            this.username = candidate;
            out.println("AUTH_OK " + this.username);
            out.println(Server.COLOR_GREEN + "--------------------------------------------------");
            out.println("  Welcome to the Chatroom, " + this.username + "!");
            out.println("  Connected clients: " + server.getActiveCount() + "/" + Server.MAX_CLIENTS);
            out.println("  Type /help at any time to view available commands.");
            out.println("--------------------------------------------------" + Server.COLOR_RESET);
            return true;
        }
        return false;
    }

    private void handleWhisper(String commandLine) {
        // e.g. /w Alice Hello Alice!
        String[] tokens = commandLine.split("\\s+", 3);
        if (tokens.length < 3) {
            sendMessage(Server.COLOR_RED + "[SYSTEM] Usage: /w <username> <message>" + Server.COLOR_RESET);
            return;
        }

        String recipient = tokens[1];
        String message = tokens[2];

        if (recipient.equalsIgnoreCase(this.username)) {
            sendMessage(Server.COLOR_RED + "[SYSTEM] You cannot send a private message to yourself." + Server.COLOR_RESET);
            return;
        }

        boolean sent = server.sendPrivateMessage(this.username, recipient, message);
        if (!sent) {
            sendMessage(Server.COLOR_RED + "[SYSTEM] User '" + recipient + "' is not found or is offline." + Server.COLOR_RESET);
        }
    }

    private void sendUserList() {
        List<String> users = server.getOnlineUsernames();
        sendMessage(Server.COLOR_YELLOW + "=== Active Users (" + users.size() + "/" + Server.MAX_CLIENTS + ") ===" + Server.COLOR_RESET);
        for (String user : users) {
            if (user.equalsIgnoreCase(this.username)) {
                sendMessage(" - " + user + " (You)");
            } else {
                sendMessage(" - " + user);
            }
        }
    }

    private void sendRecentHistory() {
        List<String> history = server.getRecentHistory();
        if (!history.isEmpty()) {
            sendMessage(Server.COLOR_YELLOW + "--- Recent Chat History ---" + Server.COLOR_RESET);
            for (String msg : history) {
                sendMessage(msg);
            }
            sendMessage(Server.COLOR_YELLOW + "---------------------------" + Server.COLOR_RESET);
        }
    }

    private void sendHelpMenu() {
        sendMessage(Server.COLOR_YELLOW + "================ Chat Commands ================" + Server.COLOR_RESET);
        sendMessage("  <message>                - Broadcast message to everyone");
        sendMessage("  /w <username> <message>  - Send a private whisper to a user");
        sendMessage("  /users or /list          - List all currently online users");
        sendMessage("  /history                 - Display recent chat history");
        sendMessage("  /quit or /exit           - Leave the chatroom");
        sendMessage("  /help                    - Show this commands list");
        sendMessage(Server.COLOR_YELLOW + "===============================================" + Server.COLOR_RESET);
    }

    /**
     * Sends a raw line to the connected client.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Closes this client's socket connection.
     */
    public void closeConnection() {
        isRunning = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    private void cleanup() {
        isRunning = false;
        if (username != null) {
            server.removeClient(username, this);
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public String getUsername() {
        return username;
    }
}
