package org.example;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Server {
    private static final int MAX_HISTORY = 100;
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    private final int port;
    private final List<ClientHandler> clientsList = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private final List<String> history = new ArrayList<>();
    private int count = 0;

    public Server(int port) {
        this.port = port;
    }

// Démarre le serveur et accepte les connexions des clients
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
        isRunning = true;
        System.out.println("Chat server started on port " + port);

        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientsList.add(clientHandler);
                new Thread(clientHandler).start();
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Erreur lors de l'acceptation d'un client: " + e.getMessage());
                }
            }
        }
    }

    private void sendHistoryToClient(ClientHandler clientHandler) {
        if (clientHandler == null) { return; }
        for (String message : history) {
            clientHandler.sendMessage(message);
        }
    }

    private void addToHistory(String message) {
        history.add(message);
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    // Arrête le serveur et ferme toutes les connexions
    public void stop() throws IOException {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        synchronized (clientsList) {
            for (ClientHandler clientHandler : clientsList) {
                clientHandler.closeConnection();
            }
            clientsList.clear();
        }
    }

    // méthode pour envoyer message à tout le monde
    public void broadcastMessage(ClientHandler sender, String message) {
        if (message == null || message.length() > MAX_MESSAGE_LENGTH) {
            return;
        }
        addToHistory(message);
        // synchronisation de la liste
        synchronized (clientsList) {
            for (ClientHandler client : clientsList) {
                client.sendMessage(message);
            }
        }
    }

    // Classe interne pour gérer chaque client
    class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String userName;
        private final int clientId;

        public ClientHandler(Socket socket, Server srv) {
            this.socket = socket;
            this.clientId = count++;
        }

        public void run() {
            try {
                initialiseStreams();
                askUserName();
                announceJoin();
                Server.this.sendHistoryToClient(this);

                listenForMessages();
                announceLeave();
            } catch (IOException e) {
                System.out.println("Client disconnected unexpectedly.");
                if (userName != null) {
                    try {
                        announceLeave();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            } finally {
                try {
                    cleanUp();
                    if (socket != null && !socket.isClosed()) socket.close();
                } catch (IOException e) {
                    System.err.println("Erreur lors de la fermeture du socket: " + e.getMessage());
                }
            }
        }

        // run sub-methodes
        private void initialiseStreams() throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        }

        private void askUserName() throws IOException {
            out.println("Enter your name: ");
            userName = in.readLine();
            if (userName == null || userName.trim().isEmpty()) {
                out.println("Username invalid.");
                throw new IOException("Username invalid.");
            }
        }

        private void announceJoin() throws IOException {
            String  message =  userName + " a rejoint la conversation";
            System.out.println(message);
            broadcastMessage(this, message);
        }

        private void announceLeave() throws IOException {
            String msg = userName + " a laissé la conversation.";
            System.out.println(msg);
            broadcastMessage(this, msg);
        }

        private void listenForMessages() throws IOException {
            String messageInput;

            while ((messageInput = in.readLine()) != null) {

                if (messageInput.length() > MAX_MESSAGE_LENGTH) {
                    sendMessage("[Server] Message too long. Ignored.");
                    continue;
                }

                String formatted = userName + ": " + messageInput;
                System.out.println(formatted);
                broadcastMessage(this, formatted);
            }
        }

        private void cleanUp() {
            clientsList.remove(this);
        }

        private void sendMessage(String message) {
            try {
                out.println(message);
            } catch (Exception e) {
                System.out.println("Client deconnecté.");
            }
        }

        public String getUserName() {
            return userName;
        }

        public void closeConnection() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture du socket: " + e.getMessage());
            }
        }

        private ServerSocket serverSocket;

//                InputStream in = socket.getInputStream();
//                BufferedReader r = new BufferedReader(new InputStreamReader(in));
//                OutputStream outStream = socket.getOutputStream();
//                out = new PrintWriter(new OutputStreamWriter(outStream), true);
//
//                out.println("Enter your name: ");
//                userName = r.readLine();
//                String message = userName + " has joined the chat.";
//                System.out.println(message);
//
//                sendHistoryToClient(this);
//                broadcastMessage(this, message);
//
//                String messageRecu;
//                while ((messageRecu = r.readLine()) != null) {
//                    message = userName + ": " + messageRecu;
//                    System.out.println(message);
//                    broadcastMessage(this, message);
//                }
//
//                String msgLeave = userName + " has left the chat.";
//                System.out.println(msgLeave);
//                broadcastMessage(this, msgLeave);
//
//            } catch (IOException e) {
//                System.out.println("Client error");
//            }

    }
}