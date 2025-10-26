package org.example.chatft.ui.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.FileInputStream;

public class MessageRenderer {

    private final FileHelper fileHelper = new FileHelper();

    /**
     * Add text message to chat box
     */
    public void addMessage(VBox box, String text, boolean isMe, boolean isFile) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(300);

        String bgColor = isMe ? "lightblue" : "lightgray";
        if (isFile) {
            bgColor = isMe ? "#e3f2fd" : "#f5f5f5";
        }

        label.setStyle("-fx-padding: 8; -fx-background-radius: 12;" +
                "-fx-background-color: " + bgColor + ";");

        HBox container = new HBox(label);
        container.setSpacing(10);

        if (isMe) {
            container.setAlignment(Pos.CENTER_RIGHT);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
        }

        box.getChildren().add(container);
    }

    /**
     * Add file message to chat box
     */
    public void addFileMessage(VBox box, String sender, String fileName, long fileSize,
                               String filePath, boolean isMe) {
        VBox fileBox = new VBox(5);
        fileBox.setMaxWidth(300);
        fileBox.setPadding(new Insets(8));

        // If image, show preview
        if (isImageFile(fileName) && filePath != null && new File(filePath).exists()) {
            try {
                Image image = new Image(new FileInputStream(filePath));
                ImageView imageView = new ImageView(image);

                double maxWidth = 250;
                double maxHeight = 200;

                double imgWidth = image.getWidth();
                double imgHeight = image.getHeight();

                if (imgWidth > maxWidth || imgHeight > maxHeight) {
                    double widthRatio = maxWidth / imgWidth;
                    double heightRatio = maxHeight / imgHeight;
                    double ratio = Math.min(widthRatio, heightRatio);

                    imageView.setFitWidth(imgWidth * ratio);
                    imageView.setFitHeight(imgHeight * ratio);
                } else {
                    imageView.setFitWidth(imgWidth);
                    imageView.setFitHeight(imgHeight);
                }

                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setStyle("-fx-cursor: hand;");

                imageView.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        fileHelper.openFile(filePath);
                    }
                });

                fileBox.getChildren().add(imageView);

            } catch (Exception e) {
                addFileIcon(fileBox);
            }
        } else {
            addFileIcon(fileBox);
        }

        // File info
        Label fileNameLabel = new Label("📎 " + fileName);
        fileNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label fileSizeLabel = new Label(fileHelper.formatFileSize(fileSize));
        fileSizeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        fileBox.getChildren().addAll(fileNameLabel, fileSizeLabel);

        // Action buttons
        if (filePath != null) {
            HBox btnBox = new HBox(5);

            Button openBtn = new Button("Open");
            openBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
            openBtn.setOnAction(e -> fileHelper.openFile(filePath));

            Button openFolderBtn = new Button("Show in Folder");
            openFolderBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
            openFolderBtn.setOnAction(e -> fileHelper.openFileLocation(filePath));

            btnBox.getChildren().addAll(openBtn, openFolderBtn);
            fileBox.getChildren().add(btnBox);
        }

        // Styling
        fileBox.setStyle("-fx-background-radius: 12;" +
                "-fx-background-color: " + (isMe ? "#e8f5e8" : "#fff3cd") + ";" +
                "-fx-border-color: " + (isMe ? "#c3e6c3" : "#ffeaa7") + ";" +
                "-fx-border-radius: 12;");

        HBox container = new HBox(fileBox);
        container.setSpacing(10);

        if (isMe) {
            container.setAlignment(Pos.CENTER_RIGHT);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
        }

        box.getChildren().add(container);
    }

    private void addFileIcon(VBox fileBox) {
        Label iconLabel = new Label("📄");
        iconLabel.setStyle("-fx-font-size: 32px;");

        VBox iconBox = new VBox(iconLabel);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setPrefHeight(80);
        iconBox.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 8;");

        fileBox.getChildren().add(iconBox);
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".webp");
    }
}