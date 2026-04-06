package lpi.client.rmi;
import lpi.server.rmi.IServer;
import lpi.server.rmi.IServer.*;
import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
public class Client {

    private static final String HOST = "100.64.161.89";
    private static final int PORT = 4321;

    private IServer server;
    private String sessionId;

    private Timer timer;
    private Set<String> lastUsers = new HashSet<>();

    public static void main(String[] args) {
        new Client().start();
    }

    public void start() {
        try {
            Registry registry = LocateRegistry.getRegistry(HOST, PORT);
            server = (IServer) registry.lookup(IServer.RMI_SERVER_NAME);

            System.out.println("Connected to server ✅");

            Scanner sc = new Scanner(System.in);

            while (true) {
                System.out.print("> ");
                String input = sc.nextLine();

                String[] parts = input.split(" ", 3);
                String cmd = parts[0];

                try {
                    switch (cmd) {

                        case "ping":
                            server.ping();
                            System.out.println("pong");
                            break;

                        case "echo":
                            System.out.println(server.echo(parts[1]));
                            break;

                        case "login":
                            sessionId = server.login(parts[1], parts[2]);
                            System.out.println("Logged in!");
                            startTimer();
                            break;

                        case "list":
                            if (!checkSession()) break;
                            printUsers(server.listUsers(sessionId));
                            break;

                        case "msg":
                            if (!checkSession()) break;
                            server.sendMessage(sessionId,
                                    new Message(parts[1], parts[2]));
                            System.out.println("Sent");
                            break;

                        case "file":
                            if (!checkSession()) break;
                            server.sendFile(sessionId,
                                    new FileInfo(parts[1], new File(parts[2])));
                            System.out.println("File sent");
                            break;

                        case "exit":
                            if (sessionId != null)
                                server.exit(sessionId);
                            stopTimer();
                            return;
                    }

                } catch (LoginException e) {
                    System.out.println("Login error: " + e.getMessage());
                } catch (ArgumentException e) {
                    System.out.println("Argument error: " + e.getMessage());
                } catch (ServerException e) {
                    System.out.println("Server error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    private boolean checkSession() {
        if (sessionId == null) {
            System.out.println("You must login first!");
            return false;
        }
        return true;
    }

    private void startTimer() {
        timer = new Timer();

        timer.schedule(new TimerTask() {
            public void run() {
                try {
                    // messages
                    Message msg = server.receiveMessage(sessionId);
                    if (msg != null)
                        System.out.println("\n📩 " + msg);

                    // files
                    FileInfo file = server.receiveFile(sessionId);
                    if (file != null) {
                        File dir = new File("downloads");
                        dir.mkdir();
                        file.saveFileTo(dir);
                        System.out.println("\n📁 File: " + file.getFilename());
                    }

                    // BONUS: users
                    updateUsers();

                } catch (Exception ignored) {}
            }
        }, 1000, 1000);
    }

    private void stopTimer() {
        if (timer != null)
            timer.cancel();
    }

    private void updateUsers() throws Exception {
        String[] usersArr = server.listUsers(sessionId);
        Set<String> current = new HashSet<>(Arrays.asList(usersArr));

        for (String u : current)
            if (!lastUsers.contains(u))
                System.out.println("\n🟢 " + u + " joined");

        for (String u : lastUsers)
            if (!current.contains(u))
                System.out.println("\n🔴 " + u + " left");

        lastUsers = current;
    }

    private void printUsers(String[] users) {
        System.out.println("Users:");
        for (String u : users)
            System.out.println(" - " + u);
    }
}