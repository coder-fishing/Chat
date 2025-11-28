package org.example.chatft.ui.util;

import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;

public class DebounceUtil {
    private Timer timer;
    private final long delay;

    /**
     * @param delayMs Thời gian delay (milliseconds), ví dụ: 300ms
     */
    public DebounceUtil(long delayMs) {
        this.delay = delayMs;
    }

    /**
     * Debounce một Runnable task
     * Chỉ chạy task sau khi user NGỪNG gõ trong khoảng delay
     */
    public void debounce(Runnable task) {
        // Cancel timer cũ nếu có
        if (timer != null) {
            timer.cancel();
        }

        // Tạo timer mới
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Chạy trên JavaFX thread
                Platform.runLater(task);
            }
        }, delay);
    }

    /**
     * Cancel timer hiện tại
     */
    public void cancel() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
