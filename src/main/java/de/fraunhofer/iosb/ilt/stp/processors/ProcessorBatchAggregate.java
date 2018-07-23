package de.fraunhofer.iosb.ilt.stp.processors;

import com.google.common.collect.ComparisonChain;
import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.EntityType;
import de.fraunhofer.iosb.ilt.sta.model.Id;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import de.fraunhofer.iosb.ilt.stp.Processor;
import de.fraunhofer.iosb.ilt.stp.ProcessorHelper;
import de.fraunhofer.iosb.ilt.stp.aggregation.Utils;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregateCombo;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregationBase;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.AggregationData;
import de.fraunhofer.iosb.ilt.stp.processors.aggregation.Aggregator;
import de.fraunhofer.iosb.ilt.stp.sta.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

/**
 *
 * @author scf
 */
public class ProcessorBatchAggregate implements Processor {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorBatchAggregate.class);
    private static final int RECEIVE_QUEUE_CAPACITY = 100000;

    private static class MessageContext {

        public final List<AggregateCombo> combos;
        public final String topic;
        public final String message;

        public MessageContext(List<AggregateCombo> combos, String topic, String message) {
            this.combos = combos;
            this.topic = topic;
            this.message = message;
        }
    }

    private class CalculationOrder implements Delayed {

        private AtomicBoolean waiting = new AtomicBoolean(true);
        private final AggregateCombo combo;
        private final Interval interval;
        private final Instant targetTime;
        private final long targetMillis;

        public CalculationOrder(AggregateCombo combo, Interval interval, Instant delayUntill) {
            this.combo = combo;
            this.interval = interval;
            this.targetTime = delayUntill;
            this.targetMillis = targetTime.toEpochMilli();
        }

        public void execute() {
            waiting.set(false);
            orders.remove(this);
            try {
                calculateAggregate(combo, interval);
            } catch (ServiceFailureException | ProcessException ex) {
                LOGGER.error("Failed to calculate order!", ex);
            }
        }

        public Instant getTargetTime() {
            return targetTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (super.equals(obj)) {
                return true;
            }
            if (!(obj instanceof CalculationOrder)) {
                return false;
            }
            CalculationOrder otherOrder = (CalculationOrder) obj;
            if (waiting.get() != otherOrder.waiting.get()) {
                return false;
            }
            if (!interval.equals(otherOrder.interval)) {
                return false;
            }
            return combo.compareTo(otherOrder.combo) == 0;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + (this.waiting.get() ? 1 : 0);
            hash = 23 * hash + Objects.hashCode(this.combo);
            hash = 23 * hash + Objects.hashCode(this.interval);
            return hash;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(targetMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            CalculationOrder other = (CalculationOrder) o;
            return ComparisonChain.start()
                    .compare(targetMillis, other.targetMillis)
                    .compareTrueFirst(waiting.get(), other.waiting.get())
                    .compare(combo, other.combo)
                    .compare(interval.getStart(), other.interval.getStart())
                    .result();
        }

    }

    private EditorMap<Map<String, Object>> editor;
    private EditorClass<SensorThingsService, Object, Service> editorServiceSource;
    private EditorString editorTimeZone;
    private EditorInt editorDelay;
    private EditorInt editorThreadCount;
    private EditorBoolean editorFixReferences;

    private boolean noAct = false;
    private SensorThingsService stsSource;
    private Service sourceService;
    private AggregationData aggregationData;

    private Duration orderDelay;
    private ZoneId zoneId;
    private boolean fixReferences;
    private MqttClient mqttClient;

    private BlockingQueue<MessageContext> messagesToHandle = new LinkedBlockingQueue<>(RECEIVE_QUEUE_CAPACITY);
    private AtomicInteger messagesCount = new AtomicInteger(0);
    private Set<CalculationOrder> orders = new HashSet<>();
    private DelayQueue<CalculationOrder> orderQueue = new DelayQueue<>();
    private ExecutorService orderExecutorService;
    private ExecutorService messageReceptionService;
    private Aggregator aggregator = new Aggregator();

    @Override
    public void configure(JsonElement config, Void context, Void edtCtx) {
        stsSource = new SensorThingsService();
        getConfigEditor(context, edtCtx).setConfig(config);
        sourceService = editorServiceSource.getValue();
        zoneId = ZoneId.of(editorTimeZone.getValue());
        sourceService.setNoAct(noAct);
        orderDelay = Duration.ofMillis(editorDelay.getValue().longValue());
        fixReferences = editorFixReferences.getValue();
        aggregationData = new AggregationData(stsSource, fixReferences);
        aggregationData.setZoneId(zoneId);
    }

    @Override
    public ConfigEditor<?> getConfigEditor(Void context, Void edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();

            editorServiceSource = new EditorClass<>(stsSource, null, Service.class, "Source Service", "The service to read observations from.");
            editor.addOption("source", editorServiceSource, false);

            editorTimeZone = new EditorString("+1", 1, "TimeZone", "The timezone to use when determining the start of the day,hour,etc.");
            editor.addOption("timeZone", editorTimeZone, true);

            editorDelay = new EditorInt(0, 999999, 1, 10000, "Delay", "The number of milliseconds to delay calculations with, in order to avoid duplicate calculations.");
            editor.addOption("delay", editorDelay, true);

            editorThreadCount = new EditorInt(0, 10, 1, 2, "Thread Count", "The number of simultanious calculations to run in parallel.");
            editor.addOption("threads", editorThreadCount, true);

            editorFixReferences = new EditorBoolean(false, "Fix References", "Fix the references between aggregate multidatastreams.");
            editor.addOption("fixRefs", editorFixReferences, true);
        }
        return editor;
    }

    @Override
    public void setNoAct(boolean noAct) {
        this.noAct = noAct;
        if (sourceService != null) {
            sourceService.setNoAct(noAct);
        }
    }

    private void calculateAggregate(AggregateCombo combo, Interval interval) throws ServiceFailureException, ProcessException {
        Instant start = interval.getStart();
        Instant end = interval.getEnd();
        List<Observation> sourceObs = combo.getObservationsForSource(start, end);
        LOGGER.info("Calculating {} using {} obs for {}.", interval, sourceObs.size(), combo);
        if (sourceObs.isEmpty()) {
            return;
        }
        LOGGER.debug("Obs:        {}/{}.", sourceObs.get(0).getPhenomenonTime(), sourceObs.get(sourceObs.size() - 1).getPhenomenonTime());

        BigDecimal[] result;
        try {
            if (combo.sourceIsAggregate) {
                result = aggregator.calculateAggregateResultFromAggregates(sourceObs);
            } else if (combo.sourceIsCollection) {
                result = aggregator.calculateAggregateResultFromOriginalLists(interval, sourceObs);
            } else {
                result = aggregator.calculateAggregateResultFromOriginals(interval, sourceObs);
            }
        } catch (NumberFormatException exc) {
            LOGGER.error("Failed to calculate statistics for " + combo.toString() + " interval " + interval, exc);
            return;
        }
        BigDecimal[] sizedResult = new BigDecimal[combo.target.getMultiObservationDataTypes().size()];
        int length = Math.min(sizedResult.length, result.length);
        for (int i = 0; i < length; i++) {
            sizedResult[i] = result[i];
        }
        Observation newObs = new Observation(sizedResult, combo.target);
        Map<String, Object> parameters = new HashMap<>();
        for (Observation sourceOb : sourceObs) {
            Map<String, Object> otherParams = sourceOb.getParameters();
            if (otherParams == null) {
                continue;
            }
            parameters.putAll(otherParams);
        }
        parameters.put("resultCount", sourceObs.size());
        newObs.setParameters(parameters);
        newObs.setPhenomenonTimeFrom(interval);
        sourceService.addObservation(newObs);
    }

    private void calculateAggregates(AggregateCombo combo) throws ServiceFailureException, ProcessException {
        Observation lastAggObs = combo.getLastForTarget();

        Instant calcIntervalStart;
        if (lastAggObs == null) {
            Observation firstSourceObs = combo.getFirstForSource();
            if (firstSourceObs == null) {
                LOGGER.debug("No source observations at all for {}.", combo);
                return;
            }
            Instant firstSourceStart = Utils.getPhenTimeStart(firstSourceObs);

            ZonedDateTime atZone = firstSourceStart.atZone(combo.getZoneId());
            ZonedDateTime firstIntStart = combo.level.toIntervalStart(atZone);
            if (atZone.isEqual(firstIntStart)) {
                calcIntervalStart = firstIntStart.toInstant();
            } else {
                calcIntervalStart = firstIntStart.plus(combo.level.duration).toInstant();
            }

        } else {
            TimeObject lastAggPhenTime = lastAggObs.getPhenomenonTime();
            calcIntervalStart = lastAggPhenTime.getAsInterval().getEnd();
        }
        Observation lastSourceObs = combo.getLastForSource();
        if (lastSourceObs == null) {
            LOGGER.debug("No source observations at all for {}.", combo);
            return;
        }
        Instant lastSourcePhenTime = Utils.getPhenTimeEnd(lastSourceObs);

        boolean more = true;
        while (more) {
            Instant calcIntervalEnd = calcIntervalStart.plus(combo.level.duration);

            if (lastSourcePhenTime.isBefore(calcIntervalEnd)) {
                LOGGER.info("Nothing (more) to do for {}.", combo);
                return;
            }

            calculateAggregate(combo, Interval.of(calcIntervalStart, calcIntervalEnd));
            calcIntervalStart = calcIntervalEnd;
        }

    }

    private void calculateAggregates(Collection<AggregateCombo> targets) {
        for (AggregateCombo target : targets) {
            try {
                calculateAggregates(target);
            } catch (ServiceFailureException | ProcessException ex) {
                LOGGER.error("Error calculating for: " + target, ex);
            }
        }
    }

    private void calculateAggregates(AggregationData aggregationData) {
        Map<String, AggregationBase> targets = aggregationData.getCombosByBase();
        for (AggregationBase target : targets.values()) {
            calculateAggregates(target.getCombos());
        }
    }

    private void createSubscriptions(AggregationData aggregationData) throws MqttException {
        Map<String, List<AggregateCombo>> comboBySource = aggregationData.getComboBySource();
        LOGGER.info("Found {} mqtt paths to watch.", comboBySource.keySet().size());

        for (Map.Entry<String, List<AggregateCombo>> entry : comboBySource.entrySet()) {
            String path = entry.getKey();
            LOGGER.debug("Subscribing to: {}", path);
            final List<AggregateCombo> combos = entry.getValue();

            // First make sure we are up-to-date.
            calculateAggregates(combos);

            // Then add the subscription.
            mqttClient.subscribe(path, (String topic, MqttMessage message) -> {
                if (messagesToHandle.offer(new MessageContext(combos, topic, message.toString()))) {
                    int count = messagesCount.getAndIncrement();
                    if (count > 1) {
                        LOGGER.trace("Receive queue size: {}", count);
                    }
                } else {
                    LOGGER.error("Receive queue is full! More than {} messages in backlog", RECEIVE_QUEUE_CAPACITY);
                }
            });
        }
    }

    private void createOrderFor(List<AggregateCombo> combos, String message) {
        try {
            AggregateCombo mainCombo = combos.get(0);
            Id sourceId = mainCombo.getSourceId();
            EntityType sourceType = mainCombo.getSourceType();
            Observation obs = parseMessageToObservation(message);
            for (AggregateCombo combo : combos) {
                createOrdersFor(combo, obs, sourceType, sourceId);
            }
        } catch (IOException ex) {
            LOGGER.error("Invalid message.", ex);
        } catch (Exception ex) {
            LOGGER.error("Exception processing!", ex);
        }
    }

    private void createOrdersFor(AggregateCombo combo, Observation obs, EntityType sourceType, Id sourceId) {
        List<Interval> intervals = combo.calculateIntervalsForTime(obs.getPhenomenonTime());
        int count = intervals.size();
        if (count > 1) {
            for (Interval interval : intervals) {
                LOGGER.debug("{} {}: Interval {} recalculating.", sourceType, sourceId, interval);
                CalculationOrder order = new CalculationOrder(combo, interval, Instant.now().plus(orderDelay));
                offerOrder(order);
            }
        } else {
            for (Interval interval : intervals) {
                Interval toCalculate;
                if (interval.getEnd().equals(Utils.getPhenTimeEnd(obs))) {
                    // The observation is the last one for the interval.
                    LOGGER.debug("{} {}: Interval {} recalculating, because end reached.", sourceType, sourceId, interval);
                    CalculationOrder order = new CalculationOrder(combo, interval, Instant.now().plus(orderDelay));
                    offerOrder(order);
                    toCalculate = combo.unsetCurrent(interval);
                } else {
                    toCalculate = combo.replaceIfNotCurrent(interval);
                }
                if (toCalculate != null) {
                    LOGGER.debug("{} {}: Interval {} recalculating, because we now have {}.", sourceType, sourceId, toCalculate, interval);
                    CalculationOrder order = new CalculationOrder(combo, toCalculate, Instant.now().plus(orderDelay));
                    offerOrder(order);
                }
            }
        }
    }

    private boolean offerOrder(CalculationOrder order) {
        if (orders.contains(order)) {
            return false;
        }
        if (!orderQueue.offer(order)) {
            LOGGER.error("Could not queue order, queue full!");
            return false;
        }
        orders.add(order);
        return true;
    }

    private Observation parseMessageToObservation(String message) throws IOException {
        return ObjectMapperFactory.get().readValue(message, Observation.class);
    }

    @Override
    public void process() {
        calculateAggregates(aggregationData);
    }

    @Override
    public void startListening() {
        try {
            mqttClient = sourceService.getMqttClient();
            orderExecutorService = ProcessorHelper.createProcessors(
                    editorThreadCount.getValue(),
                    orderQueue,
                    x -> x.execute(),
                    "Aggregator");
            messageReceptionService = ProcessorHelper.createProcessors(
                    editorThreadCount.getValue(),
                    messagesToHandle, (MessageContext x) -> {
                        messagesCount.decrementAndGet();
                        createOrderFor(x.combos, x.message);
                    },
                    "Receiver");
            createSubscriptions(aggregationData);
        } catch (MqttException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public void stopListening() {
        try {
            if (mqttClient.isConnected()) {
                LOGGER.info("Stopping MQTT client.");
                Map<String, List<AggregateCombo>> comboBySource = aggregationData.getComboBySource();
                String[] paths = comboBySource.keySet().toArray(new String[comboBySource.size()]);
                mqttClient.unsubscribe(paths);
                mqttClient.disconnect();
            } else {
                LOGGER.info("MQTT client already stopped.");
            }
            if (messageReceptionService != null) {
                LOGGER.info("Stopping Receivers.");
                ProcessorHelper.shutdownProcessors(messageReceptionService, messagesToHandle, 5, TimeUnit.SECONDS);
            }
            if (orderExecutorService != null) {
                LOGGER.info("Stopping Processors.");
                ProcessorHelper.shutdownProcessors(orderExecutorService, orderQueue, 5, TimeUnit.SECONDS);
            }
        } catch (MqttException ex) {
            LOGGER.error("Problem while disconnecting!", ex);
        }
    }

}
