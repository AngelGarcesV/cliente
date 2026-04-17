package com.cliente.presentation.controller;

import com.cliente.application.service.LogService;
import com.cliente.domain.model.LogEntry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.List;

public class LogsController {

    @FXML private TableView<LogEntry> logsTable;
    @FXML private TableColumn<LogEntry, String> colDate;
    @FXML private TableColumn<LogEntry, String> colTime;
    @FXML private TableColumn<LogEntry, String> colDescription;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        logsTable.setPlaceholder(new Label("No hay registros de log disponibles."));
        colDescription.prefWidthProperty().bind(
            logsTable.widthProperty().subtract(colDate.getWidth() + colTime.getWidth() + 20));

        loadLogs();
    }

    @FXML
    private void handleRefresh() {
        loadLogs();
    }

    @FXML
    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar Logs");
        chooser.setInitialFileName("logs_sistema.csv");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(logsTable.getScene().getWindow());
        if (file == null) return;

        List<LogEntry> items = logsTable.getItems();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Fecha,Hora,Descripción");
            for (LogEntry e : items)
                pw.printf("\"%s\",\"%s\",\"%s\"%n", e.getDate(), e.getTime(), e.getDescription());
            new Alert(Alert.AlertType.INFORMATION, "Logs exportados correctamente.").showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Error al exportar: " + ex.getMessage()).showAndWait();
        }
    }

    private void loadLogs() {
        statusLabel.setText("Cargando logs...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<LogEntry> entries = LogService.getInstance().getLogs();
                Platform.runLater(() -> {
                    logsTable.setItems(FXCollections.observableArrayList(entries));
                    statusLabel.setText(entries.size() + " registro(s) de log");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "logs-loader").start();
    }
}
