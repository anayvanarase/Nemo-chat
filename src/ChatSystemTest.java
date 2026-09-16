import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ChatSystemTest {
    private static final int TEST_PORT = 5055;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Starting Chat System Integration Tests ===");

        // 1. Start Server in background
        Server server = new Server(TEST_PORT);
        Thread serverThread = new Thread(server::start, "Test-Server-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(500); // Give server a moment to bind

        try {
            // Test 1: Connect Alice and Bob
            System.out.println("\n[Test 1] Testing User Registration Handshake...");
            SimulatedClient alice = new SimulatedClient("localhost", TEST_PORT, "Alice");
            alice.connect();
            Thread.sleep(200);

            SimulatedClient bob = new SimulatedClient("localhost", TEST_PORT, "Bob");
            bob.connect();
            Thread.sleep(200);

            // Alice should have received notification of Bob joining
            assertTrue(alice.hasReceivedMessageContaining("Bob has joined"), "Alice received Bob's join notification");

            // Test 2: Public Broadcast
            System.out.println("\n[Test 2] Testing Public Broadcast...");
            alice.sendMessage("Hello everyone from Alice!");
            Thread.sleep(300);

            assertTrue(bob.hasReceivedMessageContaining("Hello everyone from Alice!"),
                    "Bob received Alice's broadcast");

            // Test 3: Private Messaging (/w)
            System.out.println("\n[Test 3] Testing Whisper / Private Message...");
            SimulatedClient charlie = new SimulatedClient("localhost", TEST_PORT, "Charlie");
            charlie.connect();
            Thread.sleep(200);

            bob.sendMessage("/w Alice Hey Alice, this is a secret!");
            Thread.sleep(300);

            assertTrue(alice.hasReceivedMessageContaining("Whisper from Bob"), "Alice received whisper from Bob");
            assertTrue(!charlie.hasReceivedMessageContaining("secret"), "Charlie did NOT receive private message");

            // Test 4: User List (/users)
            System.out.println("\n[Test 4] Testing /users Command...");
            charlie.sendMessage("/users");
            Thread.sleep(300);
            assertTrue(charlie.hasReceivedMessageContaining("Active Users (3/100)"), "Charlie saw 3 active users");

            // Test 5: Disconnect Handling
            System.out.println("\n[Test 5] Testing Clean Disconnect...");
            charlie.disconnect();
            Thread.sleep(300);
            assertTrue(alice.hasReceivedMessageContaining("Charlie has left"),
                    "Alice received departure notice for Charlie");

            // Test 6: Duplicate Username Rejection
            System.out.println("\n[Test 6] Testing Duplicate Username Rejection...");
            Socket dupSocket = new Socket("localhost", TEST_PORT);
            BufferedReader dupIn = new BufferedReader(new InputStreamReader(dupSocket.getInputStream()));
            PrintWriter dupOut = new PrintWriter(dupSocket.getOutputStream(), true);

            String prompt = dupIn.readLine(); // AUTH_REQ
            dupOut.println("alice"); // duplicate case-insensitive
            String dupResp = dupIn.readLine();
            assertTrue(dupResp.startsWith("AUTH_ERR"), "Server rejected duplicate username 'alice'");
            dupSocket.close();

            // Test 7: Capacity Limit Test (Simulating 99 more connections to reach 100)
            System.out.println("\n[Test 7] Testing 100 Max Client Capacity Limit...");
            List<SimulatedClient> bulkClients = new ArrayList<>();
            // Alice and Bob are already 2 clients. We add 98 more to reach 100.
            for (int i = 1; i <= 98; i++) {
                SimulatedClient sc = new SimulatedClient("localhost", TEST_PORT, "Bot" + i);
                sc.connect();
                bulkClients.add(sc);
            }
            Thread.sleep(500);

            // Now 100 clients are connected. The 101st connection should be rejected.
            Socket rejectedSocket = new Socket("localhost", TEST_PORT);
            BufferedReader rejIn = new BufferedReader(new InputStreamReader(rejectedSocket.getInputStream()));
            String rejMsg = rejIn.readLine();
            assertTrue(rejMsg != null && rejMsg.contains("[SERVER FULL]"),
                    "101st client was rejected with [SERVER FULL]");
            rejectedSocket.close();

            // Clean up bulk clients
            for (SimulatedClient sc : bulkClients) {
                sc.disconnect();
            }
            alice.disconnect();
            bob.disconnect();

            System.out.println("\n>>> ALL TESTS PASSED SUCCESSFULLY! <<<");

        } finally {
            server.stop();
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (condition) {
            System.out.println("  PASS: " + message);
        } else {
            System.err.println("  FAIL: " + message);
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    static class SimulatedClient {
        private final String host;
        private final int port;
        private final String username;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
        private volatile boolean connected = false;

        public SimulatedClient(String host, int port, String username) {
            this.host = host;
            this.port = port;
            this.username = username;
        }

        public void connect() throws IOException {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read AUTH_REQ
            String req = in.readLine();
            out.println(username);

            // Read AUTH_OK
            String ok = in.readLine();
            connected = true;

            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while (connected && (line = in.readLine()) != null) {
                        receivedMessages.add(line);
                    }
                } catch (IOException ignored) {
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        public boolean hasReceivedMessageContaining(String text) {
            for (String m : receivedMessages) {
                if (m.contains(text)) {
                    return true;
                }
            }
            return false;
        }

        public void disconnect() {
            connected = false;
            if (out != null) {
                out.println("/quit");
            }
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
