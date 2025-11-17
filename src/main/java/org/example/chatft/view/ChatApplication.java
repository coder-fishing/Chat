package org.example.chatft.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.chatft.ui.controller.MainController;

import java.io.IOException;

public class ChatApplication extends Application {

    private MainController mainController;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load login view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/chatft/ui/view/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            primaryStage.setTitle("P2P Chat - Login");
            primaryStage.setScene(scene);
//            primaryStage.setResizable(true);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Handle window close
            primaryStage.setOnCloseRequest(e -> {
                if (mainController != null) {
                    mainController.shutdown();
                }
            });

            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load login view: " + e.getMessage());
        }
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
    }

    public static void main(String[] args) {
        launch(args);
    }
}