package org.example.chatft.ui.components;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;

public class ResultPopup {

    private Popup popup;
    private VBox contentBox;
    private Label resultLabel;
    private ProgressIndicator loadingIndicator;

    public ResultPopup() {
        popup = new Popup();
        popup.setAutoHide(true); // ✅ Click outside để đóng

        contentBox = new VBox(10);
        contentBox.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 2);"
        );
        contentBox.setMaxWidth(400);
        contentBox.setMinWidth(200);

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(30, 30);

        // Result label
        resultLabel = new Label();
        resultLabel.setWrapText(true);
        resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

        popup.getContent().add(contentBox);
    }

    public void showLoading(Stage owner, double x, double y, String message) {
        contentBox.getChildren().clear();

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        contentBox.getChildren().addAll(loadingIndicator, msgLabel);
        popup.show(owner, x, y);
    }

    public void showResult(String title, String content) {
        contentBox.getChildren().clear();

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0084ff;");

        resultLabel.setText(content);

        contentBox.getChildren().addAll(titleLabel, resultLabel);
    }

    public void showError(String message) {
        contentBox.getChildren().clear();

        Label errorLabel = new Label("❌ " + message);
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #d32f2f;");

        contentBox.getChildren().add(errorLabel);
    }

    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    public boolean isShowing() {
        return popup.isShowing();
    }
} 