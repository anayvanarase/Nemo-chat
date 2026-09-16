import java.util.Scanner;

/**
 * Main launcher entry point for the TCP Messaging App.
 * Allows launching either the Server or the Client from a single class.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            String[] forwardedArgs = new String[args.length - 1];
            System.arraycopy(args, 1, forwardedArgs, 0, forwardedArgs.length);

            if (mode.equals("server")) {
                Server.main(forwardedArgs);
                return;
            } else if (mode.equals("client")) {
                Client.main(forwardedArgs);
                return;
            }
        }

        // Interactive selection if no argument is passed
        System.out.println("=========================================");
        System.out.println("       TCP Messaging App Launcher        ");
        System.out.println("=========================================");
        System.out.println("Select mode to start:");
        System.out.println("  1. Start Server");
        System.out.println("  2. Start Client");
        System.out.print("\nEnter choice (1 or 2): ");

        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine().trim();

        if (choice.equals("1") || choice.equalsIgnoreCase("server")) {
            System.out.println("\nLaunching Server...");
            Server.main(new String[0]);
        } else if (choice.equals("2") || choice.equalsIgnoreCase("client")) {
            System.out.println("\nLaunching Client...");
            Client.main(new String[0]);
        } else {
            System.out.println("Invalid choice. Exiting.");
        }
        scanner.close();
    }
}
