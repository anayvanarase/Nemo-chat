# Walkthrough: TCP Client-Server Messaging Application

We have implemented a multi-threaded, terminal-based TCP messaging application supporting up to **100 concurrent clients** with username authentication, public broadcasting, private messaging, and clean session lifecycle management.

---

## What Was Implemented

### 1. [Server.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Server.java)
- **Max Client Capacity (100 Clients)**: Strictly enforces a 100-client limit using atomic concurrency tracking. Rejects subsequent incoming connections with `[SERVER FULL]` notice.
- **Concurrent Client Registry**: Maintains a thread-safe `ConcurrentHashMap<String, ClientHandler>` preventing duplicate usernames (case-insensitive check).
- **Public Broadcasting**: Formats and broadcasts messages to all participants with timestamps (`[HH:mm:ss] [Username]: <message>`).
- **Private Messaging**: Routes whispers between sender and recipient with `/w <username> <message>`.
- **Chat History Buffer**: Stores the last 20 messages in memory and automatically transmits them to newly connected users for conversation context.
- **Admin Server Console**: Server administrator can execute commands directly in the server terminal:
  - `/users` - View connected users and capacity.
  - `/broadcast <message>` - Broadcast official server announcements.
  - `/kick <username> [reason]` - Force-disconnect a disruptive user.
  - `/stop` - Cleanly shutdown server and close all connections.

### 2. [ClientHandler.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/ClientHandler.java)
- Manages I/O communication for each client socket.
- Handles the initial username handshake (validates format, length <= 20 chars, no spaces, no reserved prefixes).
- Parses user commands (`/users`, `/w`, `/history`, `/help`, `/quit`).
- Ensures clean teardown and notifications when a client disconnects or loses connection.

### 3. [Client.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Client.java)
- **Dual-Threaded Architecture (CLI / No GUI)**:
  - **Receiver Thread**: Listens asynchronously for incoming messages and system announcements without blocking the user.
  - **Sender Thread**: Reads user input from terminal console and sends messages or commands.
- Interactive username negotiation on startup.
- Supports `/quit` or `/exit` for clean termination.

### 4. [Main.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Main.java)
- Unified entry point allowing users to run either Server or Client via interactive CLI menu or arguments (`java Main server` / `java Main client`).

### 5. [ChatSystemTest.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/ChatSystemTest.java)
- Comprehensive automated test verifying:
  - Username handshake and duplicate name rejection.
  - Public broadcast delivery across clients.
  - Private whisper delivery (ensuring non-recipients do not see the message).
  - `/users` active list query.
  - Clean disconnect notification.
  - Rejection of the 101st client when capacity (100) is reached.

---

## Verification Results

### Automated Integration Test Output
All automated integration tests passed cleanly:
```text
=== Starting Chat System Integration Tests ===

[Test 1] Testing User Registration Handshake...
  PASS: Alice received Bob's join notification

[Test 2] Testing Public Broadcast...
  PASS: Bob received Alice's broadcast

[Test 3] Testing Whisper / Private Message...
  PASS: Alice received whisper from Bob
  PASS: Charlie did NOT receive private message

[Test 4] Testing /users Command...
  PASS: Charlie saw 3 active users

[Test 5] Testing Clean Disconnect...
  PASS: Alice received departure notice for Charlie

[Test 6] Testing Duplicate Username Rejection...
  PASS: Server rejected duplicate username 'alice'

[Test 7] Testing 100 Max Client Capacity Limit...
  PASS: 101st client was rejected with [SERVER FULL]

>>> ALL TESTS PASSED SUCCESSFULLY! <<<
```

---

## How to Run

### Option 1: Using IntelliJ IDEA
1. Open [Main.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Main.java) and click **Run**.
2. Type `1` in the Run console to start the **Server**.
3. In a second run configuration or terminal, run [Main.java](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/Main.java) and type `2` to launch a **Client**.

### Option 2: Using Terminal / PowerShell

**1. Compile the project:**
```powershell
javac -d out src/*.java
```

**2. Start the Server:**
```powershell
java -cp out Server
```
*(Server listens on port 5000 by default)*

**3. Start One or More Clients (in separate terminal windows):**
```powershell
java -cp out Client
```
*(Optionally specify host and port: `java -cp out Client localhost 5000`)*

---

## Available Chat Commands (In Client)

| Command | Description | Example |
| :--- | :--- | :--- |
| `<message>` | Send a public message to all participants | `Hello everyone!` |
| `/w <user> <msg>` | Send a private whisper / direct message | `/w Alice Secret project info` |
| `/users` or `/list` | Show online users and capacity count | `/users` |
| `/history` | Show recent chat history | `/history` |
| `/help` | Show command cheat sheet | `/help` |
| `/quit` or `/exit` | Disconnect cleanly from chatroom | `/quit` |
