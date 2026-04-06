package org.example;

import org.apache.activemq.ActiveMQConnectionFactory;
import javax.jms.*;
import java.io.*;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.*;

public class MQClient {

    private Connection connection;
    private Session syncSession;
    private Session asyncSession;

    private MessageConsumer msgConsumer;
    private MessageConsumer fileConsumer;

    private String login = null;

    // === AFK TIMER (60 sec) ===
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> afkTask;

    private void startAfkTimer() { resetAfkTimer(); }

    private void resetAfkTimer() {
        if (afkTask != null && !afkTask.isDone()) {
            afkTask.cancel(false);
        }

        afkTask = scheduler.schedule(() -> {
            try {
                sendAfkAutoReply("ALL");
                System.out.println("\n[AFK] Sent auto-reply.");
            } catch (Exception e) {
                System.out.println("AFK error: " + e.getMessage());
            }
        }, 60, TimeUnit.SECONDS);
    }

    // === FILE STRUCT ===
    public static class FileInfo implements Serializable {
        public String sender;
        public String receiver;
        public String filename;
        public byte[] content;
    }

    // === CONNECT ===
    public void connect(String brokerUrl) throws Exception {

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setTrustedPackages(Arrays.asList("org.example"));

        connection = factory.createConnection();
        connection.start();

        syncSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        asyncSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Incoming messages
        Destination msgQueue = asyncSession.createQueue("chat.messages");
        msgConsumer = asyncSession.createConsumer(msgQueue);
        msgConsumer.setMessageListener(this::handleIncomingMessage);

        // Incoming files
        Destination fileQueue = asyncSession.createQueue("chat.files");
        fileConsumer = asyncSession.createConsumer(fileQueue);
        fileConsumer.setMessageListener(this::handleIncomingFile);

        startAfkTimer();

        System.out.println("Connected to MQ server");
    }

    private void handleIncomingMessage(Message message) {
        try {
            resetAfkTimer();

            MapMessage msg = (MapMessage) message;
            String sender = msg.getString("sender");
            String text = msg.getString("message");

            System.out.println("\n📩 Message from " + sender + ": " + text);

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleIncomingFile(Message message) {
        try {
            resetAfkTimer();

            ObjectMessage obj = (ObjectMessage) message;
            FileInfo file = (FileInfo) obj.getObject();

            FileOutputStream fos = new FileOutputStream("received_" + file.filename);
            fos.write(file.content);
            fos.close();

            System.out.println("\n📁 File received from " + file.sender +
                    ": " + file.filename + " (saved)");

        } catch (Exception e) { e.printStackTrace(); }
    }

    // === SAFE REQUEST/RESPONSE ===
    private Message requestResponse(String queueName, Message req) {

        resetAfkTimer();

        try {
            Destination target = syncSession.createQueue(queueName);
            Destination replyQueue = syncSession.createTemporaryQueue();

            req.setJMSReplyTo(replyQueue);

            MessageProducer producer = syncSession.createProducer(target);
            MessageConsumer consumer = syncSession.createConsumer(replyQueue);

            producer.send(req);
            Message response = consumer.receive(3000);

            producer.close();
            consumer.close();

            if (response == null) {
                System.out.println("⚠ WARNING: Server did not reply to " + queueName);
                return null;
            }

            return response;

        } catch (Exception e) {
            System.out.println("⚠ ERROR in requestResponse: " + e.getMessage());
            return null;
        }
    }

    // === AFK REPLY ===
    private void sendAfkAutoReply(String receiver) throws Exception {
        MapMessage msg = syncSession.createMapMessage();
        msg.setString("receiver", receiver);
        msg.setString("message", "Sorry, I'm AFK, will answer ASAP");

        requestResponse("chat.sendMessage", msg);
    }

    // === COMMANDS ===

    private void ping() throws JMSException {
        requestResponse("chat.diag.ping", syncSession.createMessage());
        System.out.println("Ping OK.");
    }

    private void echo(String text) {
        TextMessage req;
        try {
            req = syncSession.createTextMessage(text);
        } catch (JMSException e) { return; }

        Message resp = requestResponse("chat.diag.echo", req);

        if (resp instanceof TextMessage tm) {
            try { System.out.println("Echo: " + tm.getText()); }
            catch (JMSException ignored) {}
        }
    }

    private void loginCmd() throws Exception {
        Scanner sc = new Scanner(System.in);

        System.out.print("login: ");
        String l = sc.nextLine();

        System.out.print("password: ");
        String p = sc.nextLine();

        MapMessage req = syncSession.createMapMessage();
        req.setString("login", l);
        req.setString("password", p);

        Message resp = requestResponse("chat.login", req);

        if (resp == null) return;
        MapMessage r = (MapMessage) resp;

        if (r.getBoolean("success")) {
            login = l;
            System.out.println("Logged in.");
        } else {
            System.out.println("Login failed: " + r.getString("message"));
        }
    }

    private void listUsers() throws Exception {
        Message req = syncSession.createMessage();
        Message resp = requestResponse("chat.listUsers", req);

        if (resp == null) return;

        ObjectMessage obj = (ObjectMessage) resp;
        String[] users = (String[]) obj.getObject();

        System.out.println("Users online:");
        for (String u : users)
            System.out.println(" - " + u);
    }

    private void sendMsg(String input) throws Exception {
        if (login == null) {
            System.out.println("You must login first!");
            return;
        }

        if (!input.contains(" ")) {
            System.out.println("Usage: msg <user> <text>");
            return;
        }

        String[] p = input.split(" ", 2);
        String receiver = p[0];
        String text = p[1];

        MapMessage req = syncSession.createMapMessage();
        req.setString("receiver", receiver);
        req.setString("message", text);

        requestResponse("chat.sendMessage", req);

        System.out.println("Message sent.");
    }

    private void sendFile(String input) throws Exception {
        if (login == null) {
            System.out.println("You must login first!");
            return;
        }
        if (!input.contains(" ")) {
            System.out.println("Usage: file <user> <path>");
            return;
        }

        String[] p = input.split(" ", 2);
        String receiver = p[0];
        String path = p[1];

        File f = new File(path);
        if (!f.exists()) {
            System.out.println("File not found!");
            return;
        }

        byte[] data = new FileInputStream(f).readAllBytes();

        FileInfo fi = new FileInfo();
        fi.sender = login;
        fi.receiver = receiver;
        fi.filename = f.getName();
        fi.content = data;

        ObjectMessage req = syncSession.createObjectMessage(fi);

        requestResponse("chat.sendFile", req);

        System.out.println("File sent (server may or may not reply).");
    }

    private void exit() throws Exception {
        requestResponse("chat.exit", syncSession.createMessage());

        msgConsumer.close();
        fileConsumer.close();
        syncSession.close();
        asyncSession.close();
        connection.close();

        System.out.println("Disconnected.");
    }

    public void runCLI() throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("Commands: ping, echo, login, list, msg, file, exit");

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();

            resetAfkTimer();

            if (line.equals("ping")) ping();
            else if (line.startsWith("echo ")) echo(line.substring(5));
            else if (line.equals("login")) loginCmd();
            else if (line.equals("list")) listUsers();
            else if (line.startsWith("msg ")) sendMsg(line.substring(4));
            else if (line.startsWith("file ")) sendFile(line.substring(5));
            else if (line.equals("exit")) { exit(); break; }
            else System.out.println("Unknown command.");
        }
    }

    public static void main(String[] args) throws Exception {
        MQClient client = new MQClient();
        client.connect("tcp://10.211.95.89:61616"); // <--- YOUR IP HERE
        client.runCLI();
    }
}