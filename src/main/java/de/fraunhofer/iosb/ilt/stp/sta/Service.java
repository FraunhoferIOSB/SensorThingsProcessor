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
package de.fraunhofer.iosb.ilt.stp.sta;

import com.google.gson.JsonElement;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorSubclass;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class Service implements AnnotatedConfigurable<SensorThingsService, Object> {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Service.class);

    private static final MqttQos DEFAULT_QOS = MqttQos.AT_LEAST_ONCE;

    @ConfigurableField(editor = EditorString.class,
            label = "Service URL", description = "The url of the server to aggregate for.")
    @EditorString.EdOptsString(dflt = "http://localhost:8080/FROST-Server/v1.0")
    private String serviceUrl;

    @ConfigurableField(editor = EditorString.class,
            label = "MQTT Url", description = "Connection url for the mqtt service.")
    @EditorString.EdOptsString(dflt = "tcp://localhost:1883")
    private String mqttUrl;

    @ConfigurableField(editor = EditorString.class,
            label = "MQTT ClientId", description = "Client ID to use with MQTT. Leave emtpy for random.")
    @EditorString.EdOptsString(dflt = "http://localhost:8080/FROST-Server/v1.0")
    private String mqttId;

    @ConfigurableField(editor = EditorSubclass.class,
            label = "Auth Method", description = "The authentication method the service uses.",
            optional = true)
    @EditorSubclass.EdOptsSubclass(
            iface = AuthMethod.class)
    private AuthMethod authMethod;

    @ConfigurableField(editor = EditorBoolean.class,
            label = "Use DataArrays",
            description = "Use the SensorThingsAPI DataArray extension to post Observations. "
            + "This is much more efficient when posting many observations. "
            + "The number of items grouped together is determined by the messageInterval setting.")
    @EditorBoolean.EdOptsBool()
    private boolean useDataArrays;

    @ConfigurableField(editor = EditorSubclass.class,
            label = "Validator", description = "The validator to use.",
            optional = true)
    @EditorSubclass.EdOptsSubclass(
            iface = Validator.class)
    private Validator validator;

    private SensorThingsService service;
    private boolean noAct = false;

    private final Map<Entity, DataArrayValue> davMap = new HashMap<>();

    private Entity lastDatastream;

    private DataArrayValue lastDav;

    private int inserted = 0;
    private int updated = 0;
    private String clientId;
    private Mqtt3AsyncClient client;

    private final Map<String, List<Consumer<Mqtt3Publish>>> mqttSubscriptions = new HashMap<>();

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
        AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);

        service = context;
        if (service == null) {
            service = new SensorThingsService();
        }

        try {
            service.setEndpoint(new URL(serviceUrl));
            if (authMethod != null) {
                authMethod.setAuth(service);
            }
        } catch (MalformedURLException ex) {
            LOGGER.error("Failed to create service.", ex);
            throw new IllegalArgumentException("Failed to create service.", ex);
        }

        if (validator == null) {
            validator = new Validator.ValidatorNull();
        }
    }

    public String getClientId() {
        if (Utils.isNullOrEmpty(clientId)) {
            clientId = mqttId;
            if (Utils.isNullOrEmpty(clientId)) {
                clientId = "processor-" + UUID.randomUUID();
            }
        }
        return clientId;
    }

    public synchronized Mqtt3AsyncClient getMqttClient() throws URISyntaxException {
        if (client == null) {
            String myClientId = getClientId();
            URI url = new URI(mqttUrl);
            LOGGER.info("Connecting to {} using clientId {}.", mqttUrl, myClientId);
            Mqtt3ClientBuilder builder = Mqtt3Client.builder()
                    .identifier(myClientId)
                    .serverHost(url.getHost())
                    .serverPort(url.getPort())
                    .webSocketConfig(MqttWebSocketConfig.builder().serverPath(url.getPath()).build())
                    .addConnectedListener((context) -> {
                        resubscribeAll();
                    })
                    .addDisconnectedListener((context) -> {
                        LOGGER.info("connectionLost");
                    });
            if (mqttUrl.startsWith("tcps")) {
                builder = builder.sslWithDefaultConfig();
            }
            client = builder.buildAsync();
            client.connect();
        }
        return client;
    }

    public synchronized void closeMqttClient() {
        LOGGER.info("Unsubscribing all topics...");
        unsubscribeAll();
        if (client == null) {
            return;
        }
        if (client.getState().isConnected()) {
            LOGGER.info("Stopping MQTT client...");
            client.disconnect();
        } else {
            LOGGER.info("MQTT client already stopped.");
        }
        client = null;
    }

    private void resubscribeAll() {
        LOGGER.info("Resubscribing all topics...");
        int total = 0;
        int failed = 0;
        List<CompletableFuture<Mqtt3SubAck>> futures = new ArrayList<>();
        for (Map.Entry<String, List<Consumer<Mqtt3Publish>>> entry : mqttSubscriptions.entrySet()) {
            String topic = entry.getKey();
            List<Consumer<Mqtt3Publish>> listeners = entry.getValue();
            for (Consumer<Mqtt3Publish> listener : listeners) {
                futures.add(
                        client.subscribeWith().topicFilter(topic).qos(DEFAULT_QOS).callback(listener).send()
                );
                total++;
            }
        }
        for (CompletableFuture<Mqtt3SubAck> future : futures) {
            try {
                for (Mqtt3SubAckReturnCode item : future.get().getReturnCodes()) {
                    if (item.isError()) {
                        LOGGER.error("Failed to re-subscripe to topic: {}", item);
                        failed++;
                    }
                }
            } catch (InterruptedException | ExecutionException exc) {
                LOGGER.error("Failed to re-subscripe to topic: {}", exc.getMessage());
                failed++;
            }
        }
        LOGGER.info("Resubscribed to {} topics, failed: {}...", total, failed);
    }

    public synchronized void unsubscribeAll() {
        String[] topics = mqttSubscriptions.keySet().toArray(new String[0]);
        for (String topic : topics) {
            removeSubscriptions(topic);
        }
    }

    private List<Consumer<Mqtt3Publish>> getSubscriptionListForTopic(String topic) {
        List<Consumer<Mqtt3Publish>> listeners = mqttSubscriptions.get(topic);
        if (listeners == null) {
            listeners = new ArrayList<>();
            mqttSubscriptions.put(topic, listeners);
        }
        return listeners;
    }

    public synchronized void removeSubscriptions(String topic) {
        mqttSubscriptions.remove(topic);
        if (client == null || !client.getState().isConnected()) {
            return;
        }
        client.unsubscribeWith().addTopicFilter(topic).send();
    }

    public synchronized void subscribe(String topic, Consumer<Mqtt3Publish> messageListener) {
        getSubscriptionListForTopic(topic).add(messageListener);
        if (client == null || !client.getState().isConnected()) {
            return;
        }
        client.subscribeWith().topicFilter(topic).qos(DEFAULT_QOS).callback(messageListener).send();
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
        } else if (!useDataArrays && !noAct) {
            service.create(obs);
            inserted++;
        } else if (useDataArrays) {
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
