/*
 * Copyright (C) 2017 Fraunhofer IOSB
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
package de.fraunhofer.iosb.ilt.stp.sta;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.Configurable;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorSubclass;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Entity;
import de.fraunhofer.iosb.ilt.sta.model.MultiDatastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.DataArrayDocument;
import de.fraunhofer.iosb.ilt.sta.model.ext.DataArrayValue;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import de.fraunhofer.iosb.ilt.stp.validator.Validator;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class Service implements Configurable<SensorThingsService, Object> {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Service.class);

    private EditorMap<Map<String, Object>> editor;
    private EditorString editorService;
    private EditorString editorMqttUrl;
    private EditorString editorMqttId;
    private EditorSubclass<Object, Object, AuthMethod> editorAuthMethod;
    private EditorBoolean editorUseDataArray;
    private EditorSubclass<SensorThingsService, Object, Validator> editorValidator;

    private SensorThingsService service;
    private boolean noAct = false;
    private boolean dataArray = false;

    private final Map<Entity, DataArrayValue> davMap = new HashMap<>();

    private Entity lastDatastream;

    private DataArrayValue lastDav;

    private int inserted = 0;
    private int updated = 0;
    private String clientId;
    private MqttClient client;
    private Validator validator;

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx) {
        service = context;
        if (service == null) {
            service = new SensorThingsService();
        }
        getConfigEditor(service, edtCtx).setConfig(config);
        dataArray = editorUseDataArray.getValue();
        try {
            service.setEndpoint(new URI(editorService.getValue()));
            AuthMethod authMethod = editorAuthMethod.getValue();
            if (authMethod != null) {
                authMethod.setAuth(service);
            }
        } catch (URISyntaxException ex) {
            LOGGER.error("Failed to create service.", ex);
            throw new IllegalArgumentException("Failed to create service.", ex);
        }

        validator = editorValidator.getValue();
        if (validator == null) {
            validator = new Validator.ValidatorNull();
        }
    }

    @Override
    public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();
            editorService = new EditorString("https://service.somewhere/path/v1.0", 1, "Service URL", "The url of the server.");
            editor.addOption("serviceUrl", editorService, false);

            editorMqttUrl = new EditorString("tcp://localhost:1883", 1, "MQTT Url", "Connection url for the mqtt service");
            editor.addOption("mqttUrl", editorMqttUrl, true);

            editorMqttId = new EditorString("", 1, "MQTT ClientId", "Client ID to use with MQTT. Leave emtpy for random.");
            editor.addOption("mqttId", editorMqttId, true);

            editorAuthMethod = new EditorSubclass<>(null, null, AuthMethod.class, "Auth Method", "The authentication method the service uses.", false, "className");
            editor.addOption("authMethod", editorAuthMethod, true);

            editorUseDataArray = new EditorBoolean(false, "Use DataArrays",
                    "Use the SensorThingsAPI DataArray extension to post Observations. "
                    + "This is much more efficient when posting many observations. "
                    + "The number of items grouped together is determined by the messageInterval setting.");
            editor.addOption("useDataArrays", editorUseDataArray, true);

            editorValidator = new EditorSubclass(service, edtCtx, Validator.class, "Validator", "The validator to use.", false, "className");
            editor.addOption("validator", editorValidator, true);
        }
        return editor;

    }

    public String getClientId() {
        if (Strings.isNullOrEmpty(clientId)) {
            clientId = editorMqttId.getValue();
            if (Strings.isNullOrEmpty(clientId)) {
                clientId = "processor-" + UUID.randomUUID();
            }
        }
        return clientId;
    }

    public MqttClient getMqttClient() throws MqttException {
        if (client == null) {
            String myClientId = getClientId();
            LOGGER.info("Connecting to {} using clientId {}.", editorMqttUrl.getValue(), myClientId);
            client = new MqttClient(editorMqttUrl.getValue(), myClientId);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setAutomaticReconnect(true);
            connOpts.setCleanSession(false);
            connOpts.setKeepAliveInterval(60);
            connOpts.setConnectionTimeout(30);
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    LOGGER.info("connectionLost");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
            client.connect(connOpts);
        }
        return client;
    }

    public void setNoAct(boolean noAct) {
        this.noAct = noAct;
    }

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public void addObservation(Observation obs) throws ServiceFailureException, ProcessException {
        if (!validator.isValid(obs)) {
            return;
        }
        if (obs.getId() != null && !noAct) {
            service.update(obs);
            updated++;
        } else if (!dataArray && !noAct) {
            service.create(obs);
            inserted++;
        } else if (dataArray) {
            addToDataArray(obs);
        }
    }

    private void addToDataArray(Observation obs) throws ServiceFailureException, ProcessException {
        if (!validator.isValid(obs)) {
            return;
        }
        Entity ds = obs.getDatastream();
        if (ds == null) {
            ds = obs.getMultiDatastream();
        }
        if (ds == null) {
            throw new IllegalArgumentException("Observation must have a (Multi)Datastream.");
        }
        if (ds != lastDatastream) {
            findDataArrayValue(ds, obs);
        }
        lastDav.addObservation(obs);
    }

    private void findDataArrayValue(Entity ds, Observation o) {
        DataArrayValue dav = davMap.get(ds);
        if (dav == null) {
            if (ds instanceof Datastream) {
                dav = new DataArrayValue((Datastream) ds, getDefinedProperties(o));
            } else {
                dav = new DataArrayValue((MultiDatastream) ds, getDefinedProperties(o));
            }
            davMap.put(ds, dav);
        }
        lastDav = dav;
        lastDatastream = ds;
    }

    public int sendDataArray() throws ServiceFailureException {
        if (!noAct && !davMap.isEmpty()) {
            DataArrayDocument dad = new DataArrayDocument();
            dad.getValue().addAll(davMap.values());
            List<String> locations = service.create(dad);
            long error = locations.stream().filter(
                    location -> location.startsWith("error")
            ).count();
            if (error > 0) {
                Optional<String> first = locations.stream().filter(location -> location.startsWith("error")).findFirst();
                LOGGER.warn("Failed to insert {} Observations. First error: {}", error, first);
            }
            long nonError = locations.size() - error;
            inserted += nonError;
        }
        davMap.clear();
        lastDav = null;
        lastDatastream = null;
        return inserted;
    }

    private Set<DataArrayValue.Property> getDefinedProperties(Observation o) {
        Set<DataArrayValue.Property> value = new HashSet<>();
        value.add(DataArrayValue.Property.Result);
        if (o.getPhenomenonTime() != null) {
            value.add(DataArrayValue.Property.PhenomenonTime);
        }
        if (o.getResultTime() != null) {
            value.add(DataArrayValue.Property.ResultTime);
        }
        if (o.getResultQuality() != null) {
            value.add(DataArrayValue.Property.ResultQuality);
        }
        if (o.getParameters() != null) {
            value.add(DataArrayValue.Property.Parameters);
        }
        if (o.getValidTime() != null) {
            value.add(DataArrayValue.Property.ValidTime);
        }
        return value;
    }

    public Iterator<Thing> getAllThings() {
        List<Thing> result = new ArrayList<>();
        try {
            EntityList<Thing> list = service.things().query().list();
            return list.fullIterator();
        } catch (ServiceFailureException ex) {
            LOGGER.error("Failed to fetch things.", ex);
            return null;
        }
    }

    public SensorThingsService getService() {
        return service;
    }

}
