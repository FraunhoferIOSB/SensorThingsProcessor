/*
 * Copyright (C) 2018 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.stp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

public class ControllerScene implements Initializable {

    /**
     * The logger for this class.
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ControllerScene.class);
    @FXML
    private ScrollPane paneConfig;
    @FXML
    private Button buttonLoad;
    @FXML
    private Button buttonSave;
    @FXML
    private Button buttonImport;
    @FXML
    private Button buttonStop;
    @FXML
    private CheckBox toggleNoAct;
    @FXML
    private CheckBox toggleOnline;
    @FXML
    private Button buttonAggManager;

    private ProcessorWrapper wrapper;
    private EditorMap<Map<String, Object>> configEditor;
    private FileChooser fileChooser = new FileChooser();

    @FXML
    private void actionLoad(ActionEvent event) {
        fileChooser.setTitle("Load Config");
        File file = fileChooser.showOpenDialog(paneConfig.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String config = FileUtils.readFileToString(file, "UTF-8");
            JsonElement json = new JsonParser().parse(config);
            wrapper.configure(json, null, null);
        } catch (IOException ex) {
            LOGGER.error("Failed to read file", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("failed to read file");
            alert.setContentText(ex.getLocalizedMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void actionSave(ActionEvent event) {
        JsonElement json = configEditor.getConfig();
        String config = new GsonBuilder().setPrettyPrinting().create().toJson(json);
        fileChooser.setTitle("Save Config");
        File file = fileChooser.showSaveDialog(paneConfig.getScene().getWindow());

        try {
            FileUtils.writeStringToFile(file, config, "UTF-8");
        } catch (IOException ex) {
            LOGGER.error("Failed to write file.", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("failed to write file");
            alert.setContentText(ex.getLocalizedMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void actionImport(ActionEvent event) {
        JsonElement json = configEditor.getConfig();
        String config = new Gson().toJson(json);
        wrapper.doProcess(
                config,
                toggleNoAct.isSelected(),
                toggleOnline.isSelected());
    }

    @FXML
    private void actionStop(ActionEvent event) {
        wrapper.stopProcess();
    }

    @FXML
    private void actionAggManager(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AggregationManager.fxml"));
            Parent content = loader.<Parent>load();
            Stage stage = new Stage();
            stage.setTitle("Aggregation Manager");
            Scene scene = new Scene(content, 1200, 800);
            stage.setScene(scene);
            stage.show();
        } catch (IOException ex) {
            LOGGER.error("Failed to load scene for AggManager", ex);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        wrapper = new ProcessorWrapper();
        configEditor = wrapper.getConfigEditor(null, null);
        paneConfig.setContent(configEditor.getGuiFactoryFx().getNode());
    }
}
