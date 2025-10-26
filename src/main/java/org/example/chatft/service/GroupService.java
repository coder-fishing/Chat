package org.example.chatft.service;

import org.example.chatft.model.Group;
import org.example.chatft.repository.GroupRepository;

import java.io.File;
import java.util.Set;

public class GroupService {
    private final String nickname;
    private final GroupRepository groupRepository;
    private final UdpService udpService;
    private final FileTransferService fileTransferService;
    private final int tcpPort;

    public GroupService(String nickname,
                        GroupRepository groupRepository,
                        UdpService udpService,
                        FileTransferService fileTransferService,
                        int tcpPort) {
        this.nickname = nickname;
        this.groupRepository = groupRepository;
        this.udpService = udpService;
        this.fileTransferService = fileTransferService;
        this.tcpPort = tcpPort;
    }

    /**
     * Create public group
     */
    public void createPublicGroup(String groupName) {
        groupRepository.joinPublicGroup(groupName);

        Group group = groupRepository.addDiscoveredGroup(groupName, true);
        group.setJoined(true);

        udpService.broadcastPublicGroup(groupName);
        System.out.println("[GROUP] Created public group: " + groupName);
    }

    /**
     * Create private group with password
     */
    public void createPrivateGroup(String groupName, String password) {
        groupRepository.joinPrivateGroup(groupName, password);

        Group group = groupRepository.addDiscoveredGroup(groupName, false);
        group.setJoined(true);

        udpService.broadcastPrivateGroup(groupName);
        System.out.println("[GROUP] Created private group: " + groupName);
    }

    /**
     * Join existing group
     */
    public boolean joinGroup(String groupName, String password) {
        Group group = groupRepository.getGroup(groupName);
        if (group == null) {
            System.out.println("[GROUP] Group not found: " + groupName);
            return false;
        }

        if (group.isPublic()) {
            groupRepository.joinPublicGroup(groupName);
            System.out.println("[GROUP] Joined public group: " + groupName);
            return true;
        } else {
            if (password != null && !password.trim().isEmpty()) {
                groupRepository.joinPrivateGroup(groupName, password);
                System.out.println("[GROUP] Joined private group: " + groupName);
                return true;
            } else {
                System.out.println("[GROUP] Password required for private group: " + groupName);
                return false;
            }
        }
    }

    /**
     * Leave group
     */
    public void leaveGroup(String groupName) {
        groupRepository.leaveGroup(groupName);
        System.out.println("[GROUP] Left group: " + groupName);
    }

    /**
     * Send message to group
     */
    public void sendGroupMessage(String groupName, String message) {
        if (!groupRepository.isJoined(groupName)) {
            System.out.println("[GROUP] Not joined to group: " + groupName);
            return;
        }

        udpService.sendGroupMessage(groupName, nickname, message);
        System.out.println("[GROUP] Sent message to " + groupName + ": " + message);
    }

    /**
     * Send file to group
     */
    public void sendGroupFile(String groupName, String filePath) {
        if (!groupRepository.isJoined(groupName)) {
            System.out.println("[GROUP] Not joined to group: " + groupName);
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("[GROUP] File not found: " + filePath);
            return;
        }

        // Store file path for this group
        groupRepository.addGroupFile(groupName, file.getName(), filePath);

        // Announce file to all members in group
        udpService.announceGroupFile(groupName, nickname, file.getName(), file.length(), tcpPort);
        System.out.println("[GROUP] Announced file to group " + groupName + ": " + file.getName());
    }

    /**
     * Get all joined groups
     */
    public Set<String> getJoinedGroups() {
        return groupRepository.getJoinedGroups();
    }

    /**
     * Check if user is in group
     */
    public boolean isInGroup(String groupName) {
        return groupRepository.isJoined(groupName);
    }

    /**
     * Broadcast all joined groups (used when new user detected)
     */
    public void broadcastAllGroups() {
        for (String groupName : groupRepository.getJoinedPublicGroups()) {
            udpService.broadcastPublicGroup(groupName);
        }

        for (String groupName : groupRepository.getJoinedPrivateGroups().keySet()) {
            udpService.broadcastPrivateGroup(groupName);
        }
    }
}