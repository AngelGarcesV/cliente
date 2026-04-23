package com.cliente.presentation.controller;

import com.cliente.application.service.MessageService;
import com.cliente.domain.model.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MessagesController {

    @FXML private VBox messagesContainer;
    @FXML private ScrollPane scrollPane;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    private final List<Message> currentMessages = new ArrayList<>();

    @FXML
    public void initialize() {
        scrollPane.setFitToWidth(true);
        loadMessages();
    }

    @FXML
    private void handleRefresh() {
        loadMessages();
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Mensajes");
        chooser.setInitialFileName("mensajes.csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(messagesContainer.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Cliente,Contenido,Timestamp");
            for (Message m : currentMessages)
                pw.printf("\"%s\",\"%s\",\"%s\"%n",
                    m.getClientId() != null ? m.getClientId() : "",
                    m.getContent()  != null ? m.getContent().replace("\"", "\"\"") : "",
                    m.getTimestamp() != null ? m.getTimestamp() : "");
            new Alert(Alert.AlertType.INFORMATION, "Mensajes exportados correctamente.").showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error al exportar: " + ex.getMessage()).showAndWait();
        }
    }

    private void loadMessages() {
        statusLabel.setText("Cargando mensajes...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Message> messages = MessageService.getInstance().getMessages();
                Platform.runLater(() -> {
                    currentMessages.clear();
                    currentMessages.addAll(messages);
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

        // ── Header: cliente + badge origen + timestamp ──
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label clientLabel = new Label(message.getClientId() != null ? message.getClientId() : "—");
        clientLabel.getStyleClass().add("message-client-id");

        String origenTexto = resolverTextoOrigen(message);
        Label origenLabel = new Label(origenTexto);
        origenLabel.getStyleClass().add(
            "LOCAL".equals(message.getOrigen()) ? "badge-local" : "badge-externo");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timestampLabel = new Label(message.getTimestamp() != null ? message.getTimestamp() : "");
        timestampLabel.getStyleClass().add("message-timestamp");

        header.getChildren().addAll(clientLabel, origenLabel, spacer, timestampLabel);

        // ── Contenido del mensaje ──
        Label contentLabel = new Label(message.getContent() != null ? message.getContent() : "");
        contentLabel.getStyleClass().add("message-content");
        contentLabel.setWrapText(true);

        // ── SHA-256 completo (seleccionable, con wrap) ──
        String hash = message.getHashSha256();
        Label hashLabel = new Label("SHA-256: " + (hash != null && !hash.isBlank() ? hash : "—"));
        hashLabel.getStyleClass().add("message-hash");
        hashLabel.setWrapText(true);

        // ── Contenido cifrado (seleccionable, con wrap) ──
        String cifrado = message.getContenidoCifrado();
        Label cifradoLabel = new Label("Cifrado: " + (cifrado != null && !cifrado.isBlank() ? cifrado : "—"));
        cifradoLabel.getStyleClass().add("message-hash");
        cifradoLabel.setWrapText(true);

        card.getChildren().addAll(header, contentLabel, hashLabel, cifradoLabel);
        return card;
    }

    /**
     * Si el origen es EXTERNO, muestra la IP entre paréntesis.
     * Si es LOCAL, solo muestra "LOCAL".
     */
    private String resolverTextoOrigen(Message message) {
        String origen = message.getOrigen();
        if (origen == null) return "";
        if ("EXTERNO".equals(origen)) {
            String ip = message.getIpRemitente();
            return (ip != null && !ip.isBlank()) ? "EXTERNO (" + ip + ")" : "EXTERNO";
        }
        return origen;
    }
}
