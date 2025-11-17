package org.example;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Port du serveur
        int port = 12345;
        Server server = new Server(port);
        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed", e);
        }
    }
}