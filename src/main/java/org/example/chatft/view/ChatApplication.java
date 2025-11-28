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
    private volatile boolean isShuttingDown = false;

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

            // Store ChatApplication instance in stage userData for shutdown handling
            primaryStage.setUserData(this);

            // Handle window close
            primaryStage.setOnCloseRequest(e -> {
                System.out.println("========================================");
                System.out.println("WINDOW CLOSE REQUESTED");
                System.out.println("========================================");
                performShutdown();
            });

            primaryStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load login view: " + e.getMessage());
        }
    }

    public void setMainController(MainController controller) {
        this.mainController = controller;
        System.out.println("[APP] ✅ MainController registered: " + (controller != null ? "SUCCESS" : "NULL"));
    }
    
    private void performShutdown() {
        if (isShuttingDown) {
            System.out.println("Shutdown already in progress, skipping...");
            return;
        }
        
        isShuttingDown = true;
        
        if (mainController != null) {
            System.out.println("Broadcasting offline and shutting down...");
            mainController.shutdown();
            System.out.println("Shutdown complete");
        } else {
            System.out.println("WARNING: MainController is NULL");
        }
        
        System.out.println("Exiting application");
        javafx.application.Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() throws Exception {
        performShutdown();
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}