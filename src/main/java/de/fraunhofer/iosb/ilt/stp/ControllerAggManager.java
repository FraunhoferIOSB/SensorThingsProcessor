package de.fraunhofer.iosb.ilt.stp;

import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregateCombo;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregationBase;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregationData;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregationLevel;
import de.fraunhofer.iosb.ilt.stp.sta.SensorThingsUtils;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class ControllerAggManager implements Initializable {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerAggManager.class);

    @FXML
    private BorderPane paneAddLevel;

    @FXML
    private Button buttonReload;

    @FXML
    private Button buttonAddLevel;

    @FXML
    private Button buttonApplyChanges;

    @FXML
    private TextField textUrl;

    @FXML
    private TableView<AggregationBase> table;

    private SensorThingsService service;

    private AggregationData data;

    private TableColumn<AggregationBase, String> baseColumn;
    private Map<AggregationLevel, TableColumn<AggregationBase, Boolean>> columnsByLevel = new HashMap<>();
    private EditorMap<?> levelEditor;
    private SensorThingsUtils utils = new SensorThingsUtils();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AggregationLevel level = new AggregationLevel(ChronoUnit.HOURS, 1);
        levelEditor = level.getConfigEditor(null, null);
        Node node = levelEditor.getGuiFactoryFx().getNode();
        paneAddLevel.setCenter(node);
    }

    private TableColumn<AggregationBase, Boolean> getColumnForLevel(final AggregationLevel level) {
        TableColumn<AggregationBase, Boolean> column = columnsByLevel.get(level);
        if (column == null) {
            LOGGER.info("Creating column {}.", level);
            column = new TableColumn(level.toString());
            column.setCellFactory((TableColumn<AggregationBase, Boolean> param) -> new CheckBoxTableCell<>());
            column.setCellValueFactory((TableColumn.CellDataFeatures<AggregationBase, Boolean> param) -> param.getValue().getLevelProperty(level));
            column.setEditable(true);
            columnsByLevel.put(level, column);
        }
        return column;
    }

    @FXML
    private void actionReload(ActionEvent event) {
        try {
            baseColumn = new TableColumn("Base Name");
            baseColumn.setCellValueFactory((TableColumn.CellDataFeatures<AggregationBase, String> param) -> param.getValue().getBaseNameProperty());

            String urlString = textUrl.getText();
            service = new SensorThingsService(new URL(urlString));
            data = new AggregationData(service, true, true);
            for (AggregationBase base : data.getAggregationBases()) {
                for (AggregateCombo combo : base.getCombos()) {
                    getColumnForLevel(combo.level);
                }
            }

            table.getColumns().clear();
            table.getColumns().add(baseColumn);
            table.getColumns().addAll(columnsByLevel.values());

            table.setItems(data.getAggregationBases());
        } catch (MalformedURLException | URISyntaxException ex) {
            LOGGER.error("Failed to parse url.", ex);
            new Alert(Alert.AlertType.ERROR, "Failed to parse URL", ButtonType.OK).show();
        }

    }

    @FXML
    private void actionAddLevel(ActionEvent event) {
        AggregationLevel level = new AggregationLevel();
        level.configure(levelEditor.getConfig(), null, null);
        TableColumn<AggregationBase, Boolean> column = getColumnForLevel(level);
        table.getColumns().add(column);
    }

    @FXML
    private void actionApplyChanges(ActionEvent event) throws ServiceFailureException {
        String changeLog = generateChangeLog();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setResizable(true);
        dialog.setTitle("Apply changes?");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.APPLY);
        TextArea textArea = new TextArea(changeLog);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        dialog.getDialogPane().setContent(textArea);

        Optional<ButtonType> confirmation = dialog.showAndWait();
        if (confirmation.isPresent() && confirmation.get() == ButtonType.APPLY) {
            LOGGER.info("Working");
            applyChanges();
            actionReload(null);
        } else {
            LOGGER.info("Cancelled");
        }
    }

    private void createAggregate(AggregationBase base, AggregationLevel level) throws ServiceFailureException {
        Datastream baseDs = base.getBaseDatastream();
        ObservedProperty op = baseDs.getObservedProperty();
        UnitOfMeasurement uom = baseDs.getUnitOfMeasurement();
        utils.findOrCreateAggregateOps(service, op);

        List<ObservedProperty> ops = new ArrayList<>();
        ops.add(op);
        ops.addAll(utils.aggregateProperties.get(op));
        List<UnitOfMeasurement> uoms = new ArrayList<>();
        for (int i = 0; i < ops.size(); i++) {
            uoms.add(uom);
        }

        Map<String, Object> aggProps = new HashMap<>();
        aggProps.put(SensorThingsUtils.KEY_AGGREGATE_FOR, "/Datastreams(" + op.getId().getUrl() + ")");
        String mdsName = base.getBaseName() + " " + level.toPostFix();
        String mdsDesc = baseDs.getDescription() + " aggregated per " + level.amount + " " + level.unit;
        utils.findOrCreateMultiDatastream(service, mdsName, mdsDesc, uoms, baseDs.getThing(), ops, baseDs.getSensor(), aggProps);
    }

    private void deleteAggregate(AggregationBase base, AggregationLevel level) {

    }

    private void applyChanges() throws ServiceFailureException {
        for (AggregationBase base : data.getAggregationBases()) {
            Map<AggregationLevel, AggregateCombo> presentLevels = base.getCombosByLevel();
            Map<AggregationLevel, Boolean> wantedLevels = base.getWantedLevels();
            for (Map.Entry<AggregationLevel, Boolean> wantedEntry : wantedLevels.entrySet()) {
                AggregationLevel level = wantedEntry.getKey();
                boolean wanted = wantedEntry.getValue();
                if (wanted && !presentLevels.containsKey(level)) {
                    LOGGER.info("Creating {} for {}.", level, base.getBaseName());
                    createAggregate(base, level);
                } else if (!wanted && presentLevels.containsKey(level)) {
                    LOGGER.info("Deleting {} from {}.", level, base.getBaseName());

                }

            }
        }
    }

    private String generateChangeLog() {
        StringBuilder changeLog = new StringBuilder();
        for (AggregationBase base : data.getAggregationBases()) {
            Map<AggregationLevel, AggregateCombo> presentLevels = base.getCombosByLevel();
            Map<AggregationLevel, Boolean> wantedLevels = base.getWantedLevels();
            for (Map.Entry<AggregationLevel, Boolean> wantedEntry : wantedLevels.entrySet()) {
                AggregationLevel level = wantedEntry.getKey();
                boolean wanted = wantedEntry.getValue();
                if (wanted && !presentLevels.containsKey(level)) {
                    changeLog.append("Create ").append(level.toString()).append(" for ").append(base.getBaseName()).append('\n');

                } else if (!wanted && presentLevels.containsKey(level)) {
                    changeLog.append("DELETE ").append(level.toString()).append(" for ").append(base.getBaseName()).append('\n');

                }
            }
        }
        return changeLog.toString();
    }
}
