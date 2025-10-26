package org.example.chatft.repository;

import org.example.chatft.model.User;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepository {
    private final Map<String, User> knownUsers = new ConcurrentHashMap<>();

    /**
     * Add or update user
     * @return true if new user, false if already exists
     */
    public boolean addUser(User user) {
        String key = user.getNickname();
        User existing = knownUsers.putIfAbsent(key, user);
        return existing == null;
    }

    /**
     * Remove user by nickname
     * @return removed user, or null if not found
     */
    public User removeUser(String nickname) {
        return knownUsers.remove(nickname);
    }

    /**
     * Get user by nickname
     */
    public User getUser(String nickname) {
        return knownUsers.get(nickname);
    }

    /**
     * Check if user exists
     */
    public boolean hasUser(String nickname) {
        return knownUsers.containsKey(nickname);
    }

    /**
     * Get all known users
     */
    public Collection<User> getAllUsers() {
        return knownUsers.values();
    }

    /**
     * Get count of known users
     */
    public int getUserCount() {
        return knownUsers.size();
    }

    /**
     * Clear all users
     */
    public void clear() {
        knownUsers.clear();
    }
}