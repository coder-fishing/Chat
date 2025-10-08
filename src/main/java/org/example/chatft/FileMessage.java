package org.example.chatft;

public class FileMessage {
    private String sender;
    private String fileName;
    private long fileSize;
    private String filePath;

    // Group-related fields
    private boolean isGroupMessage = false;
    private String groupName;

    public FileMessage(String sender, String fileName, long fileSize, String filePath) {
        this.sender = sender;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.filePath = filePath;
    }

    // Getters
    public String getSender() { return sender; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getFilePath() { return filePath; }

    public boolean isGroupMessage() { return isGroupMessage; }
    public String getGroupName() { return groupName; }

    // Setters
    public void setGroupMessage(boolean isGroupMessage) {
        this.isGroupMessage = isGroupMessage;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getFileSizeFormatted() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}