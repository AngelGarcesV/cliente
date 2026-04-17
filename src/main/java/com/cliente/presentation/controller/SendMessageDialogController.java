package com.cliente.presentation.controller;

import com.cliente.application.service.MessageService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SendMessageDialogController {

    @FXML private TextArea messageArea;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        statusLabel.setVisible(false);
        messageArea.textProperty().addListener((obs, old, newVal) ->
            sendButton.setDisable(newVal.isBlank()));
        sendButton.setDisable(true);
    }

    @FXML
    private void handleSend() {
        String content = messageArea.getText().trim();
        if (content.isEmpty()) return;

        sendButton.setDisable(true);
        statusLabel.setVisible(true);
        statusLabel.setText("Enviando...");

        new Thread(() -> {
            try {
                MessageService.getInstance().sendMessage(content);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Mensaje enviado correctamente.");
                    messageArea.clear();
                    sendButton.setDisable(false);
                    // close after short delay
                    new Thread(() -> {
                        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                        javafx.application.Platform.runLater(this::handleCancel);
                    }).start();
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    sendButton.setDisable(false);
                });
            }
        }, "send-message-thread").start();
    }

    @FXML
    private void handleCancel() {
        ((Stage) sendButton.getScene().getWindow()).close();
    }
}
