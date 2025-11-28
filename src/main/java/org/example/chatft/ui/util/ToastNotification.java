package org.example.chatft.ui.util;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class ToastNotification {

    public enum Type {
        SUCCESS, WARNING, ERROR, INFO
    }

    public static void show(Stage ownerStage, String message, Type type) {
        Stage toastStage = new Stage();
        toastStage.initOwner(ownerStage);
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);

        Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(350);
        
        // Apply style based on type
        String styleClass = switch (type) {
            case SUCCESS -> "toast-success";
            case WARNING -> "toast-warning";
            case ERROR -> "toast-error";
            case INFO -> "toast-info";
        };
        
        label.getStyleClass().addAll("toast", styleClass);

        StackPane root = new StackPane(label);
        root.setStyle("-fx-background-color: transparent;");
        root.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(
            ToastNotification.class.getResource("/org/example/chatft/ui/view/style/toast.css").toExternalForm()
        );

        toastStage.setScene(scene);

        // Position at top center of owner stage
        if (ownerStage != null) {
            toastStage.setX(ownerStage.getX() + (ownerStage.getWidth() / 2) - 175);
            toastStage.setY(ownerStage.getY() + 50);
        }

        toastStage.show();

        // Fade in
        label.setOpacity(0);
        Timeline fadeIn = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(label.opacityProperty(), 0)),
            new KeyFrame(Duration.millis(300), new KeyValue(label.opacityProperty(), 1))
        );
        fadeIn.play();

        // Auto hide after 3 seconds
        Timeline autoHide = new Timeline(new KeyFrame(Duration.millis(3000), e -> {
            // Fade out
            Timeline fadeOut = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(label.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(300), new KeyValue(label.opacityProperty(), 0))
            );
            fadeOut.setOnFinished(evt -> toastStage.close());
            fadeOut.play();
        }));
        autoHide.play();
    }

    // Convenience methods
    public static void showSuccess(Stage ownerStage, String message) {
        show(ownerStage, message, Type.SUCCESS);
    }

    public static void showWarning(Stage ownerStage, String message) {
        show(ownerStage, message, Type.WARNING);
    }

    public static void showError(Stage ownerStage, String message) {
        show(ownerStage, message, Type.ERROR);
    }

    public static void showInfo(Stage ownerStage, String message) {
        show(ownerStage, message, Type.INFO);
    }
}
