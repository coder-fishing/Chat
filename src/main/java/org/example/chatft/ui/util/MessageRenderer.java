package org.example.chatft.ui.util;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import javafx.util.Duration;
import org.example.chatft.ui.components.MessageContextMenu;

import java.io.File;
import java.io.FileInputStream;

public class MessageRenderer {

    private final FileHelper fileHelper = new FileHelper();
    private final ScrollPane scrollPane;
    private final MessageContextMenu messageContextMenu;

    private static final String LIKE_EMOJI = "__LIKE_ICON__";

    public MessageRenderer(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
        this.messageContextMenu = new MessageContextMenu();
    }

    private String lastSender = null;

    public void addMessage(VBox box, String sender, String text, boolean isMe, boolean isFile) {
        boolean isLikeMessage = text.trim().equals(LIKE_EMOJI);
        boolean isSystemMessage = "__SYSTEM__".equals(sender);

        VBox messageBox = new VBox(2);
        messageBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // System messages (join/leave notifications) should be centered
        if (isSystemMessage) {
            messageBox.setAlignment(Pos.CENTER);
        }

        boolean showSender = !isMe && !isSystemMessage && (lastSender == null || !lastSender.equals(sender));

        if (showSender) {
            Label senderLabel = new Label(sender);
            senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
            messageBox.getChildren().add(senderLabel);
        }

        if (isLikeMessage) {
            // ✅ HIỂN THỊ ICON LIKE BẰNG HÌNH ẢNH
            ImageView likeIcon = new ImageView(
                    new Image(getClass().getResourceAsStream("/org/example/chatft/ui/view/icons/liked.png"))
            );
            likeIcon.setFitWidth(36);
            likeIcon.setFitHeight(36);
            likeIcon.setPreserveRatio(true);

            HBox iconContainer = new HBox(likeIcon);
            iconContainer.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageBox.getChildren().add(iconContainer);

        } else {
            // ✅ Use Label with Text for selectable text
            Label label = new Label(text);
            label.setWrapText(true);
            
            // ✅ Giới hạn chiều rộng tối đa 70% khung chat
            if (!isSystemMessage) {
                label.maxWidthProperty().bind(box.widthProperty().multiply(0.7));
            }

            String bgColor, textColor, fontSize;
            
            if (isSystemMessage) {
                // System messages: gray, italic, smaller, centered
                bgColor = "#F0F0F0";
                textColor = "#888888";
                fontSize = "12px";
                label.setStyle(
                        "-fx-padding: 4 8;" +
                                "-fx-background-radius: 8;" +
                                "-fx-background-color: " + bgColor + ";" +
                                "-fx-text-fill: " + textColor + ";" +
                                "-fx-font-size: " + fontSize + ";" +
                                "-fx-font-family: 'Segoe UI';" +
                                "-fx-font-style: italic;"
                );
            } else {
                bgColor = isMe ? "#3797F0" : "#EFEFEF";
                textColor = isMe ? "white" : "black";
                fontSize = "16px";
                label.setStyle(
                        "-fx-padding: 8 12;" +
                                "-fx-background-radius: 12;" +
                                "-fx-background-color: " + bgColor + ";" +
                                "-fx-text-fill: " + textColor + ";" +
                                "-fx-font-size: " + fontSize + ";" +
                                "-fx-font-family: 'Segoe UI';"
                );
            }

            // ✅ Make label text selectable by converting to TextArea when clicked (skip for system messages)
            if (!isSystemMessage) {
                label.setOnMousePressed(event -> {
                    if (event.isPrimaryButtonDown()) {
                        // Convert to TextArea for selection (supports multi-line)
                        TextArea textArea = new TextArea(text);
                        textArea.setWrapText(true);
                        textArea.setEditable(false);
                        textArea.setFocusTraversable(false);
                        textArea.setPrefWidth(label.getWidth());
                        textArea.setPrefHeight(label.getHeight());
                        textArea.maxWidthProperty().bind(box.widthProperty().multiply(0.7));
                        
                        // ✅ Disable default context menu (Copy, Select All)
                        textArea.setContextMenu(new ContextMenu()); // Set empty context menu to override default
                        // Different highlight colors for better visibility
                        String highlightFill = isMe ? "rgba(255,255,255,0.4)" : "rgba(56,151,240,0.4)";
                        String highlightTextFill = isMe ? "white" : "black";
                        
                        textArea.setStyle(
                                "-fx-padding: 8 12;" +
                                        "-fx-background-radius: 12;" +
                                        "-fx-background-color: " + bgColor + ";" +
                                        "-fx-text-fill: " + textColor + ";" +
                                        "-fx-font-size: 16px;" +
                                        "-fx-font-family: 'Segoe UI';" +
                                        "-fx-control-inner-background: " + bgColor + ";" +
                                        "-fx-background-insets: 0;" +
                                        "-fx-border-width: 0;" +
                                        "-fx-border-color: transparent;" +
                                        "-fx-focus-color: transparent;" +
                                        "-fx-faint-focus-color: transparent;" +
                                        "-fx-text-box-border: transparent;" +
                                        "-fx-highlight-fill: " + highlightFill + ";" +
                                        "-fx-highlight-text-fill: " + highlightTextFill + ";" +
                                        "-fx-cursor: text;"
                        );
                        
                        // ✅ Override context menu with custom one - use onContextMenuRequested
                        textArea.setOnContextMenuRequested(e -> {
                            String selectedText = textArea.getSelectedText();
                            if (selectedText != null && !selectedText.trim().isEmpty()) {
                                Window owner = textArea.getScene().getWindow();
                                messageContextMenu.show(owner, e.getScreenX(), e.getScreenY(), selectedText);
                            }
                            e.consume(); // Prevent default context menu
                        });
                        
                        // Replace label with textArea temporarily
                        HBox container = (HBox) label.getParent();
                        container.getChildren().set(0, textArea);
                        
                        Platform.runLater(() -> {
                            textArea.requestFocus();
                            textArea.selectAll();
                            
                            // Hide scrollbars
                            var scrollPane = textArea.lookup(".scroll-pane");
                            if (scrollPane != null) {
                                scrollPane.setStyle("-fx-background-color: " + bgColor + "; -fx-background-insets: 0;");
                            }
                            var viewport = textArea.lookup(".scroll-pane .viewport");
                            if (viewport != null) {
                                viewport.setStyle("-fx-background-color: " + bgColor + ";");
                            }
                            var content = textArea.lookup(".scroll-pane .content");
                            if (content != null) {
                                content.setStyle("-fx-background-color: " + bgColor + ";");
                            }
                        });
                        
                        // Revert back to label when focus is lost
                        textArea.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                            if (!isNowFocused) {
                                container.getChildren().set(0, label);
                            }
                        });
                    }
                });
            }

            HBox container = new HBox(label);
            container.setAlignment(isSystemMessage ? Pos.CENTER : (isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT));

            messageBox.getChildren().add(container);
        }

        HBox outer = new HBox(messageBox);
        outer.setAlignment(isSystemMessage ? Pos.CENTER : (isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT));
        outer.setSpacing(5);

        box.getChildren().add(outer);

        smoothScrollToBottom();

        lastSender = isMe ? "me" : sender;
    }

    /**
     * ✅ Fixed: Hiển thị ảnh cho cả người gửi và người nhận
     */
    public void addFileMessage(VBox box, String sender, String fileName, long fileSize,
                               String filePath, boolean isMe) {
        // VBox chứa tên người gửi + nội dung
        VBox messageBox = new VBox(2);
        messageBox.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Hiển thị tên người gửi (chỉ khi cần)
        boolean showSender = !isMe && (lastSender == null || !lastSender.equals(sender));
        if (showSender) {
            Label senderLabel = new Label(sender);
            senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
            messageBox.getChildren().add(senderLabel);
        }

        // Hộp chứa nội dung file
        VBox fileBox = new VBox(5);
        fileBox.setMaxWidth(300);
        fileBox.setPadding(new Insets(8));

        // ✅ Kiểm tra xem có phải file ảnh không
        boolean isImage = isImageFile(fileName);
        boolean imageLoaded = false;

        // ✅ Nếu là ảnh VÀ file tồn tại → hiển thị ảnh (cho cả người gửi và nhận)
        if (isImage && filePath != null && !filePath.trim().isEmpty()) {
            try {
                File imageFile = new File(filePath);

                if (!imageFile.exists()) {
                    System.err.println("❌ File không tồn tại: " + filePath);
                    throw new Exception("File không tồn tại");
                }

                // Thử load ảnh
                Image image = new Image(new FileInputStream(imageFile));

                if (image.isError()) {
                    System.err.println("❌ Lỗi khi load ảnh");
                    throw new Exception("Lỗi khi load ảnh");
                }

                ImageView imageView = new ImageView(image);

                double maxWidth = 250;
                double maxHeight = 200;
                double widthRatio = maxWidth / image.getWidth();
                double heightRatio = maxHeight / image.getHeight();
                double ratio = Math.min(1.0, Math.min(widthRatio, heightRatio));

                imageView.setFitWidth(image.getWidth() * ratio);
                imageView.setFitHeight(image.getHeight() * ratio);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setStyle("-fx-cursor: hand;");

                // Double click để mở ảnh
                imageView.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) fileHelper.openFile(filePath);
                });

                fileBox.getChildren().add(imageView);
                imageLoaded = true;

            } catch (Exception e) {
                System.err.println("❌ Không load được ảnh: " + e.getMessage());
                imageLoaded = false;
            }
        }

        // ✅ Nếu KHÔNG phải ảnh HOẶC load ảnh thất bại → hiển thị icon file
        if (!imageLoaded) {
            addFileIcon(fileBox, fileName, fileSize, filePath );
        }

        // Style bong bóng chat
        fileBox.setStyle(
                "-fx-background-color: " + ("#FFFFFF;") +
                        "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #ddd;"
        );

        // ✅ Thêm fileBox vào messageBox
        messageBox.getChildren().add(fileBox);

        // ✅ Gộp vào HBox outer giống như addMessage()
        HBox outer = new HBox(messageBox);
        outer.setAlignment(isMe ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        outer.setSpacing(5);

        box.getChildren().add(outer);

        smoothScrollToBottom();

        lastSender = isMe ? "me" : sender;
    }

    /**
     * ✅ Fixed: Icon file với tên và kích thước
     */
    private void addFileIcon(VBox fileBox, String fileName, long fileSize, String filePath) {
        // Icon file (dùng Label thay vì emoji để tránh lỗi render)
        Label iconLabel = new Label();
        iconLabel.setStyle(
                "-fx-font-size: 40px; " +
                        "-fx-text-fill: #666; " +
                        "-fx-background-color: #f5f5f5; " +
                        "-fx-padding: 20; " +
                        "-fx-background-radius: 8;"
        );

        // Xác định icon dựa trên extension
        String ext = getFileExtension(fileName).toLowerCase();
        String icon = switch (ext) {
            case "pdf" -> "📄";
            case "doc", "docx" -> "📝";
            case "xls", "xlsx" -> "📊";
            case "zip", "rar" -> "📦";
            case "mp3", "wav" -> "🎵";
            case "mp4", "avi" -> "🎬";
            default -> "📎";
        };
        iconLabel.setText(icon);

        VBox iconBox = new VBox(iconLabel);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 8;");

        fileBox.getChildren().add(iconBox);

        // Tên file
        Label fileNameLabel = new Label(fileName);
        fileNameLabel.setStyle(
                "-fx-font-weight: bold; " +
                        "-fx-font-size: 13px; " +
                        "-fx-text-fill: #333; " +
                        "-fx-wrap-text: true;"
        );
        fileNameLabel.setMaxWidth(280);


        // Kích thước file
        Label fileSizeLabel = new Label(fileHelper.formatFileSize(fileSize));
        fileSizeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        fileBox.getChildren().addAll(fileNameLabel, fileSizeLabel);

        fileBox.setOnMouseClicked(
                    e -> {
                        if (e.getClickCount() == 2) fileHelper.openFile(filePath);
                    }
        );

    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private void smoothScrollToBottom() {
        Platform.runLater(() -> {
            if (scrollPane == null) return;
            Timeline t = new Timeline();
            KeyValue kv = new KeyValue(scrollPane.vvalueProperty(), 1.0, Interpolator.EASE_BOTH);
            KeyFrame kf = new KeyFrame(Duration.millis(300), kv);
            t.getKeyFrames().add(kf);
            t.play();
        });
    }
}