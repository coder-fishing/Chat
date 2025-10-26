package org.example.chatft.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.chatft.service.NetworkService;

import java.io.IOException;

public class LoginController {
    @FXML
    private TextField nicknameField;

    @FXML
    private Button joinButton;

    @FXML
    private Label errorLabel;

    @FXML
    private void handleJoin() {
        String nickname = nicknameField.getText().trim();

        if (nickname.isEmpty()) {
            showError("Please enter a nickname");
            return;
        }

        try {
            // Load main view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/chatft/ui/view/main.fxml"));
            Parent root = loader.load();

            // Get main controller and initialize network
            MainController mainController = loader.getController();
            mainController.initializeNetwork(nickname);

            // Switch to main scene
            Stage stage = (Stage) joinButton.getScene().getWindow();
            Scene scene = new Scene(root, 800, 600);
            stage.setScene(scene);
            stage.setTitle("P2P Chat - " + nickname);

        } catch (IOException e) {
            e.printStackTrace();
            showError("Failed to start: " + e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}