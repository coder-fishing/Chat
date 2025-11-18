package org.example.chatft.ui.controller;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.chatft.service.NetworkService;
import org.example.chatft.model.FileMessage;
import org.example.chatft.model.Group;
import org.example.chatft.model.GroupMessage;
import org.example.chatft.model.User;
import org.example.chatft.ui.util.FileHelper;
import org.example.chatft.ui.util.MessageRenderer;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MainController {

    // FXML injected components
    @FXML private ListView<User> userListView;
    @FXML private ListView<Group> groupListView;
    @FXML private Button createPublicGroupBtn;
    @FXML private Button createPrivateGroupBtn;
    @FXML private Button joinGroupBtn;
    @FXML private Button addIpButton;
    @FXML private Label chatHeaderLabel;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messagesBox;
    @FXML private TextArea messageInput;
    @FXML private Button sendLikeButton;
    @FXML private Button sendButton;
    @FXML private Button fileButton;
    @FXML private ContextMenu contextMenu;
//    @FXML private Button  emojiButton, addButton;
    @FXML private HBox textFieldContainer;
    private static final int MAX_INPUT_HEIGHT = 120;

    // Data
    private ObservableList<User> users = FXCollections.observableArrayList();
    private ObservableList<Group> groups = FXCollections.observableArrayList();
    private Map<String, VBox> chatBoxes = new HashMap<>();
    private Map<String, VBox> groupChatBoxes = new HashMap<>();

    // State
    private User currentChatUser;
    private Group currentChatGroup;
    private NetworkService network;
    private String nickname;

    // Utilities
    private MessageRenderer messageRenderer;
    private FileHelper fileHelper;

    @FXML
    private void initialize() {
        messageRenderer = new MessageRenderer(chatScrollPane);
        fileHelper = new FileHelper();

        // Ẩn hết chat-related controls
        fileButton.setDisable(true);
        sendButton.setDisable(true);
        sendLikeButton.setDisable(true);
        messageInput.setDisable(true);
//        sendButton.setVisible(true);

        // Khởi tạo ListView
        userListView.setItems(users);
        groupListView.setItems(groups);
        userListView.setCellFactory(lv -> new UserCell());
        setupGroupListCell();

        setupUserSelectionListener();
        setupGroupSelectionListener();
        setupMessageInputListener();
    }

    public void initializeNetwork(String nickname) {
        this.nickname = nickname;

        try {
            network = new NetworkService(
                    nickname,
                    // onUserOnline
                    user -> Platform.runLater(() -> {
                        if (users.stream().noneMatch(u -> u.getNickname().equals(user.getNickname()))) {
                            users.add(user);
                        }
                    }),
                    // onUserOffline
                    user -> Platform.runLater(() ->
                            users.removeIf(u -> u.getNickname().equals(user.getNickname()))
                    ),
                    // onMessage
                    msg -> Platform.runLater(() -> {
                        handleIncomingMessage(msg);
                        scrollToBottom();
                    }),
                    // onFileReceived
                    fileMsg -> Platform.runLater(() -> {
                        handleIncomingFile(fileMsg);
                        scrollToBottom();
                    }),
                    // onGroupMessage
                    groupMsg -> Platform.runLater(() -> {
                        handleIncomingGroupMessage(groupMsg);
                        scrollToBottom();
                    }),
                    // onGroupDiscovered
                    group -> Platform.runLater(() -> {
                        if (groups.stream().noneMatch(g -> g.getName().equals(group.getName()))) {
                            groups.add(group);
                        }
                    })
            );

            // Enable UI
            messageInput.setDisable(false);
//            sendButton.setVisible(false);
            sendButton.setDisable(false);
            sendLikeButton.setDisable(false);
            fileButton.setDisable(false);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to start network: " + e.getMessage());
        }
    }

    // ============= FXML Event Handlers =============

    @FXML
    private void handleCreatePublicGroup() {
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
    }

    @FXML
    private void handleCreatePrivateGroup() {
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
    }

//    @FXML
//    private void handleJoinGroup() {
//        Group selected = groupListView.getSelectionModel().getSelectedItem();
//
//
//        if (selected == null) {
//            showAlert("Info", "Please select a group to join");
//            return;
//        }
//
//        if (selected.isJoined()) {
//            showAlert("Info", "You are already in this group");
//            return;
//        } else {
//            selected.setJoined(true);
//        }
//
//        if (selected.isPublic()) {
//            if (network.joinGroup(selected.getName(), null)) {
//                selected.setJoined(true);
//                groupListView.refresh();
//            } else {
//                showAlert("Error", "Failed to join group");
//            }
//        } else {
//            TextInputDialog dialog = new TextInputDialog();
//            dialog.setTitle("Join Private Group");
//            dialog.setHeaderText("Enter password for: " + selected.getName());
//
//            Optional<String> result = dialog.showAndWait();
//            result.ifPresent(password -> {
//                if (network.joinGroup(selected.getName(), password)) {
//                    selected.setJoined(true);
//                    groupListView.refresh();
//                } else {
//                    showAlert("Error", "Invalid password");
//                }
//            });
//        }
//    }

    @FXML
    private void handleSendMessage() {
        String msg = messageInput.getText().trim();
        if (msg.isEmpty()) return;

        if (currentChatUser != null) {
            VBox box = chatBoxes.get(currentChatUser.getNickname());
//            System.out.println( "bin nè" + currentChatUser.getNickname());
            messageRenderer.addMessage(box, currentChatUser.getNickname(), msg, true, false);
            network.sendMessage(currentChatUser, msg);
        } else if (currentChatGroup != null) {
            VBox box = groupChatBoxes.get(currentChatGroup.getName());
            messageRenderer.addMessage(box, "",  msg, true, false);
            network.sendGroupMessage(currentChatGroup.getName(), msg);
        }

        messageInput.clear();
        scrollToBottom();
    }


    private void setupMessageInputListener() {
        messageInput.textProperty().addListener((obs, oldText, newText) -> {
            boolean isEmpty = newText.trim().isEmpty();
            // ✅ Ẩn/hiện các nút dựa vào text
//            addButton.setVisible(isEmpty);
//            addButton.setManaged(isEmpty);

            fileButton.setVisible(isEmpty);
            fileButton.setManaged(isEmpty);

//            emojiButton.setVisible(isEmpty);
//            emojiButton.setManaged(isEmpty);

            sendLikeButton.setVisible(isEmpty);
            sendLikeButton.setManaged(isEmpty);

            sendButton.setVisible(!isEmpty);
            sendButton.setManaged(!isEmpty);

            // ✅ Auto resize height theo nội dung
            adjustTextAreaHeight();
        });

        // ✅ Bắt Enter để gửi (Shift+Enter để xuống dòng)
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });
    }

    private void adjustTextAreaHeight() {
        // ✅ Force update layout TRƯỚC KHI lấy width
        messageInput.applyCss();
        messageInput.layout();

        double availableWidth = messageInput.getWidth()
                - messageInput.getPadding().getLeft()
                - messageInput.getPadding().getRight()
                - 10;

        // Fallback nếu width chưa có
        if (availableWidth <= 10) {
            availableWidth = messageInput.getPrefWidth() - 20;
        }

        Text textNode = new Text(messageInput.getText());
        textNode.setFont(messageInput.getFont());
        textNode.setWrappingWidth(availableWidth);

        double textHeight = textNode.getLayoutBounds().getHeight();
        double newHeight = Math.min(Math.max(24, textHeight + 12), MAX_INPUT_HEIGHT);

        System.out.println("Width: " + availableWidth + " | TextHeight: " + textHeight + " | NewHeight: " + newHeight);

        messageInput.setMinHeight(newHeight);
        messageInput.setMaxHeight(MAX_INPUT_HEIGHT);
        messageInput.setPrefHeight(newHeight);
        textFieldContainer.setPrefHeight(newHeight + 10);
    }
    @FXML
    private void handleSendLikeButton() {
        String msg = "__LIKE_ICON__";

        if (currentChatUser != null) {
            VBox box = chatBoxes.get(currentChatUser.getNickname());
            messageRenderer.addMessage(box, "Me", msg, true, false);
            network.sendMessage(currentChatUser, msg);
        } else if (currentChatGroup != null) {
            VBox box = groupChatBoxes.get(currentChatGroup.getName());
            messageRenderer.addMessage(box, "Me", msg, true, false);
            network.sendGroupMessage(currentChatGroup.getName(), msg);
        }

        messageInput.clear();
        scrollToBottom();
    }

    @FXML
    private void handleSendFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");

        Stage stage = (Stage) fileButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            // Lấy đường dẫn file để hiển thị preview
            String filePath = file.getAbsolutePath();

            if (currentChatUser != null) {
                VBox box = chatBoxes.get(currentChatUser.getNickname());
                messageRenderer.addFileMessage(box, "Me", file.getName(), file.length(), filePath, true);
                network.sendFile(currentChatUser, filePath);
            } else if (currentChatGroup != null) {
                VBox box = groupChatBoxes.get(currentChatGroup.getName());
                // Truyền filePath thay vì null
                messageRenderer.addFileMessage(box, "Me", file.getName(), file.length(), filePath, true);
                network.sendGroupFile(currentChatGroup.getName(), filePath);
            }
            scrollToBottom();
        }
    }
    // ============= Setup Methods =============

    private void setupUserListCell() {
        userListView.setCellFactory(lv -> new ListCell<User>() {
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

                    if (user.getUnreadCount() > 0) {
                        String badgeText = user.getUnreadCount() > 9 ? "9+" : String.valueOf(user.getUnreadCount());
                        Label badge = new Label(badgeText);
                        badge.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                                "-fx-padding: 2 6 2 6; -fx-background-radius: 10; " +
                                "-fx-font-size: 10px; -fx-font-weight: bold;");
                        container.getChildren().add(badge);
                        setStyle("-fx-border-color: #ff4444; -fx-border-width: 0 0 0 3; " +
                                "-fx-background-color: #fff5f5;");
                    } else {
                        setStyle("");
                    }

                    setGraphic(container);
                    setText(null);
                }
            }
        });
    }

    private void setupGroupListCell() {
        groupListView.setCellFactory(lv -> new ListCell<Group>() {
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

                    if (group.isJoined() && group.getUnreadCount() > 0) {
                        String badgeText = group.getUnreadCount() > 9 ? "9+" : String.valueOf(group.getUnreadCount());
                        Label badge = new Label(badgeText);
                        badge.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                                "-fx-padding: 2 6 2 6; -fx-background-radius: 10; " +
                                "-fx-font-size: 10px; -fx-font-weight: bold;");
                        container.getChildren().add(badge);
                        setStyle("-fx-border-color: #ff4444; -fx-border-width: 0 0 0 3; " +
                                "-fx-background-color: #fff5f5;");
                    } else {
                        setStyle("");
                    }

                    setGraphic(container);
                    setText(null);
                }
            }
        });
    }

    private void setupUserSelectionListener() {
        userListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                User selected = userListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    currentChatUser = selected;
                    currentChatGroup = null;
                    groupListView.getSelectionModel().clearSelection();

                    selected.resetUnread();
                    userListView.refresh();

                    VBox box = chatBoxes.computeIfAbsent(selected.getNickname(), k -> new VBox(5));
                    box.setPadding(new Insets(10));
                    messagesBox = box;
                    chatScrollPane.setContent(messagesBox);

                    chatHeaderLabel.setText("Chat with " + selected.getNickname());
                    fileButton.setVisible(true);
                }
            }
        });
    }

    // Thay thế method setupGroupSelectionListener() hiện tại bằng code này:

    private void setupGroupSelectionListener() {
        groupListView.setOnMouseClicked(e -> {
            Group selected = groupListView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            if (e.getClickCount() == 2) {
                // Double-click
                if (selected.isJoined()) {
                    // Đã join rồi -> mở chat (giống single-click)
                    openExistingGroupChat(selected);
                } else {
                    // Chưa join -> hiện confirm dialog để join
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmDialog.setTitle("Join Group");
                    confirmDialog.setHeaderText("Join group: " + selected.getName());
                    confirmDialog.setContentText("Do you want to join this " +
                            (selected.isPublic() ? "public" : "private") + " group?");

                    Optional<ButtonType> result = confirmDialog.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        joinGroupAndOpen(selected);
                    }
                }
            } else if (e.getClickCount() == 1) {
                // Single-click: Chỉ mở chat nếu đã join
                if (selected.isJoined()) {
                    openExistingGroupChat(selected);
                }
            }
        });
    }

// Thêm 2 methods helper này vào MainController:

    // Helper method để mở chat của group đã join (tránh duplicate code)
    private void openExistingGroupChat(Group selected) {
        currentChatGroup = selected;
        currentChatUser = null;
        userListView.getSelectionModel().clearSelection();

        selected.resetUnread();
        groupListView.refresh();

        VBox box = groupChatBoxes.computeIfAbsent(selected.getName(), k -> new VBox(5));
        box.setPadding(new Insets(10));
        messagesBox = box;
        chatScrollPane.setContent(messagesBox);

        String type = selected.isPublic() ? "Public" : "Private";
        chatHeaderLabel.setText(type + " Group: " + selected.getName());
        fileButton.setVisible(true);

        scrollToBottom();
    }

    // Method để join group và mở chat sau khi join thành công
    private void joinGroupAndOpen(Group group) {
        if (group.isPublic()) {
            // Public group - join trực tiếp
            if (network.joinGroup(group.getName(), null)) {
                group.setJoined(true);
                groupListView.refresh();
                openExistingGroupChat(group);
            } else {
                showAlert("Error", "Failed to join group");
            }
        } else {
            // Private group - yêu cầu password
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Join Private Group");
            dialog.setHeaderText("Enter password for: " + group.getName());

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(password -> {
                // ✅ CHỈ join và mở chat khi password đúng
                if (!password.trim().isEmpty()) {
                    if (network.joinGroup(group.getName(), password)) {
                        group.setJoined(true);
                        groupListView.refresh();
                        openExistingGroupChat(group);
                    } else {
                        // ❌ Password sai -> KHÔNG set joined, hiện lỗi
                        showAlert("Error", "Invalid password. Access denied.");
                    }
                }
            });
        }
    }

    // ============= Message Handlers =============

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
                box.setPadding(new Insets(20));
                messageRenderer.addMessage(box,sender.getNickname(), message, false, false);
                System.out.println("heeee" + message);

                if (currentChatUser == null || !currentChatUser.getNickname().equals(senderName)) {
                    sender.incrementUnread();
                    userListView.refresh();
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
        messageRenderer.addMessage(box, groupMsg.getSender(), groupMsg.getContent(), false, false);

        Group group = groups.stream()
                .filter(g -> g.getName().equals(groupMsg.getGroupName()))
                .findFirst()
                .orElse(null);

        if (group != null && (currentChatGroup == null || !currentChatGroup.getName().equals(groupMsg.getGroupName()))) {
            group.incrementUnread();
            groupListView.refresh();
        }

        if (currentChatGroup != null && currentChatGroup.getName().equals(groupMsg.getGroupName())) {
            messagesBox = box;
            chatScrollPane.setContent(messagesBox);
            scrollToBottom();
        }
    }

    private void handleIncomingFile(FileMessage fileMsg) {
        if (fileMsg.isGroupMessage()) {
            handleGroupFile(fileMsg);
        } else {
            handlePrivateFile(fileMsg);
        }
    }

    private void handlePrivateFile(FileMessage fileMsg) {
        User sender = users.stream()
                .filter(u -> u.getNickname().equals(fileMsg.getSender()))
                .findFirst()
                .orElse(null);

        if (sender != null) {
            VBox box = chatBoxes.computeIfAbsent(sender.getNickname(), k -> new VBox(5));
            box.setPadding(new Insets(10));
            messageRenderer.addFileMessage(box, fileMsg.getSender(), fileMsg.getFileName(),
                    fileMsg.getFileSize(), fileMsg.getFilePath(), false);

            if (currentChatUser == null || !currentChatUser.getNickname().equals(sender.getNickname())) {
                sender.incrementUnread();
                userListView.refresh();
            }

            if (currentChatUser != null && currentChatUser.getNickname().equals(sender.getNickname())) {
                messagesBox = box;
                chatScrollPane.setContent(messagesBox);
                scrollToBottom();
            }

//            fileHelper.showFileReceivedNotification(fileMsg);
        }
    }

    private void handleGroupFile(FileMessage fileMsg) {
        VBox box = groupChatBoxes.computeIfAbsent(fileMsg.getGroupName(), k -> new VBox(5));
        box.setPadding(new Insets(10));
        messageRenderer.addFileMessage(box, fileMsg.getSender(), fileMsg.getFileName(),
                fileMsg.getFileSize(), fileMsg.getFilePath(), false);

        Group group = groups.stream()
                .filter(g -> g.getName().equals(fileMsg.getGroupName()))
                .findFirst()
                .orElse(null);

        if (group != null && (currentChatGroup == null || !currentChatGroup.getName().equals(fileMsg.getGroupName()))) {
            group.incrementUnread();
            groupListView.refresh();
        }

        if (currentChatGroup != null && currentChatGroup.getName().equals(fileMsg.getGroupName())) {
            messagesBox = box;
            chatScrollPane.setContent(messagesBox);
            scrollToBottom();
        }

//        fileHelper.showGroupFileReceivedNotification(fileMsg);
    }

    // ============= Utility Methods =============

    private void scrollToBottom() {
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void shutdown() {
        if (network != null) {
            network.broadcastOffline();
        }
    }
}