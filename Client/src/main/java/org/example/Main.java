package org.example;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) {
        String address = "localhost";
        int port = 12345;
        Client client = new Client(address, port);
        try {
            client.connect();
        } catch (IOException | InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed", e);
        }
    }
}