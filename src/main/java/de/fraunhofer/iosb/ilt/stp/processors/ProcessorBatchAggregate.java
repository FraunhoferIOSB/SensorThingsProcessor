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
package de.fraunhofer.iosb.ilt.stp.processors;

import com.google.common.collect.ComparisonChain;
import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.AbstractConfigurable;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorLong;
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
import de.fraunhofer.iosb.ilt.stp.utils.MergeQueue;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
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
public class ProcessorBatchAggregate extends AbstractConfigurable<Void, Void> implements Processor {

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

    @ConfigurableField(editor = EditorClass.class,
            label = "Service", description = "The service to read observations from.",
            jsonField = "source")
    @EditorClass.EdOptsClass(clazz = Service.class)
    private Service sourceService;

    @ConfigurableField(editor = EditorString.class,
            label = "TimeZone", description = "The timezone to use when determining the start of the day,hour,etc.",
            optional = true)
    @EditorString.EdOptsString(dflt = "+1")
    private String timeZone;

    @ConfigurableField(editor = EditorLong.class,
            label = "Delay", description = "The number of milliseconds to delay calculations with, in order to avoid duplicate calculations.",
            optional = true)
    @EditorLong.EdOptsLong(dflt = 10000, min = 0, max = 999999)
    private long delay;

    @ConfigurableField(editor = EditorBoolean.class,
            label = "Fix References", description = "Fix the references between aggregate multidatastreams.", optional = true)
    @EditorBoolean.EdOptsBool(dflt = true)
    private boolean fixRefs;

    @ConfigurableField(editor = EditorInt.class,
            label = "Thread Count", description = "The number of simultanious calculations to run in parallel.", optional = true)
    @EditorInt.EdOptsInt(dflt = 2, min = 1, max = 99, step = 1)
    private int threads;

    @ConfigurableField(editor = EditorBoolean.class,
            label = "Cache", description = "Cache observations (only do this if there are no overlapping observations).", optional = true)
    @EditorBoolean.EdOptsBool(dflt = false)
    private boolean cacheObs;

    private final Map<Id, WeakReference<Observation>> obsCache = new HashMap<>();

    private boolean noAct = false;
    private Duration orderDelay;
    private ZoneId zoneId;
    private SensorThingsService stsSource;
    private AggregationData aggregationData;

    private BlockingQueue<MessageContext> messagesToHandle = new LinkedBlockingQueue<>(RECEIVE_QUEUE_CAPACITY);
    private AtomicInteger messagesCount = new AtomicInteger(0);
    private Set<CalculationOrder> orders = new HashSet<>();
    private BlockingQueue<CalculationOrder> orderQueue;
    private MergeQueue<CalculationOrder> orderMerger;
    private ExecutorService orderExecutorService;
    private ExecutorService messageReceptionService;
    private Aggregator aggregator = new Aggregator();
    private boolean running = false;

    @Override
    public void configure(JsonElement config, Void context, Void edtCtx) {
        super.configure(config, context, edtCtx);
        stsSource = sourceService.getService();

        zoneId = ZoneId.of(timeZone);
        sourceService.setNoAct(noAct);
        orderDelay = Duration.ofMillis(delay);

        aggregationData = new AggregationData(stsSource, fixRefs);
        aggregationData.setZoneId(zoneId);
    }

    @Override
    public void setNoAct(boolean noAct) {
        this.noAct = noAct;
        if (sourceService != null) {
            sourceService.setNoAct(noAct);
        }
    }

    private List<Observation> findObservations(AggregateCombo combo, Instant start, Instant end) {
        if (cacheObs && combo.getSourceType() == EntityType.DATASTREAM) {
            WeakReference<Observation> weakRef;
            synchronized (obsCache) {
                weakRef = obsCache.get(combo.getSourceId());
            }
            if (weakRef != null) {
                Observation cachedObs = weakRef.get();
                if (cachedObs != null) {
                    Interval phenTime = cachedObs.getPhenomenonTime().getAsInterval();
                    if (phenTime.contains(start) && phenTime.contains(end)) {
                        LOGGER.debug("Using cached observation for {}  ->  {}", start, end);
                        return Arrays.asList(cachedObs);
                    }
                }
            }
        }
        List<Observation> obsList = combo.getObservationsForSource(start, end);
        if (cacheObs && combo.getSourceType() == EntityType.DATASTREAM && !obsList.isEmpty()) {
            Observation lastObs = obsList.get(obsList.size() - 1);
            if (lastObs.getPhenomenonTime().isInterval()) {
                synchronized (obsCache) {
                    obsCache.put(combo.getSourceId(), new WeakReference<>(lastObs));
                }
            }
        }
        return obsList;
    }

    private void calculateAggregate(AggregateCombo combo, Interval interval) throws ServiceFailureException, ProcessException {
        Instant start = interval.getStart();
        Instant end = interval.getEnd();
        List<Observation> sourceObs = findObservations(combo, start, end);
        LOGGER.info("Calculating {} using {} obs for {}.", interval, sourceObs.size(), combo);
        if (sourceObs.isEmpty()) {
            return;
        }
        LOGGER.debug("Obs:        {}/{}.", sourceObs.get(0).getPhenomenonTime(), sourceObs.get(sourceObs.size() - 1).getPhenomenonTime());

        List<BigDecimal> result;
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
        int wantedSize = combo.target.getMultiObservationDataTypes().size();
        while (result.size() > wantedSize) {
            result.remove(result.size() - 1);
        }
        while (result.size() < wantedSize) {
            result.add(null);
        }
        Observation newObs = new Observation(result, combo.target);
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

    private void calculateAggregates(BlockingQueue<CalculationOrder> queue, AggregateCombo combo) throws ServiceFailureException, ProcessException {
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

            createOrderForDirectExecution(queue, combo, Interval.of(calcIntervalStart, calcIntervalEnd));
            calcIntervalStart = calcIntervalEnd;
        }

    }

    private void calculateAggregates(BlockingQueue<CalculationOrder> queue, Collection<AggregateCombo> targets) {
        for (AggregateCombo target : targets) {
            try {
                calculateAggregates(queue, target);
            } catch (ServiceFailureException | ProcessException ex) {
                LOGGER.error("Error calculating for: " + target, ex);
            }
        }
    }

    private void calculateAggregates(AggregationData aggregationData) {
        Map<String, AggregationBase> targets = aggregationData.getCombosByBase();
        final Iterator<AggregationBase> it = targets.values().iterator();
        final List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    BlockingQueue<CalculationOrder> queue = new ArrayBlockingQueue<>(100);
                    orderMerger.addQueue(queue);
                    boolean moreWork = true;
                    while (moreWork) {
                        AggregationBase nextBase;
                        synchronized (it) {
                            if (it.hasNext()) {
                                nextBase = it.next();
                            } else {
                                LOGGER.warn("Nothing more to do...");
                                break;
                            }
                        }
                        calculateAggregates(queue, nextBase.getCombos());
                    }
                    orderMerger.removeQueue(queue);
                }
            });
            threadList.add(t);
            t.start();
        }
        for (Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                LOGGER.error("Interrupted while waiting for threads!");
            }
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
            calculateAggregates(orderQueue, combos);

            // Then add the subscription.
            sourceService.subscribe(path, (String topic, MqttMessage message) -> {
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

    private void createOrderForDirectExecution(BlockingQueue<CalculationOrder> queue, AggregateCombo combo, Interval interval) {
        CalculationOrder order = new CalculationOrder(combo, interval, Instant.now());
        try {
            while (!queue.offer(order, 1, TimeUnit.SECONDS)) {
                LOGGER.warn("Could not offer order for a full second...");
            }
        } catch (InterruptedException exc) {
            LOGGER.warn("Rude wakeup.", exc);
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
        orderQueue = new ArrayBlockingQueue<>(200 * threads);
        orderMerger = new MergeQueue<>(orderQueue);
        orderMerger.start();
        startProcessors();
        calculateAggregates(aggregationData);
        if (!running) {
            stopProcessors(30);
        }
    }

    @Override
    public void startListening() {
        orderQueue = new DelayQueue<>();
        running = true;
        try {
            sourceService.getMqttClient();
            startProcessors();
            if (messageReceptionService == null) {
                messageReceptionService = ProcessorHelper.createProcessors(
                        threads,
                        messagesToHandle, (MessageContext x) -> {
                            messagesCount.decrementAndGet();
                            createOrderFor(x.combos, x.message);
                        },
                        "Receiver");
            }
            createSubscriptions(aggregationData);
        } catch (MqttException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public void stopListening() {
        LOGGER.debug("Stopping ProcessorBatchAggregate...");
        running = false;
        try {
            sourceService.closeMqttClient();
            if (messageReceptionService != null) {
                LOGGER.info("Stopping Receivers...");
                ProcessorHelper.shutdownProcessors(messageReceptionService, messagesToHandle, 5, TimeUnit.SECONDS);
            }
            stopProcessors(5);
        } catch (MqttException ex) {
            LOGGER.error("Problem while disconnecting!", ex);
        }
        LOGGER.debug("Done stopping ProcessorBatchAggregate.");
    }

    private synchronized void startProcessors() {
        if (orderExecutorService == null) {
            orderExecutorService = ProcessorHelper.createProcessors(
                    threads,
                    orderQueue,
                    x -> x.execute(),
                    "Aggregator");
        }
    }

    private synchronized void stopProcessors(long waitSeconds) {
        if (orderMerger != null) {
            orderMerger.stop();
        }
        if (orderExecutorService != null) {
            LOGGER.info("Stopping Processors...");
            ProcessorHelper.shutdownProcessors(orderExecutorService, orderQueue, waitSeconds, TimeUnit.SECONDS);
        }
    }
}
