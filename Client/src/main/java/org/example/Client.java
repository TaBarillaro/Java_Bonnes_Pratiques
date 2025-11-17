package org.example;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// client TCP pour la communication avec le serveur
// il gère la connexion, l'envoie et le reception des messages
public class Client {
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 5000;
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int THREAD_POOL_SIZE = 2;
    private static final int CONNECTION_DELAY_MS = 100;

    private String serverAddress;
    private int serverPort;
    private Socket clientSocket;
    private ExecutorService executorService;
    private BufferedReader consoleReader;
    private int messageCount = 0;

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    // méthode pour se connecter au serveur
    public void connect() throws IOException, InterruptedException, ExecutionException {
        clientSocket = new Socket(serverAddress, serverPort);
        clientSocket.setSoTimeout(DEFAULT_SOCKET_TIMEOUT_MS);
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        Future<?> receiveTask  = executorService.submit(this::receiveMessages);
        Thread.sleep(CONNECTION_DELAY_MS);
        Future<?> sendTask  = executorService.submit(this::sendMessages);

        receiveTask.get();
        sendTask.get();

        shutdown();
    }

    // reception des messages depuis le serveur et stamp
    private void receiveMessages() {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.length() > MAX_MESSAGE_LENGTH) {
                    System.err.println("Message trop long, ignoré.");
                    continue;
                }
                System.out.println("\r" + message);
                System.out.print("You: ");
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Socket timed out.");
        } catch (IOException e) {
            System.err.println("Déconnexion du serveur: " + e.getMessage());
            throw new RuntimeException("Erreur de réception des messages", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Erreur de socket close: " + e.getMessage());
            }
        }
    }

    // envoi messages utilisateur vers le serveur
    private void sendMessages() {
        BufferedWriter writer = null;
        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                if (userInput.trim().isEmpty()) {
                    continue;
                }
                writer.write(userInput);
                writer.newLine();
                writer.flush();
                messageCount++;
                System.out.print("You: ");
            }
        } catch (IOException e) {
            System.err.println("Error lors de l'envoi du message: " + e.getMessage());
            throw new RuntimeException("Error lors de l'envoi du message: ", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                System.err.println("Error lors de l'envoi du message: " + e.getMessage());
            }
        }
    }

    // arrêt propre de la chat
    private void shutdown() throws IOException {
        if (executorService != null) {
            executorService.shutdown();
        }
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
        }
    }
}