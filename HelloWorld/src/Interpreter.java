import java.util.Scanner;

public class Interpreter {

    static String currentUser = null;

    static String[] users = {"user1", "user2", "user3"};
    static String[][] messages = new String[3][10];

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("> ");
            String input = scanner.nextLine();

            String[] parts = input.split(" ");
            String command = parts[0];

            System.out.println("Entered command = \"" + command + "\"");

            switch (command) {

                case "ping":
                    System.out.println("pong");
                    break;

                case "echo":

                    if (parts.length > 1) {

                        StringBuilder text = new StringBuilder();

                        for (int i = 1; i < parts.length; i++) {
                            text.append(parts[i]).append(" ");
                        }

                        System.out.println(text.toString());
                    }

                    break;

                case "login":

                    if (parts.length >= 3) {

                        currentUser = parts[1];

                        System.out.println("Login: " + parts[1]);
                        System.out.println("Password: " + parts[2]);
                        System.out.println("User " + currentUser + " logged in");
                    }

                    break;

                case "list":

                    System.out.println("Users:");

                    for (String u : users) {
                        System.out.println(u);
                    }

                    break;

                case "msg":

                    if (currentUser == null) {
                        System.out.println("You must login first");
                        break;
                    }

                    if (parts.length >= 3) {

                        String destination = parts[1];

                        StringBuilder text = new StringBuilder();

                        for (int i = 2; i < parts.length; i++) {
                            text.append(parts[i]).append(" ");
                        }

                        System.out.println("Message from " + currentUser +
                                " to " + destination + ": " + text);
                    }

                    break;

                case "file":

                    if (currentUser == null) {
                        System.out.println("You must login first");
                        break;
                    }

                    if (parts.length >= 3) {

                        String destination = parts[1];
                        String filename = parts[2];

                        System.out.println("User " + currentUser +
                                " sent file \"" + filename +
                                "\" to " + destination);
                    }

                    break;

                case "exit":

                    System.out.println("Program finished");
                    return;

                default:

                    System.out.println("Unknown command");
            }
        }
    }
}