package org.example.chatft.model;

public class Group {
    private String name;
    private boolean isPublic;
    private String password; // null nếu là public group
    private boolean joined;
    private int unreadCount;

    public Group(String name, boolean isPublic, String password) {
        this.name = name;
        this.isPublic = isPublic;
        this.password = password;
        this.joined = false;
    }

    public String getName() {
        return name;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public String getPassword() {
        return password;
    }

    public boolean isJoined() {
        return joined;
    }

    public void setJoined(boolean joined) {
        this.joined = joined;
    }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int count) { this.unreadCount = count; }
    public void incrementUnread() { this.unreadCount++; }
    public void resetUnread() { this.unreadCount = 0; }

    @Override
    public String toString() {
        String type = isPublic ? "🌐" : "🔒";
        String status = joined ? " ✓" : "";
        return type + " " + name + status;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Group group = (Group) obj;
        return name.equals(group.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}