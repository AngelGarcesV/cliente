package com.cliente.presentation.controller;

import com.cliente.MainApp;
import com.cliente.application.service.FileService;
import com.cliente.domain.model.Document;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class FilesController {

    @FXML private TableView<Document> filesTable;
    @FXML private TableColumn<Document, String> colName;
    @FXML private TableColumn<Document, String> colSize;
    @FXML private TableColumn<Document, String> colType;
    @FXML private TableColumn<Document, String> colDate;
    @FXML private Label statusLabel;
    @FXML private Button refreshButton;
    @FXML private Button downloadButton;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSize.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(cd.getValue().getFormattedSize()));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));

        filesTable.setPlaceholder(new Label("No hay documentos disponibles."));
        filesTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, old, newVal) -> downloadButton.setDisable(newVal == null));
        downloadButton.setDisable(true);

        loadFiles();
    }

    @FXML
    private void handleRefresh() {
        loadFiles();
    }

    @FXML
    private void handleDownload() {
        Document selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("fxml/download-dialog.fxml"));
            Parent root = loader.load();
            DownloadDialogController controller = loader.getController();
            controller.setDocument(selected);

            Stage dialog = new Stage();
            dialog.initOwner(MainApp.getPrimaryStage());
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.setTitle("Opciones de Descarga");
            dialog.setResizable(false);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                MainApp.class.getResource("css/main.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR,
                "No se pudo abrir el diálogo: " + e.getMessage()).showAndWait();
        }
    }

    private void loadFiles() {
        statusLabel.setText("Cargando documentos...");
        refreshButton.setDisable(true);

        new Thread(() -> {
            try {
                List<Document> docs = FileService.getInstance().listDocuments();
                Platform.runLater(() -> {
                    filesTable.setItems(FXCollections.observableArrayList(docs));
                    statusLabel.setText(docs.size() + " documento(s) disponible(s)");
                    refreshButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    refreshButton.setDisable(false);
                });
            }
        }, "files-loader").start();
    }
}
