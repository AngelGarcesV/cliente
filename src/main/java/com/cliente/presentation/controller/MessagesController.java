package com.cliente.presentation.controller;

import com.cliente.application.service.MessageService;
import com.cliente.domain.model.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

public class MessagesController {

    @FXML private VBox messagesContainer;
    @FXML private ScrollPane scrollPane;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    @FXML
    public void initialize() {
        scrollPane.setFitToWidth(true);
        loadMessages();
    }

    @FXML
    private void handleRefresh() {
        loadMessages();
    }

    private void loadMessages() {
        statusLabel.setText("Cargando mensajes...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Message> messages = MessageService.getInstance().getMessages();
                Platform.runLater(() -> {
                    messagesContainer.getChildren().clear();
                    for (Message m : messages) {
                        messagesContainer.getChildren().add(buildMessageCard(m));
                    }
                    statusLabel.setText(messages.size() + " mensaje(s) recibido(s)");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "messages-loader").start();
    }

    private VBox buildMessageCard(Message message) {
        VBox card = new VBox(6);
        card.getStyleClass().add("message-card");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label clientLabel = new Label(message.getClientId() != null ? message.getClientId() : "—");
        clientLabel.getStyleClass().add("message-client-id");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timestampLabel = new Label(message.getTimestamp() != null ? message.getTimestamp() : "");
        timestampLabel.getStyleClass().add("message-timestamp");

        header.getChildren().addAll(clientLabel, spacer, timestampLabel);

        Label contentLabel = new Label(message.getContent() != null ? message.getContent() : "");
        contentLabel.getStyleClass().add("message-content");
        contentLabel.setWrapText(true);

        card.getChildren().addAll(header, contentLabel);
        return card;
    }
}
