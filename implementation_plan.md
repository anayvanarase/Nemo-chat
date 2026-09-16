# Implementation Plan - Terminal-based Client-Server Messaging App

Build a robust, multi-threaded TCP client-server chat application in Java with a terminal interface (no GUI), supporting up to 100 concurrent clients, username validation, real-time message broadcasting, and clean disconnect handling.

## User Review Required

> [!IMPORTANT]
> - **Client Cap Enforcement**: The server will strictly enforce a maximum of 100 concurrent clients. If a 101st client connects, the server responds with an informative capacity rejection message and gracefully terminates that connection.
> - **Terminal Experience**: Both client and server will run in standard terminal/CLI windows. The client will use a dual-threaded model (one thread reading user console input, one thread listening for incoming server messages) so that incoming messages don't block typing.
> - **Additional Features**: Review the proposed extra functionalities below and let us know which ones you would like included in the initial build.

---

## Analysis of Current Codebase

- **`src/Client.java`**: Currently a single-use echo client. It connects to `localhost:5000`, sends a hardcoded string `"Hello from Client!"`, reads one response line, and exits.
- **`src/Server.java`**: A single-threaded blocking echo server. It binds to port 5000, accepts one connection, reads one line, replies `"Hello from Server!"`, and closes.
- **Project Structure**: Standard IntelliJ IDEA Java project targeting Java 17.

---

## Recommended Additional Functionalities

To elevate this from a basic chat to a complete, production-ready terminal chat app, here are features that can be added:

1. **Private Messaging (Whisper / DM)**:
   - Command: `/w <username> <message>` or `/msg <username> <message>`
   - Allows users to send direct messages visible only to the recipient and sender.
2. **Active Users List**:
   - Command: `/users` or `/list`
   - Displays all currently online clients and the current capacity count (e.g., `Active Users (3/100): Alice, Bob, Charlie`).
3. **Formatted Timestamps & ANSI Terminal Colors**:
   - Prefix messages with `[HH:mm:ss]`.
   - Subtle ANSI colors: Green for usernames, Cyan for private messages, Yellow for system announcements, Red for errors.
4. **Chat Rooms / Channels**:
   - Commands: `/join <room_name>`, `/leave`, `/rooms`
   - Enables users to switch between channels (e.g. `#general`, `#tech`, `#random`) or remain in a global lounge.
5. **Recent Chat History (Backlog on Join)**:
   - Stores the last 10–20 messages in memory on the server and replays them when a new user joins so they have context.
6. **Graceful Disconnect & Session Commands**:
   - Command: `/quit` or `/exit` for clean client departure.
   - Command: `/help` to display available commands.
7. **Heartbeat / Keep-Alive & Dead Connection Detection**:
   - TCP keep-alive or timeout handling to release slots if a client connection is dropped abruptly.
8. **Admin Controls / Moderation**:
   - Server console commands or an admin password to `/kick <username>` or `/broadcast <message>`.

---

## Proposed Changes

### Core Architecture

```
                 +-------------------------------------------------+
                 |                  Server (port 5000)             |
                 |  - ServerSocket (accept loop)                   |
                 |  - Semaphore / Atomic counter (Max 100 clients) |
                 |  - ThreadPool Executor                          |
                 |  - ConcurrentHashMap<String, ClientHandler>     |
                 +-----------------------+-------------------------+
                                         |
               +-------------------------+-------------------------+
               |                                                   |
      [Socket Connection]                                 [Socket Connection]
               |                                                   |
+--------------v---------------+                    +--------------v---------------+
|     ClientHandler (Alice)    |                    |     ClientHandler (Bob)      |
+--------------^---------------+                    +--------------^---------------+
               | TCP Streams                                       | TCP Streams
+--------------v---------------+                    +--------------v---------------+
|        Client (Alice)        |                    |         Client (Bob)         |
|  - Main Thread: Console In   |                    |  - Main Thread: Console In   |
|  - Receiver Thread: Net In   |                    |  - Receiver Thread: Net In   |
+------------------------------+                    +------------------------------+
```

---

### Component Details

#### [MODIFY] [Server.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Server.java)
- **Max Client Enforcement**: Maintain `MAX_CLIENTS = 100` with `AtomicInteger` or `Semaphore`. If full, send rejection message and close socket.
- **Client Handler Pool**: Use an `ExecutorService` (e.g., cached or fixed thread pool) to handle concurrent clients without blocking the accept loop.
- **Client Registry**: `ConcurrentHashMap<String, ClientHandler>` storing active username-to-handler mappings for fast O(1) lookup during routing.
- **Username Negotiation**: Prompt client for username; reject if taken, blank, or contains illegal characters (spaces, reserved prefixes).
- **Broadcast & Dispatch**: Centralized methods:
  - `broadcast(String message, ClientHandler sender)` (omits sender or formats sender)
  - `sendSystemMessage(String message)`
  - `sendPrivateMessage(String sender, String recipient, String message)`
- **Graceful Shutdown**: Close all active client sockets and release port 5000 when stopping.

#### [NEW] [ClientHandler.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/ClientHandler.java)
- Encapsulates individual client socket connection, `BufferedReader` (in), and `PrintWriter` (out).
- Handles the initial registration handshake.
- Runs the client read loop: parses regular chat messages vs commands (`/users`, `/w`, `/help`, `/quit`).
- Safely cleans up and notifies the server on disconnect or network error.

#### [MODIFY] [Client.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Client.java)
- Connects to server (configurable host and port, default `localhost:5000`).
- Prompts user for username and performs handshake with the server.
- Spawns a background **Receiver Thread** (`ServerListener`) that continuously prints server messages to `System.out`.
- Runs a **Sender Loop** on the main thread reading user inputs from `Scanner(System.in)` and writing to the server socket.
- Handles `/quit` command and clean socket shutdown.

---

## Verification Plan

### Automated / Scripted Verification
1. **Compilation Check**:
   - Run `javac src/*.java` from project root to ensure clean compilation without errors.
2. **Concurrent Connection Test**:
   - Run a test script in PowerShell spawning multiple simulated client connections to verify that broadcasting, private messaging, and disconnect cleanup function properly under concurrency.
3. **Capacity Limit Test**:
   - Verify that when simulated client count reaches limit, subsequent connections are rejected with proper notification.

### Manual Verification
1. Start `Server`: Verify listening state on port 5000.
2. Start `Client 1`: Join as "Alice", verify welcome message.
3. Start `Client 2`: Join as "Bob", verify "Bob has joined" notification appears in Alice's terminal.
4. Test messaging: Alice sends a public message, Bob sees it.
5. Test commands:
   - `/users` displays both Alice and Bob.
   - `/w Bob Secret message` sends private message only to Bob.
   - `/help` shows command guide.
   - `/quit` cleanly disconnects Bob, Alice sees "Bob has left the chat room".
