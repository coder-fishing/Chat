package org.example.chatft.handler;

import org.example.chatft.model.Group;
import org.example.chatft.model.GroupMessage;
import org.example.chatft.model.User;
import org.example.chatft.repository.GroupRepository;
import org.example.chatft.repository.UserRepository;
import org.example.chatft.utils.MessageDeduplicator;

import java.net.InetAddress;
import java.util.Arrays;
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
    private Consumer<User> onIncomingVideoCall;
    private Consumer<VideoFrameData> onVideoFrameReceived;
    
    // Deduplication for JOIN/LEAVE notifications
    private final java.util.concurrent.ConcurrentHashMap<String, Long> recentNotifications = 
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService cleanupScheduler = 
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

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

        // Check for duplicates (skip for frequent/realtime messages)
        if (!type.equals("VIDEO_FRAME") && !type.equals("ONLINE") && !type.equals("OFFLINE") 
                && !type.equals("JOIN_GROUP") && !type.equals("LEAVE_GROUP")) {
            if (deduplicator.isDuplicate(type, msg)) {
                System.out.println("[UDP-SKIP] Duplicate: " + msg.substring(0, Math.min(50, msg.length())));
                return;
            }
        }
        
        // Debug log for video frames
        if (type.equals("VIDEO_FRAME")) {
            System.out.println("[UDP-RECV-VIDEO] Received VIDEO_FRAME message, parts: " + parts.length);
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
            case "JOIN_GROUP":
                handleJoinGroup(parts);
                break;
            case "LEAVE_GROUP":
                handleLeaveGroup(parts);
                break;
            case "VIDEO_CALL_REQUEST":
                handleVideoCallRequest(parts, addr);
                break;
            case "VIDEO_CALL_ACCEPT":
                handleVideoCallAccept(parts, addr);
                break;
            case "VIDEO_CALL_REJECT":
                handleVideoCallReject(parts, addr);
                break;
            case "VIDEO_FRAME":
                handleVideoFrame(parts, addr);
                break;
        }
    }
    
    public void setOnIncomingVideoCall(Consumer<User> callback) {
        this.onIncomingVideoCall = callback;
    }
    
    public void setOnVideoFrameReceived(Consumer<VideoFrameData> callback) {
        this.onVideoFrameReceived = callback;
    }

    private void handleOnline(String[] parts, InetAddress addr) {
        if (parts.length < 3) return;

        String nick = parts[1].trim();
        int port = Integer.parseInt(parts[2].trim());

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
        if (parts.length < 2) {
            System.out.println("[UDP-OFFLINE] Invalid OFFLINE message format");
            return;
        }

        String offlineNick = parts[1].trim();
        
        if (offlineNick.equals(nickname)) {
            return; // Ignore own OFFLINE message
        }
        
        System.out.println("[INFO] User left: " + offlineNick);
        
        User removed = userRepository.removeUser(offlineNick);

        if (removed != null) {
            System.out.println("[INFO] ✅ Removed from list: " + removed);
            onUserOffline.accept(removed);
        } else {
            System.out.println("[INFO] ⚠️ User not in list: " + offlineNick);
        }
    }

    private void handleGroupPublic(String[] parts) {
        if (parts.length >= 2) {
            String groupName = parts[1].trim(); // Trim whitespace
            
            // Check if group already exists before adding
            Group existingGroup = groupRepository.getGroup(groupName);
            if (existingGroup != null) {
                System.out.println("[GROUP] Public group already known, skipping: " + groupName);
                return;
            }
            
            Group group = groupRepository.addDiscoveredGroup(groupName, true);
            onGroupDiscovered.accept(group);
            System.out.println("[GROUP] Public group discovered: " + groupName);
        }
    }

    private void handleGroupPrivate(String[] parts) {
        if (parts.length >= 2) {
            String groupName = parts[1].trim(); // Trim whitespace
            String password = (parts.length >= 3) ? parts[2].trim() : null;

            System.out.println("[GROUP] Private group discovered: " + groupName +
                    (password != null ? " (with password)" : " (no password)"));

            // Check if group already exists before adding
            Group existingGroup = groupRepository.getGroup(groupName);
            if (existingGroup != null) {
                System.out.println("[GROUP] Private group already known, skipping: " + groupName);
                return;
            }

            // Add new group with password
            Group group = (password != null)
                    ? groupRepository.addDiscoveredGroup(groupName, false, password)
                    : groupRepository.addDiscoveredGroup(groupName, false);

            onGroupDiscovered.accept(group);
            System.out.println("[GROUP] ✅ Group handled: " + groupName);
        } else {
            System.out.println("[GROUP] ⚠️ Invalid GROUP_PRIVATE message: " + Arrays.toString(parts));
        }
    }

    private void handleGroupMessage(String[] parts) {
        if (parts.length >= 4) {
            String groupName = parts[1].trim();
            String sender = parts[2].trim();
            String content = parts[3]; // Don't trim message content

            if (!sender.equals(nickname) && groupRepository.isJoined(groupName)) {
                GroupMessage groupMsg = new GroupMessage(groupName, sender, content);
                onGroupMessage.accept(groupMsg);
                System.out.println("[GROUP] Message in " + groupName + " from " + sender + ": " + content);
            }
        }
    }

    private void handleGroupFile(String[] parts, InetAddress addr) {
        if (parts.length >= 6) {
            String groupName = parts[1].trim();
            String sender = parts[2].trim();
            String fileName = parts[3].trim();
            long fileSize = Long.parseLong(parts[4].trim());
            int senderTcpPort = Integer.parseInt(parts[5].trim());

            if (!sender.equals(nickname) && groupRepository.isJoined(groupName)) {
                System.out.println("[GROUP] File in " + groupName + " from " + sender + ": " + fileName);

                FileDownloadRequest request = new FileDownloadRequest(
                        addr.getHostAddress(), senderTcpPort, groupName, sender, fileName, fileSize
                );
                onFileDownloadRequest.accept(request);
            }
        }
    }

    private void handleJoinGroup(String[] parts) {
        if (parts.length >= 3) {
            String groupName = parts[1].trim();
            String joinerNickname = parts[2].trim();

            // Only show notification if we're in the group and it's not us joining
            if (!joinerNickname.equals(nickname) && groupRepository.isJoined(groupName)) {
                String notificationKey = "JOIN:" + groupName + ":" + joinerNickname;
                long now = System.currentTimeMillis();
                
                // Atomic operation: add if absent, returns previous value
                Long lastTime = recentNotifications.putIfAbsent(notificationKey, now);
                
                if (lastTime != null && (now - lastTime) < 3000) {
                    // Duplicate within 3 seconds, skip
                    return;
                }
                
                if (lastTime != null) {
                    // Expired, update
                    recentNotifications.put(notificationKey, now);
                }
                
                // Schedule cleanup
                cleanupScheduler.schedule(
                    () -> recentNotifications.remove(notificationKey),
                    5, java.util.concurrent.TimeUnit.SECONDS
                );
                
                String systemMessage = joinerNickname + " joined the group";
                GroupMessage groupMsg = new GroupMessage(groupName, "__SYSTEM__", systemMessage);
                onGroupMessage.accept(groupMsg);
                System.out.println("[GROUP] " + joinerNickname + " joined " + groupName);
            }
        }
    }

    private void handleLeaveGroup(String[] parts) {
        if (parts.length >= 3) {
            String groupName = parts[1].trim();
            String leaverNickname = parts[2].trim();

            // Only show notification if we're in the group and it's not us leaving
            if (!leaverNickname.equals(nickname) && groupRepository.isJoined(groupName)) {
                String notificationKey = "LEAVE:" + groupName + ":" + leaverNickname;
                long now = System.currentTimeMillis();
                
                // Atomic operation: add if absent, returns previous value
                Long lastTime = recentNotifications.putIfAbsent(notificationKey, now);
                
                if (lastTime != null && (now - lastTime) < 3000) {
                    // Duplicate within 3 seconds, skip
                    return;
                }
                
                if (lastTime != null) {
                    // Expired, update
                    recentNotifications.put(notificationKey, now);
                }
                
                // Schedule cleanup
                cleanupScheduler.schedule(
                    () -> recentNotifications.remove(notificationKey),
                    5, java.util.concurrent.TimeUnit.SECONDS
                );
                
                String systemMessage = leaverNickname + " left the group";
                GroupMessage groupMsg = new GroupMessage(groupName, "__SYSTEM__", systemMessage);
                onGroupMessage.accept(groupMsg);
                System.out.println("[GROUP] " + leaverNickname + " left " + groupName);
            }
        }
    }
    
    private void handleVideoCallRequest(String[] parts, InetAddress addr) {
        // VIDEO_CALL_REQUEST;fromNickname;fromPort
        System.out.println("[VIDEO-SIGNAL] Received VIDEO_CALL_REQUEST: " + String.join(";", parts));
        
        if (parts.length < 3) {
            System.err.println("[VIDEO-SIGNAL-ERR] Invalid VIDEO_CALL_REQUEST format");
            return;
        }
        
        String fromNickname = parts[1].trim();
        int fromPort = Integer.parseInt(parts[2].trim());
        
        System.out.println("[VIDEO-SIGNAL] From: " + fromNickname + ", My nickname: " + nickname);
        
        if (!fromNickname.equals(nickname)) {
            User caller = userRepository.getUser(fromNickname);
            if (caller == null) {
                caller = new User(fromNickname, addr.getHostAddress(), fromPort);
                userRepository.addUser(caller);
                System.out.println("[VIDEO-SIGNAL] Created new user: " + caller);
            }
            
            System.out.println("[VIDEO-SIGNAL] Triggering incoming call callback for: " + fromNickname);
            
            if (onIncomingVideoCall != null) {
                onIncomingVideoCall.accept(caller);
                System.out.println("[VIDEO-SIGNAL] Callback triggered successfully");
            } else {
                System.err.println("[VIDEO-SIGNAL-ERR] onIncomingVideoCall callback is NULL!");
            }
        } else {
            System.out.println("[VIDEO-SIGNAL] Ignoring own call request");
        }
    }
    
    private void handleVideoCallAccept(String[] parts, InetAddress addr) {
        // VIDEO_CALL_ACCEPT;fromNickname
        if (parts.length < 2) return;
        
        String fromNickname = parts[1].trim();
        System.out.println("[VIDEO] Call accepted by: " + fromNickname);
        // Handle in VideoCallController
    }
    
    private void handleVideoCallReject(String[] parts, InetAddress addr) {
        // VIDEO_CALL_REJECT;fromNickname
        if (parts.length < 2) return;
        
        String fromNickname = parts[1].trim();
        System.out.println("[VIDEO] Call rejected by: " + fromNickname);
        // Handle in VideoCallController
    }
    
    private void handleVideoFrame(String[] parts, InetAddress addr) {
        // VIDEO_FRAME;fromNickname;toNickname;base64FrameData
        if (parts.length < 4) {
            System.err.println("[UDP-VIDEO-ERR] Invalid frame format, parts: " + parts.length);
            return;
        }
        
        String fromNickname = parts[1].trim();
        String toNickname = parts[2].trim();
        String base64Frame = parts[3];
        
        // Ignore own frames FIRST (most efficient check)
        if (fromNickname.equals(nickname)) {
            // System.out.println("[UDP-VIDEO-DEBUG] Own frame, ignoring");
            return;
        }

        // Only process if this frame is for me
        if (!toNickname.equals(nickname)) {
            // System.out.println("[UDP-VIDEO-DEBUG] Frame not for me (to: " + toNickname + "), ignoring");
            return;
        }
        
        System.out.println("[UDP-VIDEO-DEBUG] Processing frame from " + fromNickname + " to " + toNickname);
        
        try {
            byte[] frameData = java.util.Base64.getDecoder().decode(base64Frame);
            
            System.out.println("[UDP-VIDEO-DEBUG] Decoded frame: " + frameData.length + " bytes");
            
            if (onVideoFrameReceived != null) {
                VideoFrameData videoFrame = new VideoFrameData(fromNickname, frameData);
                onVideoFrameReceived.accept(videoFrame);
                System.out.println("[UDP-VIDEO] Frame forwarded to callback");
            } else {
                System.err.println("[UDP-VIDEO-ERR] No callback registered!");
            }
        } catch (Exception e) {
            System.err.println("[UDP-VIDEO-ERR] Failed to decode frame: " + e.getMessage());
            e.printStackTrace();
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
    
    // Inner class for video frame data
    public static class VideoFrameData {
        public final String fromNickname;
        public final byte[] frameData;
        
        public VideoFrameData(String fromNickname, byte[] frameData) {
            this.fromNickname = fromNickname;
            this.frameData = frameData;
        }
    }
}