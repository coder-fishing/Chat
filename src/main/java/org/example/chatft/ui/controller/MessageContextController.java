package org.example.chatft.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;

public class MessageContextController {

    @FXML
    private VBox contextMenuContainer;

    @FXML
    private Button translateBtn;

    @FXML
    private Button summaryBtn;

    @FXML
    private Button translateAndSummaryBtn;

    private String selectedText;
    private Runnable onCloseCallback;

    /**
     * Khởi tạo controller
     */
    @FXML
    public void initialize() {
        // Có thể thêm các animation hoặc effect khi menu xuất hiện
        setupAnimations();
    }

    /**
     * Set text đã được bôi đen
     */
    public void setSelectedText(String text) {
        this.selectedText = text;
    }

    /**
     * Set callback khi đóng menu
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    /**
     * Xử lý khi nhấn nút Dịch
     */
    @FXML
    private void onTranslate(ActionEvent event) {
        System.out.println("Đang dịch: " + selectedText);

        // TODO: Gọi service dịch văn bản
        // translateService.translate(selectedText);

        closeMenu();
    }

    /**
     * Xử lý khi nhấn nút Tóm tắt
     */
    @FXML
    private void onSummary(ActionEvent event) {
        System.out.println("Đang tóm tắt: " + selectedText);

        // TODO: Gọi service tóm tắt văn bản
        // summaryService.summarize(selectedText);

        closeMenu();
    }

    /**
     * Xử lý khi nhấn nút Dịch và Tóm tắt
     */
    @FXML
    private void onTranslateAndSummary(ActionEvent event) {
        System.out.println("Đang dịch và tóm tắt: " + selectedText);

        // TODO: Gọi service dịch và tóm tắt văn bản
        // translateService.translate(selectedText)
        //     .thenCompose(translated -> summaryService.summarize(translated));

        closeMenu();
    }

    /**
     * Đóng menu
     */
    private void closeMenu() {
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }

    /**
     * Setup animations cho menu
     */
    private void setupAnimations() {
        // Có thể thêm fade-in animation
        contextMenuContainer.setOpacity(0);
        javafx.animation.FadeTransition fade =
                new javafx.animation.FadeTransition(
                        javafx.util.Duration.millis(200),
                        contextMenuContainer
                );
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Enable/disable dark mode
     */
    public void setDarkMode(boolean dark) {
        if (dark) {
            contextMenuContainer.getStyleClass().add("dark");
        } else {
            contextMenuContainer.getStyleClass().remove("dark");
        }
    }
}