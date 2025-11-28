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
import org.example.chatft.ui.util.DebounceUtil;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MainController {

    // FXML injected components
    @FXML private TextField searchField;
    @FXML private ListView<User> userListView;
    @FXML private TabPane leftTabPane; // Main tab pane (Messages/Groups)
    @FXML private TabPane groupTabPane; // Sub-tab pane (Public/Private)
    @FXML private ListView<Group> publicGroupListView;
    @FXML private ListView<Group> privateGroupListView;
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
    @FXML private Button videoCallButton;
    @FXML private ContextMenu contextMenu;
//    @FXML private Button  emojiButton, addButton;
    @FXML private HBox textFieldContainer;
    private static final int MAX_INPUT_HEIGHT = 120;

    // Data
    private ObservableList<User> allUsers = FXCollections.observableArrayList();
    private ObservableList<User> users = FXCollections.observableArrayList();
    private ObservableList<User> filteredUsers = FXCollections.observableArrayList();
    private ObservableList<Group> groups = FXCollections.observableArrayList(); // All groups
    private ObservableList<Group> publicGroups = FXCollections.observableArrayList();
    private ObservableList<Group> privateGroups = FXCollections.observableArrayList();
    private ObservableList<Group> filteredPublicGroups = FXCollections.observableArrayList();
    private ObservableList<Group> filteredPrivateGroups = FXCollections.observableArrayList();
    private Map<String, VBox> chatBoxes = new HashMap<>();
    private Map<String, VBox> groupChatBoxes = new HashMap<>();

    // State
    private User currentChatUser;
    private Group currentChatGroup;
    private NetworkService network;
    private String nickname;
    private VideoCallController activeVideoCallController;

    // Utilities
    private MessageRenderer messageRenderer;
    private FileHelper fileHelper;
    private DebounceUtil searchDebounce = new DebounceUtil(300);

    @FXML
    private void initialize() {
        messageRenderer = new MessageRenderer(chatScrollPane);
        fileHelper = new FileHelper();

        // Ẩn hết chat-related controls
        fileButton.setDisable(true);
        sendButton.setDisable(true);
        sendLikeButton.setDisable(true);
        messageInput.setDisable(true);
        videoCallButton.setDisable(true);
//        sendButton.setVisible(true);

        // Khởi tạo ListView
        userListView.setItems(allUsers);
        
        // Initialize group ListViews
        publicGroupListView.setItems(publicGroups);
        privateGroupListView.setItems(privateGroups);
        
        // Setup cell factories
        userListView.setCellFactory(lv -> new UserCell());
        setupGroupListCell();

        setupUserSelectionListener();
        setupPublicGroupSelectionListener();
        setupPrivateGroupSelectionListener();
        setupMessageInputListener();
        setupSearchListener();
        setupTabChangeListeners();
    }

    public void initializeNetwork(String nickname) {
        this.nickname = nickname;
        System.out.println("[MAIN-DEBUG] My nickname: " + nickname);

        try {
            network = new NetworkService(
                    nickname,
                    // onUserOnline
                    user -> Platform.runLater(() -> {
                        if (allUsers.stream().noneMatch(u -> u.getNickname().equals(user.getNickname()))) {
                            allUsers.add(user);
                            // Nếu đang search, trigger lại filter
                            if (!searchField.getText().trim().isEmpty()) {
                                filterUsers(searchField.getText().trim().toLowerCase());
                            }
                        }
                    }),
                    // onUserOffline
                    user -> Platform.runLater(() -> {
                        System.out.println("[UI] onUserOffline callback: removing " + user.getNickname());
                        System.out.println("[UI] allUsers size before: " + allUsers.size());
                        boolean removedFromAll = allUsers.removeIf(u -> u.getNickname().equals(user.getNickname()));
                        boolean removedFromFiltered = filteredUsers.removeIf(u -> u.getNickname().equals(user.getNickname()));
                        System.out.println("[UI] Removed from allUsers: " + removedFromAll + ", from filteredUsers: " + removedFromFiltered);
                        System.out.println("[UI] allUsers size after: " + allUsers.size());
                    }),
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
                            // Route to correct list based on public/private
                            if (group.isPublic()) {
                                if (publicGroups.stream().noneMatch(g -> g.getName().equals(group.getName()))) {
                                    publicGroups.add(group);
                                }
                            } else {
                                if (privateGroups.stream().noneMatch(g -> g.getName().equals(group.getName()))) {
                                    privateGroups.add(group);
                                }
                            }
                        }
                    })
            );

            // Enable UI
            messageInput.setDisable(false);
//            sendButton.setVisible(false);
            sendButton.setDisable(false);
            sendLikeButton.setDisable(false);
            fileButton.setDisable(false);
            videoCallButton.setDisable(false);
            
            // Setup video call callbacks
            setupVideoCallHandlers();

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
        
        // Apply custom CSS
        applyDialogStyles(dialog.getDialogPane());

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(groupName -> {
            if (!groupName.trim().isEmpty()) {
                Group newGroup = new Group(groupName.trim(), true, null);
                newGroup.setJoined(true);
                if (groups.stream().noneMatch(g -> g.getName().equals(newGroup.getName()))) {
                    groups.add(newGroup);
                    publicGroups.add(newGroup); // Add to public groups list
                    network.createPublicGroup(groupName.trim());
                } else {
                    // Show styled alert when group name already exists
                    showStyledAlert(
                        Alert.AlertType.WARNING,
                        "Duplicate Group Name",
                        "Group already exists",
                        "A group with the name '" + groupName.trim() + "' already exists. Please choose a different name."
                    );
                }
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
        
        // Apply custom CSS
        applyDialogStyles(dialog.getDialogPane());

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
                    privateGroups.add(newGroup); // Add to private groups list
                    network.createPrivateGroup(data[0].trim(), data[1].trim());
                } else {
                    // Show styled alert when group name already exists
                    showStyledAlert(
                        Alert.AlertType.WARNING,
                        "Duplicate Group Name",
                        "Group already exists",
                        "A group with the name '" + data[0].trim() + "' already exists. Please choose a different name."
                    );
                }
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


    private void setupSearchListener() {
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            searchDebounce.debounce(() -> performSearch(newText.trim().toLowerCase()));
        });
    }

    private void setupTabChangeListeners() {
        // Listen to main tab changes (Messages/Groups)
        leftTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            // Clear search when switching tabs
            String currentSearch = searchField.getText().trim().toLowerCase();
            if (!currentSearch.isEmpty()) {
                performSearch(currentSearch);
            }
        });

        // Listen to group sub-tab changes (Public/Private)
        groupTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            // Re-apply search when switching between Public/Private
            String currentSearch = searchField.getText().trim().toLowerCase();
            if (!currentSearch.isEmpty()) {
                performSearch(currentSearch);
            }
        });
    }

    private void performSearch(String keyword) {
        // Check which main tab is active
        int mainTabIndex = leftTabPane.getSelectionModel().getSelectedIndex();
        
        if (mainTabIndex == 0) {
            // Messages tab - search users
            filterUsers(keyword);
        } else if (mainTabIndex == 1) {
            // Groups tab - check which sub-tab is active
            int groupTabIndex = groupTabPane.getSelectionModel().getSelectedIndex();
            if (groupTabIndex == 0) {
                // Public Groups tab
                filterPublicGroups(keyword);
            } else if (groupTabIndex == 1) {
                // Private Groups tab
                filterPrivateGroups(keyword);
            }
        }
    }

    private void filterPublicGroups(String keyword) {
        if (keyword.isEmpty()) {
            publicGroupListView.setItems(publicGroups);
        } else {
            filteredPublicGroups.clear();
            publicGroups.stream()
                    .filter(group -> group.getName().toLowerCase().contains(keyword))
                    .forEach(filteredPublicGroups::add);
            publicGroupListView.setItems(filteredPublicGroups);
        }
    }

    private void filterPrivateGroups(String keyword) {
        if (keyword.isEmpty()) {
            privateGroupListView.setItems(privateGroups);
        } else {
            filteredPrivateGroups.clear();
            privateGroups.stream()
                    .filter(group -> group.getName().toLowerCase().contains(keyword))
                    .forEach(filteredPrivateGroups::add);
            privateGroupListView.setItems(filteredPrivateGroups);
        }
    }

    private void filterUsers(String keyword) {
        if (keyword.isEmpty()) {
            // Không có từ khóa -> hiển thị tất cả
            userListView.setItems(allUsers);
        } else {
            // Lọc users theo tên
            filteredUsers.clear();
            allUsers.stream()
                    .filter(user -> user.getNickname().toLowerCase().contains(keyword))
                    .forEach(filteredUsers::add);
            userListView.setItems(filteredUsers);
        }
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
        // Setup cell factory for public groups
        publicGroupListView.setCellFactory(lv -> new ListCell<Group>() {
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

        // Setup cell factory for private groups (same logic)
        privateGroupListView.setCellFactory(lv -> new ListCell<Group>() {
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
                    publicGroupListView.getSelectionModel().clearSelection();
                    privateGroupListView.getSelectionModel().clearSelection();

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

    private void setupPublicGroupSelectionListener() {
        publicGroupListView.setOnMouseClicked(e -> {
            Group selected = publicGroupListView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            // Right-click for context menu
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && selected.isJoined()) {
                showGroupContextMenu(selected, e.getScreenX(), e.getScreenY());
                return;
            }

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
                    confirmDialog.setContentText("Do you want to join this public group?");

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

    private void setupPrivateGroupSelectionListener() {
        privateGroupListView.setOnMouseClicked(e -> {
            Group selected = privateGroupListView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            // Right-click for context menu
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && selected.isJoined()) {
                showGroupContextMenu(selected, e.getScreenX(), e.getScreenY());
                return;
            }

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
                    confirmDialog.setContentText("Do you want to join this private group?");

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

    private void setupGroupSelectionListener() {
        // Deprecated - keeping for backward compatibility
        // Now using setupPublicGroupSelectionListener and setupPrivateGroupSelectionListener
    }

    private void showGroupContextMenu(Group group, double x, double y) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem leaveItem = new MenuItem("Leave Group");
        leaveItem.setOnAction(e -> handleLeaveGroup(group));
        
        contextMenu.getItems().add(leaveItem);
        contextMenu.show(publicGroupListView.getScene().getWindow(), x, y);
    }

    private void handleLeaveGroup(Group group) {
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Leave Group");
        confirmDialog.setHeaderText("Leave group: " + group.getName());
        confirmDialog.setContentText("Are you sure you want to leave this group?");
        
        applyDialogStyles(confirmDialog.getDialogPane());
        
        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Leave group via network
            network.leaveGroup(group.getName());
            
            // Update UI - set joined to false (keep group in list)
            group.setJoined(false);
            group.resetUnread();
            
            // Refresh ListViews to show updated state
            publicGroupListView.refresh();
            privateGroupListView.refresh();
            
            // Close chat if currently viewing this group
            if (currentChatGroup != null && currentChatGroup.getName().equals(group.getName())) {
                currentChatGroup = null;
                messagesBox.getChildren().clear();
                chatHeaderLabel.setText("Select a conversation");
            }
            
            // Clear chat history
            groupChatBoxes.remove(group.getName());
            
            System.out.println("[UI] Left group: " + group.getName());
        }
    }

// Thêm 2 methods helper này vào MainController:

    // Helper method để mở chat của group đã join (tránh duplicate code)
    private void openExistingGroupChat(Group selected) {
        currentChatGroup = selected;
        currentChatUser = null;
        userListView.getSelectionModel().clearSelection();

        selected.resetUnread();
        publicGroupListView.refresh();
        privateGroupListView.refresh();

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
                publicGroupListView.refresh();
                privateGroupListView.refresh();
                openExistingGroupChat(group);
            } else {
                showAlert("Error", "Failed to join group");
            }
        } else {
            // Private group - yêu cầu password
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Join Private Group");
            dialog.setHeaderText("Enter password for: " + group.getName());
            
            // Apply custom CSS
            applyDialogStyles(dialog.getDialogPane());

            Optional<String> result = dialog.showAndWait();
            result.ifPresent(password -> {
                // ✅ CHỈ join và mở chat khi password đúng
                if (!password.trim().isEmpty()) {
                    if (network.joinGroup(group.getName(), password)) {
                        group.setJoined(true);
                        publicGroupListView.refresh();
                        privateGroupListView.refresh();
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

            User sender = allUsers.stream()
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
        
        // Check if this is a system message
        boolean isSystemMessage = "__SYSTEM__".equals(groupMsg.getSender());
        messageRenderer.addMessage(box, groupMsg.getSender(), groupMsg.getContent(), false, isSystemMessage);

        Group group = groups.stream()
                .filter(g -> g.getName().equals(groupMsg.getGroupName()))
                .findFirst()
                .orElse(null);

        if (group != null && (currentChatGroup == null || !currentChatGroup.getName().equals(groupMsg.getGroupName()))) {
            group.incrementUnread();
            publicGroupListView.refresh();
            privateGroupListView.refresh();
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
        User sender = allUsers.stream()
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
            publicGroupListView.refresh();
            privateGroupListView.refresh();
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
        System.out.println("========================================");
        System.out.println("MAIN-CONTROLLER: Shutdown called for user: " + nickname);
        System.out.println("========================================");
        
        if (network != null) {
            System.out.println("Network exists, broadcasting offline to all users...");
            network.broadcastOffline();
            System.out.println("Offline broadcast complete");
        } else {
            System.out.println("ERROR: Network is null - cannot notify offline!");
        }
    }
    
    // ============= Video Call Methods =============
    
    private void setupVideoCallHandlers() {
        network.setOnIncomingVideoCall(this::handleIncomingVideoCall);
        
        network.setOnSdpOffer(parts -> {
            String fromNickname = parts[1];
            String sdp = parts[2];
            if (activeVideoCallController != null) {
                activeVideoCallController.handleIncomingSdpOffer(sdp);
            }
        });
        
        network.setOnSdpAnswer(parts -> {
            String fromNickname = parts[1];
            String sdp = parts[2];
            if (activeVideoCallController != null) {
                activeVideoCallController.handleIncomingSdpAnswer(sdp);
            }
        });
        
        network.setOnIceCandidate(parts -> {
            String fromNickname = parts[1];
            String candidate = parts[2];
            if (activeVideoCallController != null) {
                activeVideoCallController.handleIncomingIceCandidate(candidate);
            }
        });
        
        network.setOnCallEnd(fromNickname -> {
            if (activeVideoCallController != null) {
                activeVideoCallController.getVideoCallService().endCall();
                activeVideoCallController = null;
            }
        });
        
        // Setup video frame receiver
        network.setOnVideoFrame(videoFrame -> {
            if (activeVideoCallController != null) {
                activeVideoCallController.getVideoCallService().handleIncomingVideoFrame(videoFrame.frameData);
            }
        });
    }
    
    @FXML
    private void handleVideoCall() {
        if (currentChatUser == null) {
            showAlert("Info", "Please select a user to call");
            return;
        }
        
        openVideoCallWindow(currentChatUser, true);
    }
    
    private void handleIncomingVideoCall(User caller) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Incoming Video Call");
        alert.setHeaderText("Video call from " + caller.getNickname());
        alert.setContentText("Do you want to accept this call?");
        
        ButtonType acceptButton = new ButtonType("Accept");
        ButtonType rejectButton = new ButtonType("Reject", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(acceptButton, rejectButton);
        
        alert.showAndWait().ifPresent(response -> {
            if (response == acceptButton) {
                openVideoCallWindow(caller, false);
            } else {
                System.out.println("[VIDEO-UI] Call rejected");
            }
        });
    }
    
    private void openVideoCallWindow(User remoteUser, boolean isCaller) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/org/example/chatft/ui/view/video-call.fxml")
            );
            
            javafx.scene.Parent root = loader.load();
            
            VideoCallController controller = loader.getController();
            controller.initialize(remoteUser, network, isCaller);
            this.activeVideoCallController = controller;
            
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Video Call - " + remoteUser.getNickname());
            stage.setScene(new javafx.scene.Scene(root));
            stage.setOnCloseRequest(e -> {
                controller.getVideoCallService().endCall();
                activeVideoCallController = null;
            });
            stage.show();
            
            if (isCaller) {
                network.sendVideoCallRequest(remoteUser);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open video call window: " + e.getMessage());
        }
    }

    // ============= Styled Alert & Dialog Helpers =============
    
    private void applyDialogStyles(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(
            getClass().getResource("/org/example/chatft/ui/view/style/alert.css").toExternalForm()
        );
    }
    
    private void showStyledAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        // Apply custom CSS
        DialogPane dialogPane = alert.getDialogPane();
        applyDialogStyles(dialogPane);
        
        // Add type-specific style class
        String styleClass = switch (type) {
            case WARNING -> "warning";
            case ERROR -> "error";
            case INFORMATION -> "info";
            case CONFIRMATION -> "success";
            default -> "";
        };
        
        if (!styleClass.isEmpty()) {
            dialogPane.getStyleClass().add(styleClass);
        }
        
        alert.showAndWait();
    }
}