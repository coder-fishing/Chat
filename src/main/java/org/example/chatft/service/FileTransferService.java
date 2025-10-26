package org.example.chatft.service;

import org.example.chatft.config.NetworkConfig;
import org.example.chatft.model.FileMessage;
import org.example.chatft.model.User;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class FileTransferService {
    private final String nickname;
    private final ExecutorService executor;
    private final Consumer<FileMessage> onFileReceived;

    public FileTransferService(String nickname,
                               ExecutorService executor,
                               Consumer<FileMessage> onFileReceived) {
        this.nickname = nickname;
        this.executor = executor;
        this.onFileReceived = onFileReceived;

        // Create download directory
        new File(NetworkConfig.DOWNLOAD_DIR).mkdirs();
    }

    /**
     * Send file to specific user
     */
    public void sendFile(User user, String filePath) {
        executor.submit(() -> {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("[FILE-ERR] File not found: " + filePath);
                return;
            }

            try (Socket socket = new Socket(user.getIp(), user.getTcpPort());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {

                String header = "FILE:" + nickname + ":" + file.getName() + ":" + file.length();
                out.writeUTF(header);
                out.flush();

                byte[] buffer = new byte[NetworkConfig.FILE_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();

                System.out.println("[FILE] Sent: " + file.getName() + " to " + user);

                // Small delay to ensure file is sent
                Thread.sleep(NetworkConfig.FILE_SEND_DELAY_MS);

            } catch (IOException e) {
                System.err.println("[FILE-ERR] Failed to send to " + user + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Receive file from stream
     */
    public String receiveFile(DataInputStream in, String fileName, long fileSize) {
        try {
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File file = new File(NetworkConfig.DOWNLOAD_DIR, System.currentTimeMillis() + "_" + safeName);

            System.out.println("[FILE] Receiving: " + fileName + " (" + fileSize + " bytes)");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[NetworkConfig.FILE_BUFFER_SIZE];
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
            System.err.println("[FILE-ERR] " + e.getMessage());
            return null;
        }
    }

    /**
     * Download file from group member
     */
    public void downloadGroupFile(String senderIp, int senderTcpPort, String groupName,
                                  String sender, String fileName, long fileSize) {
        executor.submit(() -> {
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
                } else if (response.startsWith("ERROR:")) {
                    System.err.println("[GROUP-FILE-ERR] " + response);
                }

            } catch (IOException e) {
                System.err.println("[GROUP-FILE-ERR] " + e.getMessage());
            }
        });
    }
}