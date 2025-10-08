module org.example.chatft {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;

    opens org.example.chatft to javafx.fxml;
    exports org.example.chatft;
}