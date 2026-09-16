import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Terminal-based TCP Chat Client (no GUI).
 * Connects to the server, completes the username handshake,
 * and maintains concurrent message sending and receiving.
 */
public class Client {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    private final String host;
    private final int port;
    private volatile boolean isRunning = true;
    private volatile boolean intentionalQuit = false;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port specified. Using default: " + DEFAULT_PORT);
            }
        }

        new Client(host, port).start();
    }

    public void start() {
        System.out.println("Connecting to chat server at " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter serverOut = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
             Scanner consoleScanner = new Scanner(System.in)) {

            System.out.println("Connected to server successfully!\n");

            // Phase 1: Authentication / Username Negotiation
            if (!handleHandshake(serverIn, serverOut, consoleScanner)) {
                return;
            }

            // Phase 2: Start background receiver thread to print incoming messages
            Thread receiverThread = new Thread(() -> {
                try {
                    String incomingMessage;
                    while (isRunning && (incomingMessage = serverIn.readLine()) != null) {
                        System.out.println(incomingMessage);
                    }
                } catch (IOException e) {
                    if (isRunning && !intentionalQuit) {
                        System.out.println("\n[Connection to server lost]");
                    }
                } finally {
                    isRunning = false;
                }
            }, "Server-Receiver-Thread");

            receiverThread.setDaemon(true);
            receiverThread.start();

            // Phase 3: Main thread reads console input from user and transmits to server
            System.out.println("\nYou can now start typing messages. (Type /help for commands, /quit to exit)");

            while (isRunning && consoleScanner.hasNextLine()) {
                String input = consoleScanner.nextLine();

                if (!isRunning) {
                    break;
                }

                if (input.trim().equalsIgnoreCase("/quit") || input.trim().equalsIgnoreCase("/exit")) {
                    intentionalQuit = true;
                    isRunning = false;
                    serverOut.println("/quit");
                    break;
                }

                if (!input.trim().isEmpty()) {
                    serverOut.println(input);
                }
            }

            System.out.println("Disconnected. Have a great day!");

        } catch (UnknownHostException e) {
            System.err.println("Error: Host not found (" + host + "). Check your connection settings.");
        } catch (ConnectException e) {
            System.err.println("Error: Could not connect to server at " + host + ":" + port 
                    + ". Is the server running?");
        } catch (IOException e) {
            if (!intentionalQuit) {
                System.err.println("Communication error: " + e.getMessage());
            }
        }
    }

    /**
     * Conducts initial handshake with server for username validation.
     */
    private boolean handleHandshake(BufferedReader in, PrintWriter out, Scanner scanner) throws IOException {
        while (isRunning) {
            String serverMsg = in.readLine();
            if (serverMsg == null) {
                System.out.println("Server closed connection during handshake.");
                return false;
            }

            if (serverMsg.contains("[SERVER FULL]")) {
                System.out.println(serverMsg);
                return false;
            }

            if (serverMsg.startsWith("AUTH_REQ ")) {
                System.out.println(serverMsg.substring(9));
                System.out.print("> Username: ");
                String chosenName = scanner.nextLine().trim();
                out.println(chosenName);
            } else if (serverMsg.startsWith("AUTH_ERR ")) {
                System.out.println("\u001B[31m" + serverMsg.substring(9) + "\u001B[0m");
                System.out.print("> Username: ");
                String chosenName = scanner.nextLine().trim();
                out.println(chosenName);
            } else if (serverMsg.startsWith("AUTH_OK ")) {
                return true;
            } else {
                // Any intermediate announcement/banner line
                System.out.println(serverMsg);
            }
        }
        return false;
    }
}
