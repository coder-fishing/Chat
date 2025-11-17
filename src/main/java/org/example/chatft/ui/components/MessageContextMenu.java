package org.example.chatft.ui.components;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.chatft.service.APIService;

public class MessageContextMenu {

    private ContextMenu contextMenu;
    private APIService translationService;
    private ResultPopup resultPopup;

    public MessageContextMenu() {
        this.translationService = new APIService();
        this.contextMenu = new ContextMenu();
        this.resultPopup = new ResultPopup();
    }

    public void show(Window owner, double x, double y, String selectedText) {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }

        contextMenu.getItems().clear();

        MenuItem translateItem = new MenuItem("🌐 Translate");
        translateItem.setOnAction(e -> {
            contextMenu.hide();
            translate((Stage) owner, x, y, selectedText);
        });

        MenuItem summarizeItem = new MenuItem("📝 Summarize");
        summarizeItem.setOnAction(e -> {
            contextMenu.hide();
            summarize((Stage) owner, x, y, selectedText);
        });

        MenuItem bothItem = new MenuItem("🌐📝 Both");
        bothItem.setOnAction(e -> {
            contextMenu.hide();
            translateAndSummarize((Stage) owner, x, y, selectedText);
        });

        contextMenu.getItems().addAll(translateItem, summarizeItem, bothItem);
        contextMenu.setAutoHide(true);
        contextMenu.show(owner, x, y);
    }

    public void hide() {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }
        resultPopup.hide();
    }

    private void translate(Stage owner, double x, double y, String text) {
        resultPopup.showLoading(owner, x, y - 50, "Translating...");

        new Thread(() -> {
            try {
                String result = translationService.translate(text, "en_XX", "vi_VN");
                Platform.runLater(() -> {
                    resultPopup.showResult("Translation", result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultPopup.showError("Translation failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void summarize(Stage owner, double x, double y, String text) {
        resultPopup.showLoading(owner, x, y - 50, "Summarizing...");

        new Thread(() -> {
            try {
                String result = translationService.summarize(text);
                Platform.runLater(() -> {
                    resultPopup.showResult("Summary", result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultPopup.showError("Summarization failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void translateAndSummarize(Stage owner, double x, double y, String text) {
        resultPopup.showLoading(owner, x, y - 50, "Processing...");

        new Thread(() -> {
            try {
                String[] results = translationService.translateAndSummarize(text, "en_XX", "vi_VN");
                Platform.runLater(() -> {
                    resultPopup.showResult("Result",
                            "Translation: " + results[0] + "\n\nSummary: " + results[1]);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resultPopup.showError("Process failed: " + e.getMessage());
                });
            }
        }).start();
    }
}