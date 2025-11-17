package org.example.chatft.service;

import org.example.chatft.config.NetworkConfig;
import org.example.chatft.handler.UdpMessageHandler;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ExecutorService;

public class UdpService {
    private final String nickname;
    private final int tcpPort;
    private final MulticastSocket multicastSocket;
    private final InetAddress multicastGroup;
    private final ExecutorService executor;
    private final UdpMessageHandler messageHandler;

    public UdpService(String nickname, int tcpPort,
                      ExecutorService executor,
                      UdpMessageHandler messageHandler) throws IOException {
        this.nickname = nickname;
        this.tcpPort = tcpPort;
        this.executor = executor;
        this.messageHandler = messageHandler;

        // Setup multicast socket
        this.multicastSocket = new MulticastSocket(NetworkConfig.UDP_PORT);
        this.multicastGroup = InetAddress.getByName(NetworkConfig.MULTICAST_GROUP);
        this.multicastSocket.joinGroup(multicastGroup);

        System.out.println("[MULTICAST] Joined group: " + NetworkConfig.MULTICAST_GROUP + ":" + NetworkConfig.UDP_PORT);
    }

    /**
     * Start listening for UDP messages
     */
    public void startListener() {
        executor.submit(() -> {
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (!multicastSocket.isClosed()) {
                try {
                    multicastSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("[MULTICAST-RECV] from " + packet.getAddress().getHostAddress() + ": " + msg);

                    messageHandler.handleMessage(msg, packet.getAddress());

                } catch (IOException e) {
                    if (!multicastSocket.isClosed()) {
                        System.err.println("[MULTICAST-ERR] " + e.getMessage());
                    }
                    break;
                }
            }

            System.out.println("[MULTICAST] Listener stopped");
        });
    }

    /**
     * Send message via multicast
     */
    public void sendMessage(String msg) {
        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setTimeToLive(NetworkConfig.MULTICAST_TTL);
            socket.joinGroup(multicastGroup);

            byte[] buf = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, multicastGroup, NetworkConfig.UDP_PORT);
            socket.send(packet);

            System.out.println("[MULTICAST-SEND] to " + multicastGroup.getHostAddress() + ":" + NetworkConfig.UDP_PORT + " => " + msg);

            socket.leaveGroup(multicastGroup);
        } catch (IOException e) {
            System.err.println("[MULTICAST-SEND-ERR] " + e.getMessage());
        }
    }

    /**
     * Broadcast ONLINE status
     */
    public void broadcastOnline() {
        sendMessage("ONLINE;" + nickname + ";" + tcpPort);
    }

    /**
     * Broadcast OFFLINE status
     */
    public void broadcastOffline() {
        sendMessage("OFFLINE;" + nickname + ";" + tcpPort);
    }

    /**
     * Broadcast public group
     */
    public void broadcastPublicGroup(String groupName) {
        sendMessage("GROUP_PUBLIC;" + groupName);
    }

    /**
     * Broadcast private group
     */
    public void broadcastPrivateGroup(String groupName, String password) {
        sendMessage("GROUP_PRIVATE;" + groupName + " ;" + password);
    }

    /**
     * Send group message
     */
    public void sendGroupMessage(String groupName, String senderNick, String message) {
        String payload = "GMSG;" + groupName + ";" + senderNick + ";" + message;
        sendMessage(payload);
    }

    /**
     * Announce group file
     */
    public void announceGroupFile(String groupName, String senderNick, String fileName, long fileSize, int tcpPort) {
        String payload = "GFILE;" + groupName + ";" + senderNick + ";" + fileName + ";" + fileSize + ";" + tcpPort;
        sendMessage(payload);
    }

    /**
     * Close socket and cleanup
     */
    public void shutdown() {
        try {
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                multicastSocket.leaveGroup(multicastGroup);
                multicastSocket.close();
            }
            System.out.println("[MULTICAST] Shutdown complete");
        } catch (IOException e) {
            System.err.println("[MULTICAST-SHUTDOWN-ERR] " + e.getMessage());
        }
    }
}