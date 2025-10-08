package org.example.chatft;

import javafx.scene.layout.VBox;

public class User {
    private String nickname;
    private String ip;
    private int tcpPort;
    private transient VBox messagesBox;
    private int unreadCount = 0;

    public User(String nickname, String ip, int tcpPort) {
        this.nickname = nickname;
        this.ip = ip;
        this.tcpPort = tcpPort;
    }

    public String getNickname() { return nickname; }
    public String getIp() { return ip; }
    public int getTcpPort() { return tcpPort; }
    public VBox getMessagesBox() { return messagesBox; }
    public void setMessagesBox(VBox messagesBox) { this.messagesBox = messagesBox; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int count) { this.unreadCount = count; }
    public void incrementUnread() { this.unreadCount++; }
    public void resetUnread() { this.unreadCount = 0; }


    @Override
    public String toString() {
        return nickname + " " /*(" + ip + ":" + tcpPort + ")*/;
    }
}
