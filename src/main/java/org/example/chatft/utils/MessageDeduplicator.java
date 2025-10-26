package org.example.chatft.utils;

import org.example.chatft.config.NetworkConfig;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MessageDeduplicator {
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public MessageDeduplicator() {
        startCleanupTask();
    }

    /**
     * Check if message is duplicate
     * @param messageType Type of message (GMSG, GFILE, etc.)
     * @param messageContent Full message content
     * @return true if duplicate, false if new
     */
    public boolean isDuplicate(String messageType, String messageContent) {
        // Only check duplicates for group messages
        if (!messageType.equals("GMSG") && !messageType.equals("GFILE")) {
            return false;
        }

        // Create unique ID: content + timestamp (rounded to 100ms)
        long timestamp = System.currentTimeMillis() / NetworkConfig.MESSAGE_DEDUP_WINDOW_MS;
        String messageId = messageContent + "@" + timestamp;

        // Check and add atomically
        return !processedMessages.add(messageId);
    }

    private void startCleanupTask() {
        cleanupScheduler.scheduleAtFixedRate(
                () -> {
                    processedMessages.clear();
                    System.out.println("[DEDUP] Cleared processed messages");
                },
                NetworkConfig.MESSAGE_CLEANUP_INTERVAL_MS,
                NetworkConfig.MESSAGE_CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}