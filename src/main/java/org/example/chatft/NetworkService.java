package org.example.chatft;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NetworkService {
    private static final int UDP_PORT = 8888; // discovery + group broadcast

    private final String nickname;
    private final int tcpPort;
    private final DatagramSocket udpSocket;
    private final ServerSocket tcpServer;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Consumer<User> onUserOnline;
    private final Consumer<User> onUserOffline;
    private final Consumer<String> onMessage;
    private final Consumer<FileMessage> onFileReceived;
    private final Consumer<GroupMessage> onGroupMessage;
    private final Consumer<Group> onGroupDiscovered;

    private final Map<String, User> knownUsers = new ConcurrentHashMap<>();

    // Group management
    private final Set<String> joinedPublicGroups = ConcurrentHashMap.newKeySet();
    private final Map<String, String> joinedPrivateGroups = new ConcurrentHashMap<>(); // groupName -> password
    private final Map<String, Group> discoveredGroups = new ConcurrentHashMap<>();

    // Group file tracking: groupName -> (fileName -> filePath)
    private final Map<String, Map<String, String>> groupFiles = new ConcurrentHashMap<>();

    private final String downloadDir = "downloads";

    public NetworkService(String nickname,
                          Consumer<User> onUserOnline,
                          Consumer<User> onUserOffline,
                          Consumer<String> onMessage,
                          Consumer<FileMessage> onFileReceived,
                          Consumer<GroupMessage> onGroupMessage,
                          Consumer<Group> onGroupDiscovered) throws IOException {
        this.nickname = nickname;
        this.onUserOnline = onUserOnline;
        this.onUserOffline = onUserOffline;
        this.onMessage = onMessage;
        this.onFileReceived = onFileReceived;
        this.onGroupMessage = onGroupMessage;
        this.onGroupDiscovered = onGroupDiscovered;

        new File(downloadDir).mkdirs();

        this.udpSocket = new DatagramSocket(null);
        udpSocket.setReuseAddress(true);
        udpSocket.bind(new InetSocketAddress(UDP_PORT));

        this.tcpServer = new ServerSocket(0);
        this.tcpPort = tcpServer.getLocalPort();

        System.out.println("[START] " + nickname + " started with TCP port " + tcpPort);

        startUdpListener();
        startTcpListener();
        broadcastOnline();
    }

    // ============= GROUP API =============

    public void createPublicGroup(String groupName) {
        joinedPublicGroups.add(groupName);
        Group group = new Group(groupName, true, null);
        group.setJoined(true);
        discoveredGroups.put(groupName, group);
        sendUdp("GROUP_PUBLIC;" + groupName);
        System.out.println("[GROUP] Created public group: " + groupName);
    }

    public void createPrivateGroup(String groupName, String password) {
        joinedPrivateGroups.put(groupName, password);
        Group group = new Group(groupName, false, password);
        group.setJoined(true);
        discoveredGroups.put(groupName, group);
        sendUdp("GROUP_PRIVATE;" + groupName);
        System.out.println("[GROUP] Created private group: " + groupName);
    }

    public boolean joinGroup(String groupName, String password) {
        Group group = discoveredGroups.get(groupName);
        if (group == null) {
            System.out.println("[GROUP] Group not found: " + groupName);
            return false;
        }

        if (group.isPublic()) {
            joinedPublicGroups.add(groupName);
            group.setJoined(true);
            System.out.println("[GROUP] Joined public group: " + groupName);
            return true;
        } else {
            if (password != null && !password.trim().isEmpty()) {
                joinedPrivateGroups.put(groupName, password);
                group.setJoined(true);
                System.out.println("[GROUP] Joined private group: " + groupName);
                return true;
            } else {
                System.out.println("[GROUP] Password required for private group: " + groupName);
                return false;
            }
        }
    }

    public void leaveGroup(String groupName) {
        joinedPublicGroups.remove(groupName);
        joinedPrivateGroups.remove(groupName);
        Group group = discoveredGroups.get(groupName);
        if (group != null) {
            group.setJoined(false);
        }
        System.out.println("[GROUP] Left group: " + groupName);
    }

    public void sendGroupMessage(String groupName, String msg) {
        if (isJoinedToGroup(groupName)) {
            String payload = "GMSG;" + groupName + ";" + nickname + ";" + msg;
            sendUdp(payload);
            System.out.println("[GROUP] Sent message to " + groupName + ": " + msg);
        } else {
            System.out.println("[GROUP] Not joined to group: " + groupName);
        }
    }

    public void sendGroupFile(String groupName, String filePath) {
        if (!isJoinedToGroup(groupName)) {
            System.out.println("[GROUP] Not joined to group: " + groupName);
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("[GROUP] File not found: " + filePath);
            return;
        }

        // Store file path for this group
        groupFiles.computeIfAbsent(groupName, k -> new ConcurrentHashMap<>())
                .put(file.getName(), filePath);

        // Announce file to all members in group
        String fileNotification = "GFILE;" + groupName + ";" + nickname + ";" + file.getName() + ";" + file.length() + ";" + tcpPort;
        sendUdp(fileNotification);
        System.out.println("[GROUP] Announced file to group " + groupName + ": " + file.getName());
    }

    private boolean isJoinedToGroup(String groupName) {
        return joinedPublicGroups.contains(groupName) || joinedPrivateGroups.containsKey(groupName);
    }

    // ============= UDP LISTENER =============

    // ✅ FIX: Track received messages để tránh duplicate
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();

    private void startUdpListener() {
        executor.submit(() -> {
            byte[] buf = new byte[1024];
            while (!udpSocket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    udpSocket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("[UDP-RECV] from " + packet.getAddress().getHostAddress() + ": " + msg);
                    handleUdpMessage(msg, packet.getAddress());
                } catch (IOException e) {
                    break;
                }
            }
        });

        // ✅ FIX: Cleanup processed messages mỗi 10 giây
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10000);
                    processedMessages.clear();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void handleUdpMessage(String msg, InetAddress addr) {
        String[] parts = msg.split(";");
        if (parts.length < 1) return;

        String type = parts[0];

        // ✅ FIX: Chỉ check duplicate cho group messages
        if (type.equals("GMSG") || type.equals("GFILE")) {
            // Tạo unique ID cho message: type + content + timestamp (làm tròn 100ms)
            long timestamp = System.currentTimeMillis() / 100; // Làm tròn 100ms
            String messageId = msg + "@" + timestamp;

            if (processedMessages.contains(messageId)) {
                System.out.println("[UDP-SKIP] Duplicate message ignored: " + msg.substring(0, Math.min(50, msg.length())));
                return;
            }
            processedMessages.add(messageId);
        }

        switch (type) {
            case "ONLINE":
                if (parts.length < 3) return;
                String nick = parts[1];
                int port = Integer.parseInt(parts[2]);

                if (!nick.equals(nickname)) {
                    // ✅ FIX: Key chỉ dùng nickname (logic demo1)
                    String key = nick;

                    User user = new User(nick, addr.getHostAddress(), port);

                    if (!knownUsers.containsKey(key)) {
                        knownUsers.put(key, user);
                        System.out.println("[INFO] User joined: " + user);
                        onUserOnline.accept(user);

                        // ✅ FIX: Phản hồi NGAY (logic demo1)
                        broadcastOnline();

                        // ✅ FIX: Broadcast groups NGAY, không delay
                        for (String groupName : joinedPublicGroups) {
                            sendUdp("GROUP_PUBLIC;" + groupName);
                        }
                        for (String groupName : joinedPrivateGroups.keySet()) {
                            sendUdp("GROUP_PRIVATE;" + groupName);
                        }
                    }
                }
                break;

            case "OFFLINE":
                if (parts.length < 2) return;
                String offlineNick = parts[1];

                // ✅ FIX: Key chỉ dùng nickname (logic demo1)
                String offlineKey = offlineNick;

                User removed = knownUsers.remove(offlineKey);
                if (removed != null) {
                    System.out.println("[INFO] User left: " + removed);
                    onUserOffline.accept(removed);
                }
                break;

            case "GROUP_PUBLIC":
                if (parts.length >= 2) {
                    String groupName = parts[1];
                    Group group = discoveredGroups.computeIfAbsent(groupName,
                            k -> new Group(k, true, null));
                    onGroupDiscovered.accept(group);
                    System.out.println("[GROUP] Public group discovered: " + groupName);
                }
                break;

            case "GROUP_PRIVATE":
                if (parts.length >= 2) {
                    String groupName = parts[1];
                    Group group = discoveredGroups.computeIfAbsent(groupName,
                            k -> new Group(k, false, null));
                    onGroupDiscovered.accept(group);
                    System.out.println("[GROUP] Private group discovered: " + groupName);
                }
                break;

            case "GMSG":
                if (parts.length >= 4) {
                    String groupName = parts[1];
                    String sender = parts[2];
                    String content = parts[3];

                    if (!sender.equals(nickname) && isJoinedToGroup(groupName)) {
                        GroupMessage groupMsg = new GroupMessage(groupName, sender, content);
                        onGroupMessage.accept(groupMsg);
                        System.out.println("[GROUP] Message in " + groupName + " from " + sender + ": " + content);
                    }
                }
                break;

            case "GFILE":
                if (parts.length >= 6) {
                    String groupName = parts[1];
                    String sender = parts[2];
                    String fileName = parts[3];
                    long fileSize = Long.parseLong(parts[4]);
                    int senderTcpPort = Integer.parseInt(parts[5]);

                    if (!sender.equals(nickname) && isJoinedToGroup(groupName)) {
                        System.out.println("[GROUP] File in " + groupName + " from " + sender + ": " + fileName);
                        executor.submit(() -> downloadGroupFile(addr.getHostAddress(), senderTcpPort,
                                groupName, sender, fileName, fileSize));
                    }
                }
                break;
        }
    }

    private void downloadGroupFile(String senderIp, int senderTcpPort, String groupName,
                                   String sender, String fileName, long fileSize) {
        System.out.println("[GROUP] Downloading " + fileName + " from " + sender);

        try (Socket socket = new Socket(senderIp, senderTcpPort);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String request = "REQUEST_GROUP_FILE:" + groupName + ":" + fileName;
            out.writeUTF(request);
            System.out.println("[GROUP] Sent request: " + request);

            String response = in.readUTF();
            System.out.println("[GROUP] Response: " + response);

            if (response.startsWith("GROUP_FILE:")) {
                String[] responseParts = response.split(":");
                if (responseParts.length >= 5) {
                    String receivedFileName = responseParts[3];
                    long receivedFileSize = Long.parseLong(responseParts[4]);

                    String savedPath = receiveFile(in, receivedFileName, receivedFileSize);
                    if (savedPath != null) {
                        FileMessage fileMsg = new FileMessage(sender, receivedFileName, receivedFileSize, savedPath);
                        fileMsg.setGroupMessage(true);
                        fileMsg.setGroupName(groupName);
                        onFileReceived.accept(fileMsg);
                        System.out.println("[GROUP] Downloaded: " + receivedFileName + " -> " + savedPath);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[GROUP-FILE-ERR] " + e.getMessage());
        }
    }

    // ============= TCP LISTENER =============

    private void startTcpListener() {
        executor.submit(() -> {
            while (!tcpServer.isClosed()) {
                try {
                    Socket socket = tcpServer.accept();
                    System.out.println("[TCP] Connection from " + socket.getInetAddress().getHostAddress());
                    executor.submit(() -> handleTcpClient(socket));
                } catch (IOException e) {
                    break;
                }
            }
        });
    }

    private void handleTcpClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String header = in.readUTF();

            if (header.startsWith("MSG:")) {
                String msg = header.substring(4);
                onMessage.accept(msg);

            } else if (header.startsWith("FILE:")) {
                String[] parts = header.split(":");
                if (parts.length >= 4) {
                    String sender = parts[1];
                    String fileName = parts[2];
                    long fileSize = Long.parseLong(parts[3]);
                    String savedPath = receiveFile(in, fileName, fileSize);
                    if (savedPath != null) {
                        FileMessage fileMsg = new FileMessage(sender, fileName, fileSize, savedPath);
                        onFileReceived.accept(fileMsg);
                    }
                }

            } else if (header.startsWith("REQUEST_GROUP_FILE:")) {
                String[] parts = header.split(":");
                if (parts.length >= 3) {
                    String groupName = parts[1];
                    String fileName = parts[2];
                    handleGroupFileRequest(out, groupName, fileName);
                }
            }
        } catch (IOException e) {
            System.out.println("[TCP-ERR] " + e.getMessage());
        }
    }

    private void handleGroupFileRequest(DataOutputStream out, String groupName, String fileName) {
        System.out.println("[GROUP] File request: " + groupName + "/" + fileName);

        try {
            if (!isJoinedToGroup(groupName)) {
                out.writeUTF("ERROR:Not in group");
                out.flush();
                return;
            }

            Map<String, String> files = groupFiles.get(groupName);
            String filePath = (files != null) ? files.get(fileName) : null;

            if (filePath == null) {
                out.writeUTF("ERROR:File not found");
                out.flush();
                return;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                out.writeUTF("ERROR:File not found");
                out.flush();
                return;
            }

            String header = "GROUP_FILE:" + groupName + ":" + nickname + ":" + fileName + ":" + file.length();
            out.writeUTF(header);
            out.flush(); // ✅ FIX: Flush header

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush(); // ✅ FIX: Flush toàn bộ file
            }

            System.out.println("[GROUP] Sent file: " + fileName);

            // ✅ FIX: Đợi để đảm bảo file được gửi hết
            Thread.sleep(100);

        } catch (IOException e) {
            System.out.println("[GROUP-FILE-ERR] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String receiveFile(DataInputStream in, String fileName, long fileSize) {
        try {
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File file = new File(downloadDir, System.currentTimeMillis() + "_" + safeName);

            System.out.println("[FILE] Receiving: " + fileName + " (" + fileSize + " bytes)");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                long totalRead = 0;
                while (totalRead < fileSize) {
                    int toRead = (int) Math.min(buffer.length, fileSize - totalRead);
                    int bytesRead = in.read(buffer, 0, toRead);
                    if (bytesRead == -1) break;
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
                System.out.println("[FILE] Received " + totalRead + "/" + fileSize + " bytes");
                return file.getAbsolutePath();
            }
        } catch (IOException e) {
            System.out.println("[FILE-ERR] " + e.getMessage());
            return null;
        }
    }

    // ============= SEND METHODS =============

    public void sendMessage(User user, String message) {
        executor.submit(() -> {
            try (Socket socket = new Socket(user.getIp(), user.getTcpPort());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                String fullMsg = nickname + ": " + message;
                out.writeUTF("MSG:" + fullMsg);
                out.flush(); // ✅ FIX: Đảm bảo data được gửi hết
                System.out.println("[TCP-SEND] to " + user + " => " + fullMsg);

                // ✅ FIX: Đợi 1 chút để đảm bảo data được gửi
                Thread.sleep(50);
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to send to " + user + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void sendFile(User user, String filePath) {
        executor.submit(() -> {
            File file = new File(filePath);
            if (!file.exists()) return;
            try (Socket socket = new Socket(user.getIp(), user.getTcpPort());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {
                String header = "FILE:" + nickname + ":" + file.getName() + ":" + file.length();
                out.writeUTF(header);
                out.flush(); // ✅ FIX: Flush header trước

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush(); // ✅ FIX: Flush toàn bộ file
                System.out.println("[FILE] Sent: " + file.getName());

                // ✅ FIX: Đợi để đảm bảo file được gửi hết
                Thread.sleep(100);
            } catch (IOException e) {
                System.err.println("[FILE-ERR] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // ============= UDP SEND =============

    public void broadcastOnline() {
        sendUdp("ONLINE;" + nickname + ";" + tcpPort);
    }

    public void broadcastOffline() {
        sendUdp("OFFLINE;" + nickname + ";" + tcpPort);
        shutdown();
    }

    private void sendUdp(String msg) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    InetAddress broadcast = addr.getBroadcast();
                    if (broadcast == null) continue;

                    DatagramPacket packet = new DatagramPacket(
                            msg.getBytes(),
                            msg.length(),
                            broadcast,
                            UDP_PORT
                    );
                    socket.send(packet);
                    System.out.println("[UDP-SEND] to " + broadcast.getHostAddress() + " => " + msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ============= UTILITY METHODS =============

    public Set<String> getJoinedGroups() {
        Set<String> allGroups = new HashSet<>(joinedPublicGroups);
        allGroups.addAll(joinedPrivateGroups.keySet());
        return allGroups;
    }

    public boolean isInGroup(String groupName) {
        return isJoinedToGroup(groupName);
    }

    private void shutdown() {
        try { udpSocket.close(); } catch (Exception ignored) {}
        try { tcpServer.close(); } catch (Exception ignored) {}
        executor.shutdownNow();
        System.out.println("[INFO] NetworkService shutdown for " + nickname);
    }
}