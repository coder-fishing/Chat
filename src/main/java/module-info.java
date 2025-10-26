module org.example.chatft {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires webrtc.java;

    // Cho phép JavaFX truy cập các class controller / view bằng reflection
    opens org.example.chatft.ui.controller to javafx.fxml;
    opens org.example.chatft.ui.view to javafx.fxml;
    opens org.example.chatft.model to javafx.fxml;
    opens org.example.chatft.view to javafx.graphics, javafx.fxml;  // 👈 thêm dòng này

    // Cho phép export (các module khác có thể import)
    exports org.example.chatft;
    exports org.example.chatft.ui.controller;

    exports org.example.chatft.model;
    exports org.example.chatft.service;
    exports org.example.chatft.view; // 👈 và dòng này
}
