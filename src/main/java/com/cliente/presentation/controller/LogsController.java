package com.cliente.presentation.controller;

import com.cliente.application.service.LogService;
import com.cliente.domain.model.LogEntry;
import com.cliente.domain.model.PaginatedResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    private int paginaActual = 0;
    private int tamanoPagina = 50;
    private int totalPaginas = 1;

    private Button btnAnterior;
    private Button btnSiguiente;
    private Label pageInfoLabel;

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        logsTable.setPlaceholder(new Label("No hay registros de log disponibles."));
        colDescription.prefWidthProperty().bind(
            logsTable.widthProperty().subtract(colDate.getWidth() + colTime.getWidth() + 20));

        addPaginationBar();
        loadLogs();
    }

    private void addPaginationBar() {
        btnAnterior = new Button("← Anterior");
        btnSiguiente = new Button("Siguiente →");
        pageInfoLabel = new Label("Página 1 de 1");

        btnAnterior.setDisable(true);
        btnSiguiente.setDisable(true);

        btnAnterior.setOnAction(e -> {
            if (paginaActual > 0) {
                paginaActual--;
                loadLogs();
            }
        });

        btnSiguiente.setOnAction(e -> {
            if (paginaActual < totalPaginas - 1) {
                paginaActual++;
                loadLogs();
            }
        });

        HBox paginationBar = new HBox(10);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.getChildren().addAll(btnAnterior, pageInfoLabel, btnSiguiente);

        // logsTable's parent is the root VBox; append the pagination bar after the table
        VBox parent = (VBox) logsTable.getParent();
        parent.getChildren().add(paginationBar);
    }

    @FXML
    private void handleRefresh() {
        paginaActual = 0;
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
        if (btnAnterior != null) btnAnterior.setDisable(true);
        if (btnSiguiente != null) btnSiguiente.setDisable(true);

        final int pagina = paginaActual;
        final int tamano = tamanoPagina;

        new Thread(() -> {
            try {
                PaginatedResult<LogEntry> result = LogService.getInstance().getLogs(pagina, tamano);
                Platform.runLater(() -> {
                    logsTable.setItems(FXCollections.observableArrayList(result.getRegistros()));
                    totalPaginas = Math.max(1, result.getTotalPaginas());
                    paginaActual = result.getPagina();

                    String pageInfo = "Página %d de %d (%d registros)".formatted(
                            paginaActual + 1, totalPaginas, result.getTotalRegistros());
                    pageInfoLabel.setText(pageInfo);
                    statusLabel.setText("%d registro(s) en esta página".formatted(result.getRegistros().size()));

                    btnAnterior.setDisable(paginaActual <= 0);
                    btnSiguiente.setDisable(paginaActual >= totalPaginas - 1);
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                    if (btnAnterior != null) btnAnterior.setDisable(false);
                    if (btnSiguiente != null) btnSiguiente.setDisable(false);
                });
            }
        }, "logs-loader").start();
    }
}
