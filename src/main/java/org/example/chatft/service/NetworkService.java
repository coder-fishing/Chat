package org.example.chatft.service;

import org.example.chatft.handler.TcpConnectionHandler;
import org.example.chatft.handler.UdpMessageHandler;
import org.example.chatft.model.*;
import org.example.chatft.repository.GroupRepository;
import org.example.chatft.repository.UserRepository;
import org.example.chatft.service.*;
import org.example.chatft.utils.MessageDeduplicator;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class NetworkService {
    private final String nickname;

    // Repositories
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    // Services
    private final UdpService udpService;
    private final TcpService tcpService;
    private final GroupService groupService;
    private final FileTransferService fileTransferService;

    // Utilities
    private final MessageDeduplicator messageDeduplicator;
    private final ExecutorService executor;

    // Callbacks
    private final Consumer<User> onUserOnline;
    private final Consumer<User> onUserOffline;
    private final Consumer<String> onMessage;
    private final Consumer<FileMessage> onFileReceived;
    private final Consumer<GroupMessage> onGroupMessage;
    private final Consumer<Group> onGroupDiscovered;

    private UdpMessageHandler udpHandler;
    private Consumer<User> incomingVideoCallCallback;
    
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

        // Initialize executor
        this.executor = Executors.newCachedThreadPool();

        // Initialize repositories
        this.userRepository = new UserRepository();
        this.groupRepository = new GroupRepository();

        // Initialize utilities
        this.messageDeduplicator = new MessageDeduplicator();

        // Initialize FileTransferService
        this.fileTransferService = new FileTransferService(nickname, executor, onFileReceived);

        // Initialize TCP service
        TcpConnectionHandler tcpHandler = new TcpConnectionHandler(
                nickname, groupRepository, fileTransferService, onMessage, onFileReceived
        );
        
        // Set video call request handler
        tcpHandler.setOnVideoCallRequest(parts -> {
            String fromNickname = parts[1].trim();
            int fromPort = Integer.parseInt(parts[2].trim());
            
            User caller = userRepository.getUser(fromNickname);
            if (caller != null && udpHandler != null) {
                System.out.println("[TCP-VIDEO] Forwarding incoming call from " + fromNickname + " to UI");
                // Use Platform.runLater to update UI
                javafx.application.Platform.runLater(() -> {
                    if (udpHandler != null) {
                        var callback = getIncomingVideoCallCallback();
                        if (callback != null) {
                            callback.accept(caller);
                        }
                    }
                });
            }
        });
        
        this.tcpService = new TcpService(nickname, executor, tcpHandler);

        // Initialize UDP service
        this.udpHandler = new UdpMessageHandler(
                nickname,
                tcpService.getTcpPort(),
                userRepository,
                groupRepository,
                messageDeduplicator,
                onUserOnline,
                onUserOffline,
                onGroupMessage,
                onGroupDiscovered,
                this::handleNewUserDetected,
                this::handleFileDownloadRequest
        );
        this.udpService = new UdpService(nickname, tcpService.getTcpPort(), executor, udpHandler);

        // Initialize GroupService
        this.groupService = new GroupService(
                nickname, groupRepository, udpService, fileTransferService, tcpService.getTcpPort()
        );

        // Start services
        System.out.println("[START] " + nickname + " started with TCP port " + tcpService.getTcpPort());
        udpService.startListener();
        tcpService.startListener();
        udpService.broadcastOnline();
    }

    // ============= PUBLIC API =============

    // Group operations
    public void createPublicGroup(String groupName) {
        groupService.createPublicGroup(groupName);
    }

    public void createPrivateGroup(String groupName, String password) {
        groupService.createPrivateGroup(groupName, password);
    }

    public boolean joinGroup(String groupName, String password) {
        Group group = groupRepository.getGroup(groupName);
        if (group == null) {
            System.out.println("[GROUP] ❌ Group not found: " + groupName);
            return false;
        }

        if (group.isPublic()) {
            groupRepository.joinPublicGroup(groupName);
            System.out.println("[GROUP] ✅ Joined public group: " + groupName);
            return true;
        } else {
            // Lấy password gốc của group (do người tạo broadcast)
            String savedPassword = group.getPassword();

            if (savedPassword == null) {
                System.out.println("[GROUP] ⚠️ No password saved for private group: " + groupName);
                return false;
            }

            if (password != null && password.equals(savedPassword)) {
                groupRepository.joinPrivateGroup(groupName, password);
                System.out.println("[GROUP] ✅ Joined private group: " + groupName);
                return true;
            } else {
                System.out.println("[GROUP] ❌ Incorrect password for private group: " + groupName);
                return false;
            }
        }
    }


    public void leaveGroup(String groupName) {
        groupService.leaveGroup(groupName);
    }

    public void sendGroupMessage(String groupName, String message) {
        groupService.sendGroupMessage(groupName, message);
    }

    public void sendGroupFile(String groupName, String filePath) {
        groupService.sendGroupFile(groupName, filePath);
    }

    public Set<String> getJoinedGroups() {
        return groupService.getJoinedGroups();
    }

    public boolean isInGroup(String groupName) {
        return groupService.isInGroup(groupName);
    }

    // Direct messaging
    public void sendMessage(User user, String message) {
        tcpService.sendMessage(user, message);
    }

    public void sendFile(User user, String filePath) {
        fileTransferService.sendFile(user, filePath);
    }

    // Network status
    public void broadcastOnline() {
        udpService.broadcastOnline();
    }

    public void broadcastOffline() {
        udpService.broadcastOffline();
        shutdown();
    }

    // ============= PRIVATE METHODS =============

    /**
     * Called when new user is detected - broadcast our presence and groups
     */
    private void handleNewUserDetected() {
        udpService.broadcastOnline();
        groupService.broadcastAllGroups();
    }

    /**
     * Called when group file download is requested
     */
    private void handleFileDownloadRequest(UdpMessageHandler.FileDownloadRequest request) {
        fileTransferService.downloadGroupFile(
                request.senderIp,
                request.senderTcpPort,
                request.groupName,
                request.sender,
                request.fileName,
                request.fileSize
        );
    }

    /**
     * Set incoming video call callback
     */
    public void setOnIncomingVideoCall(Consumer<User> callback) {
        this.incomingVideoCallCallback = callback;
        udpHandler.setOnIncomingVideoCall(callback);
    }
    
    private Consumer<User> getIncomingVideoCallCallback() {
        return incomingVideoCallCallback;
    }
    
    /**
     * Send video call request to a user (via TCP direct)
     */
    public void sendVideoCallRequest(User targetUser) {
        executor.submit(() -> {
            try (java.net.Socket socket = new java.net.Socket(targetUser.getIp(), targetUser.getTcpPort());
                 java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream())) {

                String msg = "VIDEO_CALL_REQUEST;" + nickname + ";" + tcpService.getTcpPort();
                out.writeUTF(msg);
                out.flush();

                System.out.println("[VIDEO-SIGNAL] Sent VIDEO_CALL_REQUEST to " + targetUser.getNickname() + " (" + targetUser.getIp() + ":" + targetUser.getTcpPort() + ")");

            } catch (Exception e) {
                System.err.println("[VIDEO-SIGNAL-ERR] Failed to send call request to " + targetUser.getNickname() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Shutdown all services
     */
    private void shutdown() {
        messageDeduplicator.shutdown();
        udpService.shutdown();
        tcpService.shutdown();
        executor.shutdownNow();
        System.out.println("[INFO] NetworkService shutdown for " + nickname);
    }
}