package org.example.chatft.model;

public class GroupMessage {
    private String groupName;
    private String sender;
    private String content;
    private long timestamp;

    public GroupMessage(String groupName, String sender, String content) {
        this.groupName = groupName;
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getGroupName() {
        return groupName;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return sender + ": " + content;
    }
}