package org.example;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

// client TCP pour la communication avec le serveur
// il gère la connexion, l'envoie et le reception des messages
public class Client {
    private static final int MAX_MESSAGE_LENGTH = 1024;
    private static final int THREAD_POOL_SIZE = 2;// Nombre de threads : un pour l'envoi, un pour la réception
    private static final int CONNECTION_DELAY_MS = 100;// Délai avant de lancer l'envoi après la réception
    private static final int SOCKET_TIMEOUT_MS = 30000; // Timeout sur lo socket

    private String serverAddress;
    private int serverPort;
    private Socket clientSocket;
    private final Object socketLock = new Object(); // lock pour proteger le socket
    private ExecutorService executorService;
    private BufferedReader consoleReader;
    private final AtomicInteger messageCount = new AtomicInteger(0);

    public Client(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    // méthode pour se connecter au serveur et gérer l'envoi/réception
    public void connect() throws IOException, InterruptedException, ExecutionException {
        clientSocket = new Socket(serverAddress, serverPort);
        clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS); // Timeout ajouté pour éviter blocage
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Lancer un thread pour la réception des messages
        Future<?> receiveTask  = executorService.submit(this::receiveMessages);
        // Petit délai pour laisser le thread de réception s'initialiser
        Thread.sleep(CONNECTION_DELAY_MS);
        // Lancer un thread pour l'envoi des messages
        Future<?> sendTask  = executorService.submit(this::sendMessages);

        // Attendre que les threads se terminent (bloquant)
        receiveTask.get();
        sendTask.get();

        // Fermeture propre du client
        shutdown();
    }

    // reception des messages depuis le serveur et stamp
    private void receiveMessages() {
        if (clientSocket == null) {
            return;
        }
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
                synchronized (System.out) {
                    System.out.println("\r" + message);
                    System.out.print("You: ");
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("Socket timed out."); // Timeout si le serveur ne répond pas
        } catch (IOException e) {
            System.err.println("Déconnexion du serveur: " + e.getMessage());
            throw new RuntimeException("Erreur de réception des messages", e);
        } finally {
            // Fermeture du socket à la fin de la réception
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
                synchronized (socketLock) { // protection socket pendant l'écriture
                    writer.write(userInput);
                    writer.newLine();
                    writer.flush();
                }
                messageCount.incrementAndGet();
                System.out.print("You: ");
            }
        } catch (IOException e) {
            System.err.println("Error lors de l'envoi du message: " + e.getMessage());
            throw new RuntimeException("Error lors de l'envoi du message: ", e);
        } finally {
            // Fermeture du writer pour libérer les ressources
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
            executorService.shutdown(); // arret des threads
        }
        synchronized (socketLock) {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        }
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close(); // fermature du socket
        }
    }
}