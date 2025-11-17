package org.example.chatft.ui.controller;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.example.chatft.model.User;

public class UserCell extends ListCell<User> {

    private HBox content;
    private StackPane avatarContainer;
    private Circle avatar;
    private Label initials;
    private Label nameLabel;
    private Label badge;

    public UserCell() {
        avatar = new Circle(20);

        initials = new Label();
        initials.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        avatarContainer = new StackPane(avatar, initials);
        avatarContainer.setAlignment(Pos.CENTER);
        avatarContainer.setPrefSize(40, 40);

        nameLabel = new Label();
        nameLabel.setStyle("-fx-font-size: 14px;");
        nameLabel.setMaxWidth(140);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        badge = new Label();
        badge.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; " +
                "-fx-padding: 2 6 2 6; -fx-background-radius: 10; " +
                "-fx-font-size: 10px; -fx-font-weight: bold;");
        badge.setManaged(false);
        badge.setVisible(false);

        content = new HBox(12, avatarContainer, nameLabel, badge);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setStyle("-fx-padding: 8 12;");
    }

    @Override
    protected void updateItem(User user, boolean empty) {
        super.updateItem(user, empty);

        if (empty || user == null) {
            setGraphic(null);
            setStyle("");
        } else {
            String initial = user.getNickname().substring(0, 1).toUpperCase();
            initials.setText(initial);

            // ✅ Set màu đơn sắc
            Color color = getColorForUser(user.getNickname());
            avatar.setFill(color);

            nameLabel.setText(user.getNickname());

            if (user.getUnreadCount() > 0) {
                String badgeText = user.getUnreadCount() > 9 ? "9+" : String.valueOf(user.getUnreadCount());
                badge.setText(badgeText);
                badge.setManaged(true);
                badge.setVisible(true);
                setStyle("-fx-border-color: #ff4444; -fx-border-width: 0 0 0 3; " +
                        "-fx-background-color: #fff5f5;");
            } else {
                badge.setManaged(false);
                badge.setVisible(false);
                setStyle("");
            }

            setGraphic(content);
        }
    }

    private Color getColorForUser(String name) {
        // ✅ Palette màu của bạn
        String[] colors = {
                "#6C77E1",  // Xanh tím
                "#92B9E3",  // Xanh nhạt
                "#FFC4A4",  // Cam nhạt
                "#FBA2D0",  // Hồng
                "#956AD6",  // Tím
                "#F17988",  // Đỏ hồng
//                "#EBDFEB",  // Tím nhạt
                "#F08074",  // Cam đỏ
                "#70C2B4"   // Xanh ngọc
        };

        // Chọn màu dựa vào hash của tên (consistent)
        int index = Math.abs(name.hashCode()) % colors.length;
        return Color.web(colors[index]);
    }
}