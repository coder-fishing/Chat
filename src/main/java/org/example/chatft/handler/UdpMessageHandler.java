package org.example.chatft.handler;

import org.example.chatft.model.Group;
import org.example.chatft.model.GroupMessage;
import org.example.chatft.model.User;
import org.example.chatft.repository.GroupRepository;
import org.example.chatft.repository.UserRepository;
import org.example.chatft.utils.MessageDeduplicator;

import java.net.InetAddress;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;

public class UdpMessageHandler {
    private final String nickname;
    private final int tcpPort;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MessageDeduplicator deduplicator;

    // Callbacks
    private final Consumer<User> onUserOnline;
    private final Consumer<User> onUserOffline;
    private final Consumer<GroupMessage> onGroupMessage;
    private final Consumer<Group> onGroupDiscovered;
    private final Runnable onNewUserDetected;
    private final Consumer<FileDownloadRequest> onFileDownloadRequest;

    public UdpMessageHandler(String nickname, int tcpPort,
                             UserRepository userRepository,
                             GroupRepository groupRepository,
                             MessageDeduplicator deduplicator,
                             Consumer<User> onUserOnline,
                             Consumer<User> onUserOffline,
                             Consumer<GroupMessage> onGroupMessage,
                             Consumer<Group> onGroupDiscovered,
                             Runnable onNewUserDetected,
                             Consumer<FileDownloadRequest> onFileDownloadRequest) {
        this.nickname = nickname;
        this.tcpPort = tcpPort;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.deduplicator = deduplicator;
        this.onUserOnline = onUserOnline;
        this.onUserOffline = onUserOffline;
        this.onGroupMessage = onGroupMessage;
        this.onGroupDiscovered = onGroupDiscovered;
        this.onNewUserDetected = onNewUserDetected;
        this.onFileDownloadRequest = onFileDownloadRequest;
    }

    public void handleMessage(String msg, InetAddress addr) {
        String[] parts = msg.split(";");
        if (parts.length < 1) return;

        String type = parts[0];

        // Check for duplicates (only for group messages)
        if (deduplicator.isDuplicate(type, msg)) {
            System.out.println("[UDP-SKIP] Duplicate: " + msg.substring(0, Math.min(50, msg.length())));
            return;
        }

        switch (type) {
            case "ONLINE":
                handleOnline(parts, addr);
                break;
            case "OFFLINE":
                handleOffline(parts);
                break;
            case "GROUP_PUBLIC":
                handleGroupPublic(parts);
                break;
            case "GROUP_PRIVATE":
                handleGroupPrivate(parts);
                break;
            case "GMSG":
                handleGroupMessage(parts);
                break;
            case "GFILE":
                handleGroupFile(parts, addr);
                break;
        }
    }

    private void handleOnline(String[] parts, InetAddress addr) {
        if (parts.length < 3) return;

        String nick = parts[1];
        int port = Integer.parseInt(parts[2]);

        if (!nick.equals(nickname)) {
            User user = new User(nick, addr.getHostAddress(), port);

            if (userRepository.addUser(user)) {
                System.out.println("[INFO] User joined: " + user);
                onUserOnline.accept(user);

                // Notify that new user detected (will trigger response)
                onNewUserDetected.run();
            }
        }
    }

    private void handleOffline(String[] parts) {
        if (parts.length < 2) return;

        String offlineNick = parts[1];
        User removed = userRepository.removeUser(offlineNick);

        if (removed != null) {
            System.out.println("[INFO] User left: " + removed);
            onUserOffline.accept(removed);
        }
    }

    private void handleGroupPublic(String[] parts) {
        if (parts.length >= 2) {
            String groupName = parts[1];
            Group group = groupRepository.addDiscoveredGroup(groupName, true);
            onGroupDiscovered.accept(group);
            System.out.println("[GROUP] Public group discovered: " + groupName);
        }
    }

    private void handleGroupPrivate(String[] parts) {
        if (parts.length >= 2) {
            String groupName = parts[1];
            Group group = groupRepository.addDiscoveredGroup(groupName, false);
            onGroupDiscovered.accept(group);
            System.out.println("[GROUP] Private group discovered: " + groupName);
        }
    }

    private void handleGroupMessage(String[] parts) {
        if (parts.length >= 4) {
            String groupName = parts[1];
            String sender = parts[2];
            String content = parts[3];

            if (!sender.equals(nickname) && groupRepository.isJoined(groupName)) {
                GroupMessage groupMsg = new GroupMessage(groupName, sender, content);
                onGroupMessage.accept(groupMsg);
                System.out.println("[GROUP] Message in " + groupName + " from " + sender + ": " + content);
            }
        }
    }

    private void handleGroupFile(String[] parts, InetAddress addr) {
        if (parts.length >= 6) {
            String groupName = parts[1];
            String sender = parts[2];
            String fileName = parts[3];
            long fileSize = Long.parseLong(parts[4]);
            int senderTcpPort = Integer.parseInt(parts[5]);

            if (!sender.equals(nickname) && groupRepository.isJoined(groupName)) {
                System.out.println("[GROUP] File in " + groupName + " from " + sender + ": " + fileName);

                FileDownloadRequest request = new FileDownloadRequest(
                        addr.getHostAddress(), senderTcpPort, groupName, sender, fileName, fileSize
                );
                onFileDownloadRequest.accept(request);
            }
        }
    }

    // Inner class for file download request
    public static class FileDownloadRequest {
        public final String senderIp;
        public final int senderTcpPort;
        public final String groupName;
        public final String sender;
        public final String fileName;
        public final long fileSize;

        public FileDownloadRequest(String senderIp, int senderTcpPort, String groupName,
                                   String sender, String fileName, long fileSize) {
            this.senderIp = senderIp;
            this.senderTcpPort = senderTcpPort;
            this.groupName = groupName;
            this.sender = sender;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
    }
}