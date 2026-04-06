package org.example;

import javax.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.util.Arrays;
import java.util.Scanner;

public class MQClient {

    // URL вашого MQ-сервера (замінити на IP)
    private String brokerUrl = "tcp://10.211.95.89:61616";

    private Connection connection;
    private Session syncSession;
    private Session asyncSession;

    private MessageConsumer msgConsumer;

    private boolean isLoggedIn = false;


    // ==========================================================
    // 1. Підключення до ActiveMQ
    // ==========================================================
    public void connect() throws Exception {

        ActiveMQConnectionFactory cf =
                new ActiveMQConnectionFactory(brokerUrl);

        // Дозволяємо ObjectMessage для пакету сервера
        cf.setTrustedPackages(Arrays.asList("lpi.server.mq", "java.lang", "java.util"));

        connection = cf.createConnection();
        connection.start();

        syncSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        asyncSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        System.out.println("✔ Connected: " + brokerUrl);

        startAsyncReceiver();
    }


    // ==========================================================
    // 2. Відключення
    // ==========================================================
    public void disconnect() throws Exception {
        if (msgConsumer != null) msgConsumer.close();
        if (asyncSession != null) asyncSession.close();
        if (syncSession != null) syncSession.close();
        if (connection != null) connection.close();
        System.out.println("✖ Disconnected");
    }


    // ==========================================================
    // 3. Універсальний запит
    // ==========================================================
    private Message sendRequest(String queueName, Message msg) throws Exception {

        Destination q = syncSession.createQueue(queueName);
        Destination reply = syncSession.createTemporaryQueue();

        msg.setJMSReplyTo(reply);

        MessageProducer producer = syncSession.createProducer(q);
        MessageConsumer consumer = syncSession.createConsumer(reply);

        producer.send(msg);

        Message resp = consumer.receive(2000);

        producer.close();
        consumer.close();

        return resp;
    }


    // ==========================================================
    // 4. PING
    // ==========================================================
    public void ping() throws Exception {
        Message req = syncSession.createMessage();
        Message resp = sendRequest("chat.diag.ping", req);

        if (resp != null)
            System.out.println("PING OK");
        else
            System.out.println("PING TIMEOUT");
    }


    // ==========================================================
    // 5. ECHO
    // ==========================================================
    public void echo(String text) throws Exception {
        TextMessage req = syncSession.createTextMessage(text);

        Message resp = sendRequest("chat.diag.echo", req);

        if (resp instanceof TextMessage)
            System.out.println("Server: " + ((TextMessage) resp).getText());
        else
            System.out.println("ECHO ERROR");
    }


    // ==========================================================
    // 6. Login
    // ==========================================================
    public void login(String user, String pass) throws Exception {

        MapMessage req = syncSession.createMapMessage();
        req.setString("login", user);
        req.setString("password", pass);

        Message resp = sendRequest("chat.login", req);

        if (resp instanceof MapMessage) {
            MapMessage r = (MapMessage) resp;

            if (r.getBoolean("success")) {
                isLoggedIn = true;
                System.out.println("✔ Logged in as " + user);
            } else {
                System.out.println("Login failed: " + r.getString("message"));
            }
        }
    }


    // ==========================================================
    // 7. List users
    // ==========================================================
    public void listUsers() throws Exception {

        Message req = syncSession.createMessage();
        Message resp = sendRequest("chat.listUsers", req);

        if (resp instanceof ObjectMessage) {
            ObjectMessage om = (ObjectMessage) resp;

            Object obj = om.getObject();

            if (obj instanceof String[]) {
                System.out.println("Online:");
                for (String u : (String[]) obj)
                    System.out.println(" - " + u);
            }
        }
    }


    // ==========================================================
    // 8. Send message
    // ==========================================================
    public void sendMsg(String receiver, String text) throws Exception {

        if (!isLoggedIn) {
            System.out.println("❌ Login required!");
            return;
        }

        MapMessage req = syncSession.createMapMessage();
        req.setString("receiver", receiver);
        req.setString("message", text);

        Message resp = sendRequest("chat.sendMessage", req);

        if (resp instanceof MapMessage)
            System.out.println("✔ Message sent");
        else
            System.out.println("SEND ERROR");
    }


    // ==========================================================
    // 9. Async receiving
    // ==========================================================
    private void startAsyncReceiver() throws Exception {

        Destination q = asyncSession.createQueue("chat.messages");

        msgConsumer = asyncSession.createConsumer(q);

        msgConsumer.setMessageListener(message -> {
            try {
                if (message instanceof MapMessage) {
                    MapMessage m = (MapMessage) message;

                    System.out.println("\n📩 NEW MESSAGE from " +
                            m.getString("sender") +
                            ": " + m.getString("message"));
                    System.out.print("> ");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    // ==========================================================
    // 10. Exit
    // ==========================================================
    public void exit() throws Exception {
        Message req = syncSession.createMessage();
        sendRequest("chat.exit", req);
    }


    // ==========================================================
    // 11. Main
    // ==========================================================
    public static void main(String[] args) throws Exception {

        MQClient c = new MQClient();
        c.connect();

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("> ");

            String cmd = sc.next();

            switch (cmd) {

                case "ping": c.ping(); break;

                case "echo":
                    c.echo(sc.nextLine().trim());
                    break;

                case "login":
                    c.login(sc.next(), sc.next());
                    break;

                case "list": c.listUsers(); break;

                case "msg":
                    c.sendMsg(sc.next(), sc.nextLine().trim());
                    break;

                case "exit":
                    c.exit();
                    c.disconnect();
                    return;

                default:
                    System.out.println("Unknown command");
            }
        }
    }
}