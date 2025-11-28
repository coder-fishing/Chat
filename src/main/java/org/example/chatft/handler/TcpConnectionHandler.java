package org.example.chatft.handler;

import org.example.chatft.model.FileMessage;
import org.example.chatft.repository.GroupRepository;
import org.example.chatft.service.FileTransferService;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class TcpConnectionHandler {
    private final String nickname;
    private final GroupRepository groupRepository;
    private final FileTransferService fileTransferService;
    private final Consumer<String> onMessage;
    private final Consumer<FileMessage> onFileReceived;
    private Consumer<String[]> onVideoCallRequest;
    private Consumer<String[]> onSdpOffer;
    private Consumer<String[]> onSdpAnswer;
    private Consumer<String[]> onIceCandidate;
    private Consumer<String> onCallEnd;
    private Consumer<String> onUserOfflineTcp;

    public TcpConnectionHandler(String nickname,
                                GroupRepository groupRepository,
                                FileTransferService fileTransferService,
                                Consumer<String> onMessage,
                                Consumer<FileMessage> onFileReceived) {
        this.nickname = nickname;
        this.groupRepository = groupRepository;
        this.fileTransferService = fileTransferService;
        this.onMessage = onMessage;
        this.onFileReceived = onFileReceived;
    }
    
    public void setOnVideoCallRequest(Consumer<String[]> callback) {
        this.onVideoCallRequest = callback;
    }
    
    public void setOnSdpOffer(Consumer<String[]> callback) {
        this.onSdpOffer = callback;
    }
    
    public void setOnSdpAnswer(Consumer<String[]> callback) {
        this.onSdpAnswer = callback;
    }
    
    public void setOnIceCandidate(Consumer<String[]> callback) {
        this.onIceCandidate = callback;
    }
    
    public void setOnCallEnd(Consumer<String> callback) {
        this.onCallEnd = callback;
    }
    
    public void setOnUserOfflineTcp(Consumer<String> callback) {
        this.onUserOfflineTcp = callback;
    }

    public void handleConnection(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            String header = in.readUTF();
            System.out.println("[TCP] Received header: " + header);

            if (header.startsWith("MSG:")) {
                handleMessage(header);

            } else if (header.startsWith("FILE:")) {
                handleFile(header, in);

            } else if (header.startsWith("REQUEST_GROUP_FILE:")) {
                handleGroupFileRequest(header, out);
                
            } else if (header.startsWith("TCP_OFFLINE:")) {
                handleTcpOffline(header);
                
            } else if (header.startsWith("VIDEO_CALL_REQUEST")) {
                handleVideoCallRequest(header);
                
            } else if (header.startsWith("VIDEO_SDP_OFFER:")) {
                handleSdpOffer(header);
                
            } else if (header.startsWith("VIDEO_SDP_ANSWER:")) {
                handleSdpAnswer(header);
                
            } else if (header.startsWith("VIDEO_ICE_CANDIDATE:")) {
                handleIceCandidate(header);
                
            } else if (header.startsWith("VIDEO_CALL_END:")) {
                handleCallEnd(header);
            }

        } catch (IOException e) {
            System.out.println("[TCP-ERR] " + e.getMessage());
        }
    }

    private void handleMessage(String header) {
        String msg = header.substring(4); // Remove "MSG:" prefix
        onMessage.accept(msg);
        System.out.println("[TCP] Message received: " + msg);
    }
    
    private void handleTcpOffline(String header) {
        // TCP_OFFLINE:nicknameOfUserWhoLeft
        String[] parts = header.split(":");
        if (parts.length >= 2) {
            String offlineNick = parts[1].trim();
            System.out.println("[TCP-OFFLINE] User offline: " + offlineNick);
            
            if (onUserOfflineTcp != null) {
                onUserOfflineTcp.accept(offlineNick);
            }
        }
    }
    
    private void handleVideoCallRequest(String header) {
        // VIDEO_CALL_REQUEST;fromNickname;fromPort
        System.out.println("[TCP-VIDEO] Received VIDEO_CALL_REQUEST: " + header);
        
        String[] parts = header.split(";");
        if (parts.length >= 3 && onVideoCallRequest != null) {
            onVideoCallRequest.accept(parts);
            System.out.println("[TCP-VIDEO] Video call request forwarded to handler");
        } else {
            System.err.println("[TCP-VIDEO-ERR] Invalid format or no callback: " + header);
        }
    }
    
    private void handleSdpOffer(String header) {
        // VIDEO_SDP_OFFER:fromNickname:sdp_content
        System.out.println("[TCP-VIDEO] Received SDP Offer");
        
        String[] parts = header.split(":", 3);
        if (parts.length >= 3 && onSdpOffer != null) {
            onSdpOffer.accept(parts);
            System.out.println("[TCP-VIDEO] SDP Offer forwarded");
        } else {
            System.err.println("[TCP-VIDEO-ERR] Invalid SDP Offer format");
        }
    }
    
    private void handleSdpAnswer(String header) {
        // VIDEO_SDP_ANSWER:fromNickname:sdp_content
        System.out.println("[TCP-VIDEO] Received SDP Answer");
        
        String[] parts = header.split(":", 3);
        if (parts.length >= 3 && onSdpAnswer != null) {
            onSdpAnswer.accept(parts);
            System.out.println("[TCP-VIDEO] SDP Answer forwarded");
        } else {
            System.err.println("[TCP-VIDEO-ERR] Invalid SDP Answer format");
        }
    }
    
    private void handleIceCandidate(String header) {
        // VIDEO_ICE_CANDIDATE:fromNickname:candidate_json
        System.out.println("[TCP-VIDEO] Received ICE Candidate");
        
        String[] parts = header.split(":", 3);
        if (parts.length >= 3 && onIceCandidate != null) {
            onIceCandidate.accept(parts);
            System.out.println("[TCP-VIDEO] ICE Candidate forwarded");
        } else {
            System.err.println("[TCP-VIDEO-ERR] Invalid ICE Candidate format");
        }
    }
    
    private void handleCallEnd(String header) {
        // VIDEO_CALL_END:fromNickname
        System.out.println("[TCP-VIDEO] Received Call End");
        
        String[] parts = header.split(":");
        if (parts.length >= 2 && onCallEnd != null) {
            onCallEnd.accept(parts[1]);
            System.out.println("[TCP-VIDEO] Call End forwarded");
        } else {
            System.err.println("[TCP-VIDEO-ERR] Invalid Call End format");
        }
    }

    private void handleFile(String header, DataInputStream in) throws IOException {
        String[] parts = header.split(":");
        if (parts.length >= 4) {
            String sender = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);

            String savedPath = fileTransferService.receiveFile(in, fileName, fileSize);
            if (savedPath != null) {
                FileMessage fileMsg = new FileMessage(sender, fileName, fileSize, savedPath);
                onFileReceived.accept(fileMsg);
                System.out.println("[FILE] Received: " + fileName);
            }
        }
    }

    private void handleGroupFileRequest(String header, DataOutputStream out) throws IOException {
        String[] parts = header.split(":");
        if (parts.length >= 3) {
            String groupName = parts[1];
            String fileName = parts[2];

            System.out.println("[GROUP] File request: " + groupName + "/" + fileName);

            // Check if in group
            if (!groupRepository.isJoined(groupName)) {
                out.writeUTF("ERROR:Not in group");
                out.flush();
                System.out.println("[GROUP] Error: Not in group");
                return;
            }

            // Get file path
            String filePath = groupRepository.getGroupFilePath(groupName, fileName);
            if (filePath == null) {
                out.writeUTF("ERROR:File not found");
                out.flush();
                System.out.println("[GROUP] Error: File not found");
                return;
            }

            // Check file exists
            File file = new File(filePath);
            if (!file.exists()) {
                out.writeUTF("ERROR:File not found");
                out.flush();
                System.out.println("[GROUP] Error: File does not exist");
                return;
            }

            // Send file
            String responseHeader = "GROUP_FILE:" + groupName + ":" + nickname + ":" + fileName + ":" + file.length();
            out.writeUTF(responseHeader);
            out.flush();

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }

            System.out.println("[GROUP] Sent file: " + fileName);

            // Small delay to ensure file is sent
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}