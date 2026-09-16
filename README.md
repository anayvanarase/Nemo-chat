# TCP Chatroom (CLI Client-Server Messenger)

A robust, multi-threaded TCP messaging application written in Java. Built with a client-server architecture, terminal-based interface (no GUI), strict capacity enforcement of up to 100 concurrent clients, unique username registration, real-time message broadcasting, and direct messaging.

---

## Key Features

- **Strict 100-Client Capacity**: Tracks active connections concurrently. When the 100-client limit is reached, subsequent connections are politely notified with `[SERVER FULL]` and gracefully closed.
- **Unique Username Authentication**: Connects with an interactive handshake. Validates that usernames are non-empty, under 20 characters, contain no spaces or illegal prefixes, and are not duplicate (case-insensitive).
- **Dual-Threaded Client (CLI)**:
  - **Background Receiver Thread**: Asynchronously listens for incoming server broadcasts and whispers without interrupting what you are typing.
  - **Main Sender Thread**: Reads user console input and sends messages or commands.
- **Real-Time Public Broadcast**: All connected users receive messages prefixed with timestamps (`[HH:mm:ss] [Username]: <message>`).
- **Private Messaging (Whispers)**: Send private direct messages to specific users via `/w <username> <message>`.
- **Active User Directory**: Query all online users and capacity count at any time via `/users` or `/list`.
- **Recent Chat Backlog**: Stores the last 20 messages in server memory and replays them when a newcomer joins.
- **Server Admin Console**: The server administrator can issue commands directly from the server terminal:
  - `/users` - List online participants.
  - `/broadcast <msg>` - Send an official server announcement.
  - `/kick <user> [reason]` - Disconnect a specific user.
  - `/stop` - Gracefully close all client sessions and shut down.
- **Clean Session Lifecycle**: Users can disconnect cleanly via `/quit` or `/exit`, automatically notifying the room.

---

## Architecture Overview

```
                          +-------------------------------------------------+
                          |               Server (Port 5000)                |
                          |  - Accepts incoming sockets                     |
                          |  - Enforces MAX_CLIENTS = 100                   |
                          |  - ConcurrentHashMap<String, ClientHandler>     |
                          |  - Cached thread pool executor                  |
                          +-----------------------+-------------------------+
                                                  |
                    +-----------------------------+-----------------------------+
                    |                                                           |
           [TCP Socket Stream]                                         [TCP Socket Stream]
                    |                                                           |
     +--------------v---------------+                            +--------------v---------------+
     |     ClientHandler (Alice)    |                            |      ClientHandler (Bob)     |
     | - Handshake & Validation     |                            | - Handshake & Validation     |
     | - Command parsing & routing  |                            | - Command parsing & routing  |
     +--------------^---------------+                            +--------------^---------------+
                    |                                                           |
     +--------------v---------------+                            +--------------v---------------+
     |        Client (Alice)        |                            |         Client (Bob)         |
     |  - Background Listener Thread|                            |  - Background Listener Thread|
     |  - Main Console Input Thread |                            |  - Main Console Input Thread |
     +------------------------------+                            +------------------------------+
```

---

## Project Structure

```
Tcp_messager/
├── src/
│   ├── Main.java            # Unified interactive launcher (Server or Client)
│   ├── Server.java          # Multi-client TCP server & admin console
│   ├── ClientHandler.java   # Socket worker handling per-client protocol & commands
│   ├── Client.java          # Dual-threaded terminal client
│   └── ChatSystemTest.java  # Automated concurrency & functional test suite
├── out/                     # Compiled bytecode (.class files)
├── README.md                # Project documentation
└── Tcp_messager.iml         # IntelliJ IDEA module descriptor
```

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 17 or higher recommended (compatible with JDK 11+).

Check your installed version:
```powershell
java -version
javac -version
```

---

## Compilation

From the project root directory, compile all source files into the `out` directory:

```powershell
javac -d out src/*.java
```

---

## How to Run

### Method 1: Using the Unified Launcher (`Main.java`)

Run the launcher and select either `1` (Server) or `2` (Client):

```powershell
java -cp out Main
```

Or pass the mode directly as a command-line argument:
```powershell
java -cp out Main server
java -cp out Main client
```

---

### Method 2: Running Server & Clients Directly

#### 1. Start the Server
In your first terminal window:

```powershell
java -cp out Server
```
*By default, the server binds to port `5000`. You can specify a custom port: `java -cp out Server 8080`.*

#### 2. Start One or More Clients
In separate terminal windows, start clients:

```powershell
java -cp out Client
```
*By default, connects to `localhost:5000`. You can specify custom host and port: `java -cp out Client 127.0.0.1 5000`.*

When prompted, enter your desired username:
```text
Connecting to chat server at localhost:5000...
Connected to server successfully!

Welcome to the Chatroom! Please enter your username:
> Username: Alice
```

---

## Client Commands Reference

| Command | Description | Example |
| :--- | :--- | :--- |
| `<message>` | Broadcast a public message to all participants | `Hello everyone!` |
| `/w <username> <msg>` | Send a private whisper / direct message | `/w Bob Secret project update` |
| `/users` or `/list` | List all currently connected users and count | `/users` |
| `/history` | Show the recent chat history backlog | `/history` |
| `/help` | Display the list of available commands | `/help` |
| `/quit` or `/exit` | Disconnect cleanly from the chatroom | `/quit` |

---

## Server Console Commands Reference

Administrators can enter commands directly into the server's running console:

| Command | Description | Example |
| :--- | :--- | :--- |
| `/users` | Display all currently online users | `/users` |
| `/broadcast <message>` | Send an official announcement to all clients | `/broadcast Server maintenance at 10 PM` |
| `/kick <username> [reason]` | Disconnect a specific user | `/kick SpammerBot Spamming` |
| `/stop` | Gracefully terminate all sessions and stop server | `/stop` |
| `/help` | Display server admin commands | `/help` |

---

## Running the Automated Test Suite

An automated integration test is included in [`src/ChatSystemTest.java`](file:///c:/Users/LENOVO/IdeaProjects/Tcp_messager/src/ChatSystemTest.java). It automatically verifies:
1. Registration & username handshake.
2. Duplicate username rejection (case-insensitive).
3. Public message broadcasting.
4. Private whisper isolation (ensuring non-recipients do not receive private whispers).
5. Active users count and listing.
6. Clean departure notifications.
7. Capacity limit: Simulates 100 clients and verifies that the 101st client is rejected with `[SERVER FULL]`.

To run the test suite:
```powershell
java -cp out ChatSystemTest
```
