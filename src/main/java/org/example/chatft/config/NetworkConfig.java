package org.example.chatft.config;

public class NetworkConfig {
    // Network ports
    public static final int UDP_PORT = 8888;
    public static final String MULTICAST_GROUP = "230.0.0.1";

    // File transfer
    public static final String DOWNLOAD_DIR = "downloads";
    public static final int FILE_BUFFER_SIZE = 4096;

    // Multicast settings
    public static final int MULTICAST_TTL = 4;

    // Cleanup intervals
    public static final long MESSAGE_CLEANUP_INTERVAL_MS = 10000; // 10 seconds
    public static final long MESSAGE_DEDUP_WINDOW_MS = 100; // 100ms

    // Network timeouts
    public static final int TCP_SEND_DELAY_MS = 50;
    public static final int FILE_SEND_DELAY_MS = 100;

    private NetworkConfig() {
        // Prevent instantiation
    }
}