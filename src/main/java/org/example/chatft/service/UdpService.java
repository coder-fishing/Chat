package org.example.chatft.service;

import org.example.chatft.config.NetworkConfig;
import org.example.chatft.handler.UdpMessageHandler;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class UdpService {
    private final String nickname;
    private final int tcpPort;
    private MulticastSocket socket;
    private DatagramSocket broadcastSocket;
    private final ExecutorService executor;
    private final UdpMessageHandler messageHandler;

    // Multicast configuration
    private static final String MULTICAST_GROUP = "230.0.0.1";
    private InetAddress multicastGroup;
    private List<NetworkInterface> activeInterfaces = new ArrayList<>();

    public UdpService(String nickname, int tcpPort,
                      ExecutorService executor,
                      UdpMessageHandler messageHandler) throws IOException {
        this.nickname = nickname;
        this.tcpPort = tcpPort;
        this.executor = executor;
        this.messageHandler = messageHandler;

        // Setup Multicast socket
        this.socket = new MulticastSocket(NetworkConfig.UDP_PORT);
        this.socket.setReuseAddress(true);
        this.multicastGroup = InetAddress.getByName(MULTICAST_GROUP);

        // Setup separate broadcast socket
        this.broadcastSocket = new DatagramSocket();
        this.broadcastSocket.setBroadcast(true);

        // Detect and join multicast on all active network interfaces
        detectAndJoinInterfaces();

        System.out.println("[UDP-MULTICAST] Started on port: " + NetworkConfig.UDP_PORT);
        System.out.println("[UDP-MULTICAST] Group: " + MULTICAST_GROUP);
        System.out.println("[UDP-MULTICAST] Active interfaces: " + activeInterfaces.size());
    }

    /**
     * Detect all active network interfaces and join multicast group
     */
    private void detectAndJoinInterfaces() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            
            // Skip loopback and inactive interfaces
            if (iface.isLoopback() || !iface.isUp()) {
                continue;
            }
            
            // Check if interface supports multicast
            if (!iface.supportsMulticast()) {
                continue;
            }
            
            // Check if interface has any IP addresses
            if (!iface.getInetAddresses().hasMoreElements()) {
                continue;
            }
            
            try {
                // Join multicast group on this interface
                socket.joinGroup(new InetSocketAddress(multicastGroup, NetworkConfig.UDP_PORT), iface);
                activeInterfaces.add(iface);
                
                System.out.println("[UDP-MULTICAST] ✅ Joined on interface: " + iface.getDisplayName() 
                    + " (" + getInterfaceIPs(iface) + ")");
                
            } catch (IOException e) {
                System.out.println("[UDP-MULTICAST] ⚠️ Failed to join on " + iface.getDisplayName() + ": " + e.getMessage());
            }
        }
        
        if (activeInterfaces.isEmpty()) {
            System.err.println("[UDP-MULTICAST] ❌ WARNING: No active network interfaces found!");
        }
    }
    
    /**
     * Get IP addresses of an interface
     */
    private String getInterfaceIPs(NetworkInterface iface) {
        StringBuilder ips = new StringBuilder();
        Enumeration<InetAddress> addresses = iface.getInetAddresses();
        while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            if (ips.length() > 0) ips.append(", ");
            ips.append(addr.getHostAddress());
        }
        return ips.toString();
    }

    /**
     * Start listening for UDP messages on ALL interfaces
     */
    public void startListener() {
        // Create separate listener for each active interface
        for (NetworkInterface iface : activeInterfaces) {
            executor.submit(() -> listenOnInterface(iface));
        }
    }
    
    /**
     * Listen for multicast messages on a specific interface
     */
    private void listenOnInterface(NetworkInterface iface) {
        MulticastSocket ifaceSocket = null;
        try {
            // Create dedicated socket for this interface
            ifaceSocket = new MulticastSocket(NetworkConfig.UDP_PORT);
            ifaceSocket.setReuseAddress(true);
            ifaceSocket.setNetworkInterface(iface);
            
            // Join multicast group on this interface
            ifaceSocket.joinGroup(new InetSocketAddress(multicastGroup, NetworkConfig.UDP_PORT), iface);
            
            System.out.println("[UDP-LISTEN] Started listener on " + iface.getDisplayName());
            
            byte[] buf = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (!ifaceSocket.isClosed()) {
                try {
                    ifaceSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    String fromIP = packet.getAddress().getHostAddress();
                    
                    System.out.println("[UDP-RECV][" + iface.getDisplayName() + "] from " + fromIP + ": " + msg);

                    messageHandler.handleMessage(msg, packet.getAddress());

                } catch (IOException e) {
                    if (!ifaceSocket.isClosed()) {
                        System.err.println("[UDP-ERR][" + iface.getDisplayName() + "] " + e.getMessage());
                    }
                    break;
                }
            }

            System.out.println("[UDP-LISTEN] Stopped listener on " + iface.getDisplayName());
            
        } catch (IOException e) {
            System.err.println("[UDP-LISTEN-ERR] Failed to start listener on " + iface.getDisplayName() + ": " + e.getMessage());
        } finally {
            if (ifaceSocket != null && !ifaceSocket.isClosed()) {
                try {
                    ifaceSocket.leaveGroup(new InetSocketAddress(multicastGroup, NetworkConfig.UDP_PORT), iface);
                    ifaceSocket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Send message via:
     * - Multicast for LAN
     * - Broadcast to each interface (for compatibility)
     * - Direct unicast to Zerotier subnet
     */
    public void sendMessage(String msg) {
        byte[] buf = msg.getBytes();
        
        // 1. Send multicast (works on LAN)
        try {
            socket.setTimeToLive(32);
            DatagramPacket packet = new DatagramPacket(
                    buf, buf.length, multicastGroup, NetworkConfig.UDP_PORT
            );
            socket.send(packet);
            System.out.println("[UDP-MULTICAST-SEND] => " + msg);
        } catch (IOException e) {
            System.err.println("[UDP-MULTICAST-ERR] " + e.getMessage());
        }
        
        // 2. Send broadcast on each interface (including Zerotier)
        for (NetworkInterface iface : activeInterfaces) {
            try {
                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = addr.getBroadcast();
                    if (broadcast != null) {
                        DatagramPacket packet = new DatagramPacket(
                                buf, buf.length, broadcast, NetworkConfig.UDP_PORT
                        );
                        broadcastSocket.send(packet);
                        System.out.println("[UDP-BROADCAST-SEND][" + iface.getDisplayName() + "] to " + broadcast.getHostAddress());
                    }
                }
            } catch (IOException e) {
                System.err.println("[UDP-BROADCAST-ERR][" + iface.getDisplayName() + "] " + e.getMessage());
            }
        }
        
        // 3. For Zerotier compatibility: scan and send to all IPs in Zerotier subnet
        sendToZerotierSubnet(buf);
    }

    /**
     * Send to Zerotier subnet using directed broadcast
     * Zerotier typically uses /24 subnets like 192.168.192.0/24
     */
    private void sendToZerotierSubnet(byte[] buf) {
        for (NetworkInterface iface : activeInterfaces) {
            String ifaceName = iface.getDisplayName().toLowerCase();
            
            // Detect Zerotier interface
            if (ifaceName.contains("zerotier") || ifaceName.contains("zt")) {
                try {
                    for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                        InetAddress ifaceAddr = addr.getAddress();
                        
                        // Only process IPv4
                        if (ifaceAddr instanceof java.net.Inet4Address) {
                            String myIP = ifaceAddr.getHostAddress();
                            
                            // Calculate broadcast address based on subnet mask
                            short prefixLength = addr.getNetworkPrefixLength();
                            String subnet = myIP.substring(0, myIP.lastIndexOf('.'));
                            String broadcastIP = subnet + ".255"; // Assume /24 for simplicity
                            
                            try {
                                InetAddress broadcast = InetAddress.getByName(broadcastIP);
                                DatagramPacket packet = new DatagramPacket(
                                        buf, buf.length, broadcast, NetworkConfig.UDP_PORT
                                );
                                broadcastSocket.send(packet);
                                System.out.println("[UDP-ZEROTIER] Sent to " + broadcastIP + " on " + iface.getDisplayName());
                            } catch (IOException e) {
                                System.err.println("[UDP-ZEROTIER-ERR] Failed to send to " + broadcastIP + ": " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[UDP-ZEROTIER-ERR] " + e.getMessage());
                }
            }
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
            if (socket != null && !socket.isClosed()) {
                // Leave multicast group on all interfaces before closing
                for (NetworkInterface iface : activeInterfaces) {
                    try {
                        socket.leaveGroup(new InetSocketAddress(multicastGroup, NetworkConfig.UDP_PORT), iface);
                        System.out.println("[UDP-MULTICAST] Left group on " + iface.getDisplayName());
                    } catch (IOException e) {
                        // Ignore errors during shutdown
                    }
                }
                
                socket.close();
            }
            
            if (broadcastSocket != null && !broadcastSocket.isClosed()) {
                broadcastSocket.close();
            }
            
            System.out.println("[UDP] Shutdown complete");
        } catch (Exception e) {
            System.err.println("[UDP-SHUTDOWN-ERR] " + e.getMessage());
        }
    }
}