package org.example.chatft;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MainApp extends Application {
    private ObservableList<User> users = FXCollections.observableArrayList();
    private ListView<User> userList = new ListView<>(users);

    private ObservableList<Group> groups = FXCollections.observableArrayList();
    private ListView<Group> groupList = new ListView<>(groups);

    private NetworkService network;

    private Map<String, VBox> chatBoxes = new HashMap<>();
    private Map<String, VBox> groupChatBoxes = new HashMap<>();

    private User currentChatUser;
    private Group currentChatGroup;

    private BorderPane chatPane;
    private ScrollPane chatScrollPane;
    private VBox messagesBox;
    private TextField input;
    private Button sendBtn;
    private Button fileBtn;
    private Label chatHeader;

    @Override
    public void start(Stage stage) {
        TextField nicknameField = new TextField();
        Button joinBtn = new Button("Join");

        VBox loginBox = new VBox(10,
                new Label("Nickname:"), nicknameField, joinBtn);
        loginBox.setPadding(new Insets(10));

        BorderPane root = new BorderPane();

        // ===== Left Sidebar với Tabs =====
        TabPane leftTabs = new TabPane();

        // Tab Users
        Tab usersTab = new Tab("Users");
        usersTab.setClosable(false);
        userList.setPrefWidth(200);

        // Custom cell factory cho User list với unread badge
        userList.setCellFactory(lv -> new ListCell<User>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    HBox container = new HBox(5);
                    container.setAlignment(Pos.CENTER_LEFT);

                    Label nameLabel = new Label(user.toString());
                    nameLabel.setMaxWidth(140);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);

                    container.getChildren().add(nameLabel);

                    // Thêm badge nếu có unread
                    if (user.getUnreadCount() > 0) {
                        String badgeText = user.getUnreadCount() > 9 ? "9+" : String.valueOf(user.getUnreadCount());
                        Label badge = new Label(badgeText);
                        badge.setStyle("-fx-background-color: #ff4444; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 2 6 2 6; " +
                                "-fx-background-radius: 10; " +
                                "-fx-font-size: 10px; " +
                                "-fx-font-weight: bold;");
                        container.getChildren().add(badge);

                        // Viền đỏ cho item
                        setStyle("-fx-border-color: #ff4444; " +
                                "-fx-border-width: 0 0 0 3; " +
                                "-fx-background-color: #fff5f5;");
                    } else {
                        setStyle("");
                    }

                    setGraphic(container);
                    setText(null);
                }
            }
        });

        usersTab.setContent(userList);

        // Tab Groups
        Tab groupsTab = new Tab("Groups");
        groupsTab.setClosable(false);

        VBox groupContainer = new VBox(5);
        groupContainer.setPadding(new Insets(5));

        Button createPublicBtn = new Button("Create Public Group");
        createPublicBtn.setPrefWidth(180);
        Button createPrivateBtn = new Button("Create Private Group");
        createPrivateBtn.setPrefWidth(180);
        Button joinGroupBtn = new Button("Join Group");
        joinGroupBtn.setPrefWidth(180);

        groupList.setPrefWidth(200);
        groupList.setPrefHeight(300);

        // Custom cell factory cho Group list với unread badge
        groupList.setCellFactory(lv -> new ListCell<Group>() {
            @Override
            protected void updateItem(Group group, boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    HBox container = new HBox(5);
                    container.setAlignment(Pos.CENTER_LEFT);

                    Label nameLabel = new Label(group.toString());
                    nameLabel.setMaxWidth(140);
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);

                    container.getChildren().add(nameLabel);

                    // Thêm badge nếu có unread và đã join
                    if (group.isJoined() && group.getUnreadCount() > 0) {
                        String badgeText = group.getUnreadCount() > 9 ? "9+" : String.valueOf(group.getUnreadCount());
                        Label badge = new Label(badgeText);
                        badge.setStyle("-fx-background-color: #ff4444; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 2 6 2 6; " +
                                "-fx-background-radius: 10; " +
                                "-fx-font-size: 10px; " +
                                "-fx-font-weight: bold;");
                        container.getChildren().add(badge);

                        // Viền đỏ cho item
                        setStyle("-fx-border-color: #ff4444; " +
                                "-fx-border-width: 0 0 0 3; " +
                                "-fx-background-color: #fff5f5;");
                    } else {
                        setStyle("");
                    }

                    setGraphic(container);
                    setText(null);
                }
            }
        });

        groupContainer.getChildren().addAll(
                createPublicBtn, createPrivateBtn, joinGroupBtn,
                new Separator(), groupList);

        groupsTab.setContent(groupContainer);

        leftTabs.getTabs().addAll(usersTab, groupsTab);
        leftTabs.setPrefWidth(220);

        root.setLeft(leftTabs);

        // ===== Chat view =====
        chatHeader = new Label("Select a user or group to start chatting...");
        chatHeader.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        BorderPane.setMargin(chatHeader, new Insets(5));

        messagesBox = new VBox(5);
        messagesBox.setPadding(new Insets(10));

        chatScrollPane = new ScrollPane(messagesBox);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        input = new TextField();
        input.setDisable(true);
        sendBtn = new Button("Send");
        sendBtn.setDisable(true);
        fileBtn = new Button("📎 File");
        fileBtn.setDisable(true);

        HBox inputBox = new HBox(10, input, sendBtn, fileBtn);
        inputBox.setPadding(new Insets(10));
        HBox.setHgrow(input, Priority.ALWAYS);

        chatPane = new BorderPane();
        chatPane.setTop(chatHeader);
        chatPane.setCenter(chatScrollPane);
        chatPane.setBottom(inputBox);

        root.setCenter(chatPane);

        Scene scene = new Scene(new VBox(loginBox, root), 800, 600);
        stage.setScene(scene);
        stage.setTitle("P2P Chat with File Transfer & Groups");
        stage.show();

        // ===== EVENT HANDLERS =====

        joinBtn.setOnAction(e -> {
            String nickname = nicknameField.getText().trim();
            if (!nickname.isEmpty()) {
                try {
                    network = new NetworkService(
                            nickname,
                            user -> Platform.runLater(() -> {
                                if (users.stream().noneMatch(u -> u.getNickname().equals(user.getNickname()))) {
                                    users.add(user);
                                }
                            }),
                            user -> Platform.runLater(() -> users.removeIf(u -> u.getNickname().equals(user.getNickname()))),
                            msg -> Platform.runLater(() -> {
                                handleIncomingMessage(msg);
                                scrollToBottom();
                            }),
                            fileMsg -> Platform.runLater(() -> {
                                handleIncomingFile(fileMsg);
                                scrollToBottom();
                            }),
                            groupMsg -> Platform.runLater(() -> {
                                handleIncomingGroupMessage(groupMsg);
                                scrollToBottom();
                            }),
                            group -> Platform.runLater(() -> {
                                if (groups.stream().noneMatch(g -> g.getName().equals(group.getName()))) {
                                    groups.add(group);
                                }
                            })
                    );
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showAlert("Error", "Failed to start network service: " + ex.getMessage());
                    return;
                }
                joinBtn.setDisable(true);
                nicknameField.setDisable(true);
                input.setDisable(false);
                sendBtn.setDisable(false);
                fileBtn.setDisable(false);
                createPublicBtn.setDisable(false);
                createPrivateBtn.setDisable(false);
                joinGroupBtn.setDisable(false);
            }
        });

        createPublicBtn.setDisable(true);
        createPrivateBtn.setDisable(true);
        joinGroupBtn.setDisable(true);

        createPublicBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Create Public Group");
            dialog.setHeaderText("Enter group name:");
            Optional<String> result = dialog.showAndWait();

            result.ifPresent(groupName -> {
                if (!groupName.trim().isEmpty()) {
                    Group newGroup = new Group(groupName.trim(), true, null);
                    newGroup.setJoined(true);
                    if (groups.stream().noneMatch(g -> g.getName().equals(newGroup.getName()))) {
                        groups.add(newGroup);
                    }
                    network.createPublicGroup(groupName.trim());
                }
            });
        });

        createPrivateBtn.setOnAction(e -> {
            Dialog<String[]> dialog = new Dialog<>();
            dialog.setTitle("Create Private Group");
            dialog.setHeaderText("Create a private group");

            ButtonType createButtonType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField groupName = new TextField();
            groupName.setPromptText("Group name");
            PasswordField password = new PasswordField();
            password.setPromptText("Password");

            grid.add(new Label("Group Name:"), 0, 0);
            grid.add(groupName, 1, 0);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(password, 1, 1);

            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == createButtonType) {
                    return new String[]{groupName.getText(), password.getText()};
                }
                return null;
            });

            Optional<String[]> result = dialog.showAndWait();
            result.ifPresent(data -> {
                if (!data[0].trim().isEmpty() && !data[1].trim().isEmpty()) {
                    Group newGroup = new Group(data[0].trim(), false, data[1].trim());
                    newGroup.setJoined(true);
                    if (groups.stream().noneMatch(g -> g.getName().equals(newGroup.getName()))) {
                        groups.add(newGroup);
                    }
                    network.createPrivateGroup(data[0].trim(), data[1].trim());
                }
            });
        });

        joinGroupBtn.setOnAction(e -> {
            Group selected = groupList.getSelectionModel().getSelectedItem();
            if (selected != null && !selected.isJoined()) {
                if (selected.isPublic()) {
                    if (network.joinGroup(selected.getName(), null)) {
                        selected.setJoined(true);
                        groupList.refresh();
                        System.out.println("Successfully joined public group: " + selected.getName());
                    } else {
                        showAlert("Error", "Failed to join public group!");
                    }
                } else {
                    TextInputDialog dialog = new TextInputDialog();
                    dialog.setTitle("Join Private Group");
                    dialog.setHeaderText("Enter password for group: " + selected.getName());
                    dialog.getEditor().setPromptText("Password");

                    Optional<String> result = dialog.showAndWait();
                    result.ifPresent(password -> {
                        if (network.joinGroup(selected.getName(), password)) {
                            selected.setJoined(true);
                            groupList.refresh();
                            System.out.println("Successfully joined private group: " + selected.getName());
                        } else {
                            showAlert("Error", "Invalid password for private group!");
                        }
                    });
                }
            } else if (selected != null && selected.isJoined()) {
                showAlert("Info", "You are already joined to this group!");
            }
        });

        // User selection - Reset unread khi click
        userList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                User selected = userList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    currentChatUser = selected;
                    currentChatGroup = null;
                    groupList.getSelectionModel().clearSelection();

                    // Reset unread count
                    selected.resetUnread();
                    userList.refresh();

                    VBox box = chatBoxes.computeIfAbsent(selected.getNickname(), k -> new VBox(5));
                    box.setPadding(new Insets(10));
                    messagesBox = box;
                    chatScrollPane.setContent(messagesBox);

                    chatHeader.setText("Chat with " + selected.getNickname());
                    fileBtn.setVisible(true);
                }
            }
        });

        // Group selection - Reset unread khi click
        groupList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                Group selected = groupList.getSelectionModel().getSelectedItem();
                if (selected != null && selected.isJoined()) {
                    currentChatGroup = selected;
                    currentChatUser = null;
                    userList.getSelectionModel().clearSelection();

                    // Reset unread count
                    selected.resetUnread();
                    groupList.refresh();

                    VBox box = groupChatBoxes.computeIfAbsent(selected.getName(), k -> new VBox(5));
                    box.setPadding(new Insets(10));
                    messagesBox = box;
                    chatScrollPane.setContent(messagesBox);

                    String type = selected.isPublic() ? "Public" : "Private";
                    chatHeader.setText(type + " Group: " + selected.getName());
                    fileBtn.setVisible(true);
                }
            }
        });

        sendBtn.setOnAction(e -> {
            String msg = input.getText().trim();
            if (!msg.isEmpty()) {
                if (currentChatUser != null) {
                    VBox box = chatBoxes.get(currentChatUser.getNickname());
                    addMessage(box, "Me: " + msg, true, false);
                    network.sendMessage(currentChatUser, msg);
                } else if (currentChatGroup != null) {
                    VBox box = groupChatBoxes.get(currentChatGroup.getName());
                    addMessage(box, "Me: " + msg, true, false);
                    network.sendGroupMessage(currentChatGroup.getName(), msg);
                }
                input.clear();
                scrollToBottom();
            }
        });

        fileBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select File to Send");
            File file = fileChooser.showOpenDialog(stage);

            if (file != null) {
                if (currentChatUser != null) {
                    VBox box = chatBoxes.get(currentChatUser.getNickname());
                    addFileMessage(box, "Me", file.getName(), file.length(), null, true);
                    network.sendFile(currentChatUser, file.getAbsolutePath());
                } else if (currentChatGroup != null) {
                    VBox box = groupChatBoxes.get(currentChatGroup.getName());
                    addFileMessage(box, "Me", file.getName(), file.length(), null, true);
                    network.sendGroupFile(currentChatGroup.getName(), file.getAbsolutePath());
                }
                scrollToBottom();
            }
        });

        input.setOnAction(e -> sendBtn.fire());

        stage.setOnCloseRequest(e -> {
            if (network != null) {
                network.broadcastOffline();
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addMessage(VBox box, String text, boolean isMe, boolean isFile) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(300);

        String bgColor = isMe ? "lightblue" : "lightgray";
        if (isFile) {
            bgColor = isMe ? "#e3f2fd" : "#f5f5f5";
        }

        label.setStyle("-fx-padding: 8; -fx-background-radius: 12;"
                + "-fx-background-color: " + bgColor + ";");

        HBox container = new HBox(label);
        container.setSpacing(10);

        if (isMe) {
            container.setAlignment(Pos.CENTER_RIGHT);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
        }

        box.getChildren().add(container);
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                lowerName.endsWith(".bmp") || lowerName.endsWith(".webp");
    }

    private void addFileMessage(VBox box, String sender, String fileName, long fileSize, String filePath, boolean isMe) {
        VBox fileBox = new VBox(5);
        fileBox.setMaxWidth(300);
        fileBox.setPadding(new Insets(8));

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

                imageView.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        openFile(filePath);
                    }
                });

                imageView.setStyle("-fx-cursor: hand;");

                fileBox.getChildren().add(imageView);

            } catch (Exception e) {
                addFileIcon(fileBox, fileName, fileSize);
            }
        } else {
            addFileIcon(fileBox, fileName, fileSize);
        }

        Label fileNameLabel = new Label("📎 " + fileName);
        fileNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label fileSizeLabel = new Label(formatFileSize(fileSize));
        fileSizeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        fileBox.getChildren().addAll(fileNameLabel, fileSizeLabel);

        if (filePath != null) {
            HBox btnBox = new HBox(5);

            Button openBtn = new Button("Open");
            openBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
            openBtn.setOnAction(e -> openFile(filePath));

            Button openFolderBtn = new Button("Show in Folder");
            openFolderBtn.setStyle("-fx-font-size: 9px; -fx-padding: 2 6 2 6;");
            openFolderBtn.setOnAction(e -> openFileLocation(filePath));

            btnBox.getChildren().addAll(openBtn, openFolderBtn);
            fileBox.getChildren().add(btnBox);
        }

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

    private void addFileIcon(VBox fileBox, String fileName, long fileSize) {
        Label iconLabel = new Label("📄");
        iconLabel.setStyle("-fx-font-size: 32px;");

        VBox iconBox = new VBox(iconLabel);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setPrefHeight(80);
        iconBox.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 8;");

        fileBox.getChildren().add(iconBox);
    }

    private void openFile(String filePath) {
        try {
            Desktop.getDesktop().open(new File(filePath));
        } catch (IOException e) {
            showAlert("Error", "Cannot open file: " + e.getMessage());
        }
    }

    private void openFileLocation(String filePath) {
        try {
            File file = new File(filePath);
            Desktop.getDesktop().open(file.getParentFile());
        } catch (IOException e) {
            showAlert("Error", "Cannot open folder: " + e.getMessage());
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void handleIncomingMessage(String msg) {
        String[] parts = msg.split(": ", 2);
        if (parts.length >= 2) {
            String senderName = parts[0];
            String message = parts[1];

            User sender = users.stream()
                    .filter(u -> u.getNickname().equals(senderName))
                    .findFirst()
                    .orElse(null);

            if (sender != null) {
                VBox box = chatBoxes.computeIfAbsent(sender.getNickname(), k -> new VBox(5));
                box.setPadding(new Insets(10));
                addMessage(box, senderName + ": " + message, false, false);

                // Tăng unread nếu không phải chat hiện tại
                if (currentChatUser == null || !currentChatUser.getNickname().equals(senderName)) {
                    sender.incrementUnread();
                    userList.refresh();
                }

                if (currentChatUser != null && currentChatUser.getNickname().equals(senderName)) {
                    messagesBox = box;
                    chatScrollPane.setContent(messagesBox);
                    scrollToBottom();
                }
            }
        }
    }

    private void handleIncomingGroupMessage(GroupMessage groupMsg) {
        VBox box = groupChatBoxes.computeIfAbsent(groupMsg.getGroupName(), k -> new VBox(5));
        box.setPadding(new Insets(10));
        addMessage(box, groupMsg.getSender() + ": " + groupMsg.getContent(), false, false);

        // Tìm group và tăng unread nếu không phải chat hiện tại
        Group group = groups.stream()
                .filter(g -> g.getName().equals(groupMsg.getGroupName()))
                .findFirst()
                .orElse(null);

        if (group != null && (currentChatGroup == null || !currentChatGroup.getName().equals(groupMsg.getGroupName()))) {
            group.incrementUnread();
            groupList.refresh();
        }

        if (currentChatGroup != null && currentChatGroup.getName().equals(groupMsg.getGroupName())) {
            messagesBox = box;
            chatScrollPane.setContent(messagesBox);
            scrollToBottom();
        }
    }

    private void handleIncomingFile(FileMessage fileMsg) {
        if (fileMsg.isGroupMessage()) {
            // Group file
            VBox box = groupChatBoxes.computeIfAbsent(fileMsg.getGroupName(), k -> new VBox(5));
            box.setPadding(new Insets(10));
            addFileMessage(box, fileMsg.getSender(), fileMsg.getFileName(),
                    fileMsg.getFileSize(), fileMsg.getFilePath(), false);

            // Tìm group và tăng unread nếu không phải chat hiện tại
            Group group = groups.stream()
                    .filter(g -> g.getName().equals(fileMsg.getGroupName()))
                    .findFirst()
                    .orElse(null);

            if (group != null && (currentChatGroup == null || !currentChatGroup.getName().equals(fileMsg.getGroupName()))) {
                group.incrementUnread();
                groupList.refresh();
            }

            if (currentChatGroup != null && currentChatGroup.getName().equals(fileMsg.getGroupName())) {
                messagesBox = box;
                chatScrollPane.setContent(messagesBox);
                scrollToBottom();
            }

            showGroupFileReceivedNotification(fileMsg);
        } else {
            // Private file
            User sender = users.stream()
                    .filter(u -> u.getNickname().equals(fileMsg.getSender()))
                    .findFirst()
                    .orElse(null);

            if (sender != null) {
                VBox box = chatBoxes.computeIfAbsent(sender.getNickname(), k -> new VBox(5));
                box.setPadding(new Insets(10));
                addFileMessage(box, fileMsg.getSender(), fileMsg.getFileName(),
                        fileMsg.getFileSize(), fileMsg.getFilePath(), false);

                // Tăng unread nếu không phải chat hiện tại
                if (currentChatUser == null || !currentChatUser.getNickname().equals(sender.getNickname())) {
                    sender.incrementUnread();
                    userList.refresh();
                }

                if (currentChatUser != null && currentChatUser.getNickname().equals(sender.getNickname())) {
                    messagesBox = box;
                    chatScrollPane.setContent(messagesBox);
                    scrollToBottom();
                }

                showFileReceivedNotification(fileMsg);
            }
        }
    }

    private void showFileReceivedNotification(FileMessage fileMsg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("File Received");
        alert.setHeaderText("File received from " + fileMsg.getSender());
        alert.setContentText("File: " + fileMsg.getFileName() + "\nSize: " + fileMsg.getFileSizeFormatted());

        ButtonType openBtn = new ButtonType("Open File");
        ButtonType openFolderBtn = new ButtonType("Open Folder");
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(openBtn, openFolderBtn, closeBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == openBtn) {
                openFile(fileMsg.getFilePath());
            } else if (response == openFolderBtn) {
                openFileLocation(fileMsg.getFilePath());
            }
        });
    }

    private void showGroupFileReceivedNotification(FileMessage fileMsg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Group File Received");
        alert.setHeaderText("File received from " + fileMsg.getSender() + " in group " + fileMsg.getGroupName());
        alert.setContentText("File: " + fileMsg.getFileName() + "\nSize: " + fileMsg.getFileSizeFormatted());

        ButtonType openBtn = new ButtonType("Open File");
        ButtonType openFolderBtn = new ButtonType("Open Folder");
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(openBtn, openFolderBtn, closeBtn);

        alert.showAndWait().ifPresent(response -> {
            if (response == openBtn) {
                openFile(fileMsg.getFilePath());
            } else if (response == openFolderBtn) {
                openFileLocation(fileMsg.getFilePath());
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}