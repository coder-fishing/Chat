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
        this.tcpService = new TcpService(nickname, executor, tcpHandler);

        // Initialize UDP service
        UdpMessageHandler udpHandler = new UdpMessageHandler(
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
        return groupService.joinGroup(groupName, password);
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