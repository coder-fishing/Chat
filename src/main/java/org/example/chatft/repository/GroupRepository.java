package org.example.chatft.repository;

import org.example.chatft.model.Group;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupRepository {
    // Groups user has joined
    private final Set<String> joinedPublicGroups = ConcurrentHashMap.newKeySet();
    private final Map<String, String> joinedPrivateGroups = new ConcurrentHashMap<>(); // groupName -> password

    // All discovered groups
    private final Map<String, Group> discoveredGroups = new ConcurrentHashMap<>();

    // Group file tracking: groupName -> (fileName -> filePath)
    private final Map<String, Map<String, String>> groupFiles = new ConcurrentHashMap<>();

    /**
     * Add discovered group
     */
    public Group addDiscoveredGroup(String groupName, boolean isPublic, String password) {
        // Check if group already exists
        Group existingGroup = discoveredGroups.get(groupName);
        if (existingGroup != null) {
            System.out.println("[REPO] ⚠️ Group already exists, skipping: " + groupName);
            return existingGroup;
        }
        
        Group group = new Group(groupName, isPublic, password);
        discoveredGroups.put(groupName, group);
        System.out.println("[REPO] ✅ Group saved: " + groupName +
                ", password: " + (password != null ? "[SAVED]" : "[NULL]"));
        return group;
    }

    /**
     * Add discovered group (existing method - keep as is)
     */
    public Group addDiscoveredGroup(String groupName, boolean isPublic) {
        return addDiscoveredGroup(groupName, isPublic, null);
    }

    /**
     * Join public group
     */
    public void joinPublicGroup(String groupName) {
        joinedPublicGroups.add(groupName);
        Group group = discoveredGroups.get(groupName);
        if (group != null) {
            group.setJoined(true);
        }
    }

    /**
     * Join private group with password
     */
    public void joinPrivateGroup(String groupName, String password) {
        joinedPrivateGroups.put(groupName, password);
        Group group = discoveredGroups.get(groupName);
        if (group != null) {
            group.setJoined(true);
        }
    }

    /**
     * Leave group
     */
    public void leaveGroup(String groupName) {
        joinedPublicGroups.remove(groupName);
        joinedPrivateGroups.remove(groupName);
        Group group = discoveredGroups.get(groupName);
        if (group != null) {
            group.setJoined(false);
        }
    }

    /**
     * Check if user is in group
     */
    public boolean isJoined(String groupName) {
        return joinedPublicGroups.contains(groupName)
                || joinedPrivateGroups.containsKey(groupName);
    }

    /**
     * Get group by name
     */
    public Group getGroup(String groupName) {
        return discoveredGroups.get(groupName);
    }

    /**
     * Get all joined groups
     */
    public Set<String> getJoinedGroups() {
        Set<String> allGroups = new HashSet<>(joinedPublicGroups);
        allGroups.addAll(joinedPrivateGroups.keySet());
        return allGroups;
    }

    /**
     * Get all joined public groups
     */
    public Set<String> getJoinedPublicGroups() {
        return new HashSet<>(joinedPublicGroups);
    }

    /**
     * Get all joined private groups
     */
    public Map<String, String> getJoinedPrivateGroups() {
        return new HashMap<>(joinedPrivateGroups);
    }

    /**
     * Get all discovered groups
     */
    public Collection<Group> getAllDiscoveredGroups() {
        return discoveredGroups.values();
    }

    /**
     * Add file to group
     */
    public void addGroupFile(String groupName, String fileName, String filePath) {
        groupFiles.computeIfAbsent(groupName, k -> new ConcurrentHashMap<>())
                .put(fileName, filePath);
    }

    /**
     * Get file path from group
     */
    public String getGroupFilePath(String groupName, String fileName) {
        Map<String, String> files = groupFiles.get(groupName);
        return files != null ? files.get(fileName) : null;
    }

    /**
     * Clear all data
     */
    public void clear() {
        joinedPublicGroups.clear();
        joinedPrivateGroups.clear();
        discoveredGroups.clear();
        groupFiles.clear();
    }
}