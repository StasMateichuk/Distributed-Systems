import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class Client {

    // --- COMMANDS ---
    private static final byte CMD_PING = 1;
    private static final byte CMD_ECHO = 3;
    private static final byte CMD_LOGIN = 5;
    private static final byte CMD_LIST = 10;
    private static final byte CMD_MSG = 15;

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("Usage: java Client <server_ip> <port>");
            return;
        }

        String serverIp = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(serverIp, port)) {

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in);

            System.out.println("Connected to server: " + serverIp + ":" + port);

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Disconnected.");
                    break;
                }

                // --- PING ---
                if (input.equalsIgnoreCase("ping")) {
                    send(out, new byte[]{CMD_PING});
                    handleResponse(in);
                    continue;
                }

                // --- ECHO ---
                if (input.startsWith("echo ")) {
                    byte[] msg = input.substring(5).getBytes("UTF-8");

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bos.write(CMD_ECHO);
                    bos.write(msg);

                    send(out, bos.toByteArray());
                    handleResponse(in);
                    continue;
                }

                // --- LOGIN ---
                if (input.startsWith("login ")) {
                    String[] parts = input.split(" ", 3);

                    if (parts.length < 3) {
                        System.out.println("Usage: login <user> <pass>");
                        continue;
                    }

                    byte[] data = serialize(new String[]{parts[1], parts[2]});

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bos.write(CMD_LOGIN);
                    bos.write(data);

                    send(out, bos.toByteArray());
                    handleResponse(in);
                    continue;
                }

                // --- LIST ---
                if (input.equals("list")) {
                    send(out, new byte[]{CMD_LIST});
                    handleResponse(in);
                    continue;
                }

                // --- MSG ---
                if (input.startsWith("msg ")) {
                    String[] parts = input.split(" ", 3);

                    if (parts.length < 3) {
                        System.out.println("Usage: msg <user> <message>");
                        continue;
                    }

                    byte[] data = serialize(new String[]{parts[1], parts[2]});

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bos.write(CMD_MSG);
                    bos.write(data);

                    send(out, bos.toByteArray());
                    handleResponse(in);
                    continue;
                }

                System.out.println("Unknown command.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- SEND ---
    private static void send(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    // --- RESPONSE HANDLER ---
    private static void handleResponse(DataInputStream in) throws IOException {


        int length = in.readInt();
        byte[] response = new byte[length];
        in.readFully(response);

        byte code = response[0];

        switch (code) {

            case 2:
                System.out.println("SERVER: PONG");
                break;

            case 6:
                System.out.println("SERVER: LOGIN OK (new user)");
                break;

            case 7:
                System.out.println("SERVER: LOGIN OK");
                break;

            case 16:
                System.out.println("SERVER: MESSAGE SENT");
                break;

            case 21:
                System.out.println("SERVER: FILE SENT");
                break;

            case 26:
                System.out.println("SERVER: NO MESSAGES");
                break;

            case 31:
                System.out.println("SERVER: NO FILES");
                break;

            case 100:
                System.out.println("SERVER: ERROR");
                break;

            case 101:
                System.out.println("SERVER: WRONG SIZE");
                break;

            case 102:
                System.out.println("SERVER: SERIALIZATION ERROR");
                break;

            case 103:
                System.out.println("SERVER: UNKNOWN COMMAND");
                break;

            case 104:
                System.out.println("SERVER: WRONG PARAMS");
                break;

            case 110:
                System.out.println("SERVER: WRONG PASSWORD");
                break;

            case 112:
                System.out.println("SERVER: LOGIN FIRST");
                break;

            case 113:
                System.out.println("SERVER: FAILED SENDING");
                break;

            default:
                // --- ПРОБУЄМО ДЕСЕРІАЛІЗАЦІЮ ---
                try {
                    Object obj = deserialize(response);

                    if (obj instanceof String[]) {
                        System.out.println("SERVER: " + Arrays.toString((String[]) obj));
                    } else if (obj instanceof Object[]) {
                        System.out.println("SERVER: " + Arrays.toString((Object[]) obj));
                    } else {
                        System.out.println("SERVER: " + obj);
                    }

                } catch (Exception e) {
                    // fallback як текст
                    try {
                        String text = new String(response, "UTF-8");
                        System.out.println("SERVER: " + text);
                    } catch (Exception ex) {
                        System.out.println("SERVER: UNKNOWN RESPONSE (" + code + ")");
                    }
                }
        }
    }

    // --- SERIALIZE ---
    private static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(obj);
        out.flush();
        return bos.toByteArray();
    }

    // --- DESERIALIZE ---
    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        return in.readObject();
    }
}
