package org.example.chatft.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.example.chatft.model.FileMessage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public class FileHelper {

    /**
     * Open file with default application
     */
    public void openFile(String filePath) {
        try {
            Desktop.getDesktop().open(new File(filePath));
        } catch (IOException e) {
            showError("Cannot open file: " + e.getMessage());
        }
    }

    /**
     * Open file location in file explorer
     */
    public void openFileLocation(String filePath) {
        try {
            File file = new File(filePath);
            Desktop.getDesktop().open(file.getParentFile());
        } catch (IOException e) {
            showError("Cannot open folder: " + e.getMessage());
        }
    }

    /**
     * Format file size to human readable string
     */
    public String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    /**
     * Show file received notification for private files
     */
//    public void showFileReceivedNotification(FileMessage fileMsg) {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle("File Received");
//        alert.setHeaderText("File received from " + fileMsg.getSender());
//        alert.setContentText("File: " + fileMsg.getFileName() +
//                "\nSize: " + formatFileSize(fileMsg.getFileSize()));
//
//        ButtonType openBtn = new ButtonType("Open File");
//        ButtonType openFolderBtn = new ButtonType("Open Folder");
//        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
//
//        alert.getButtonTypes().setAll(openBtn, openFolderBtn, closeBtn);
//
//        alert.showAndWait().ifPresent(response -> {
//            if (response == openBtn) {
//                openFile(fileMsg.getFilePath());
//            } else if (response == openFolderBtn) {
//                openFileLocation(fileMsg.getFilePath());
//            }
//        });
//    }
//
//    /**
//     * Show file received notification for group files
//     */
//    public void showGroupFileReceivedNotification(FileMessage fileMsg) {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle("Group File Received");
//        alert.setHeaderText("File received from " + fileMsg.getSender() +
//                " in group " + fileMsg.getGroupName());
//        alert.setContentText("File: " + fileMsg.getFileName() +
//                "\nSize: " + formatFileSize(fileMsg.getFileSize()));
//
//        ButtonType openBtn = new ButtonType("Open File");
//        ButtonType openFolderBtn = new ButtonType("Open Folder");
//        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
//
//        alert.getButtonTypes().setAll(openBtn, openFolderBtn, closeBtn);
//
//        alert.showAndWait().ifPresent(response -> {
//            if (response == openBtn) {
//                openFile(fileMsg.getFilePath());
//            } else if (response == openFolderBtn) {
//                openFileLocation(fileMsg.getFilePath());
//            }
//        });
//    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }
}