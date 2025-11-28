package org.example.chatft.service;

import org.example.chatft.config.NetworkConfig;
import org.example.chatft.handler.TcpConnectionHandler;
import org.example.chatft.model.User;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

public class TcpService {
    private final String nickname;
    private final ServerSocket serverSocket;
    private final int tcpPort;
    private final ExecutorService executor;
    private final TcpConnectionHandler connectionHandler;

    public TcpService(String nickname,
                      ExecutorService executor,
                      TcpConnectionHandler connectionHandler) throws IOException {
        this.nickname = nickname;
        this.executor = executor;
        this.connectionHandler = connectionHandler;

        this.serverSocket = new ServerSocket(0);
        this.tcpPort = serverSocket.getLocalPort();

        System.out.println("[TCP] Server started on port: " + tcpPort);
    }

    /**
     * Start listening for TCP connections
     */
    public void startListener() {
        executor.submit(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    System.out.println("[TCP] Connection from " + socket.getInetAddress().getHostAddress());

                    executor.submit(() -> {
                        connectionHandler.handleConnection(socket);
                        try {
                            socket.close();
                        } catch (IOException e) {
                            System.err.println("[TCP] Error closing socket: " + e.getMessage());
                        }
                    });

                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("[TCP-ERR] " + e.getMessage());
                    }
                    break;
                }
            }
            System.out.println("[TCP] Listener stopped");
        });
    }

    /**
     * Send text message to user
     */
    public void sendMessage(User user, String message) {
        executor.submit(() -> {
            try (Socket socket = new Socket(user.getIp(), user.getTcpPort());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String fullMsg = nickname + ": " + message;
                out.writeUTF("MSG:" + fullMsg);
                out.flush();

                System.out.println("[TCP-SEND] to " + user + " => " + fullMsg);

                // Small delay to ensure data is sent
                Thread.sleep(NetworkConfig.TCP_SEND_DELAY_MS);

            } catch (IOException e) {
                System.err.println("[ERROR] Failed to send to " + user + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Send TCP offline notification to a user
     */
    public void notifyOffline(User user) {
        executor.submit(() -> {
            try (Socket socket = new Socket(user.getIp(), user.getTcpPort());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String msg = "TCP_OFFLINE:" + nickname;
                out.writeUTF(msg);
                out.flush();

                System.out.println("[TCP-OFFLINE] Notified " + user.getNickname() + " that " + nickname + " is offline");

            } catch (IOException e) {
                System.out.println("[TCP-OFFLINE] Could not notify " + user.getNickname() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Get TCP port
     */
    public int getTcpPort() {
        return tcpPort;
    }

    /**
     * Close server socket
     */
    public void shutdown() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("[TCP] Shutdown complete");
        } catch (IOException e) {
            System.err.println("[TCP-SHUTDOWN-ERR] " + e.getMessage());
        }
    }
}