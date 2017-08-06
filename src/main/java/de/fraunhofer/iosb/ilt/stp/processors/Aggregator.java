package de.fraunhofer.iosb.ilt.stp.processors;

import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

/**
 *
 * @author Hylke van der Schaaf
 */
public class Aggregator {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Aggregator.class);

    private int getScale(Number number) {
        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).scale();
        } else if (number instanceof Integer || number instanceof Long) {
            return new BigDecimal(number.longValue()).scale();
        }
        return new BigDecimal(number.doubleValue()).scale();
    }

    private Number handleResult(Object result) {
        if (result instanceof Number) {
            Number number = (Number) result;
            return number;
        } else if (result instanceof List) {
            List list = (List) result;
            if (list.isEmpty()) {
                return null;
            }
            return handleResult(list.get(0));
        } else {
            LOGGER.trace("Unknow result type: {}", result.getClass().getName());
        }
        return null;
    }

    private Instant handleTime(TimeObject phenTime) {
        if (phenTime.isInterval()) {
            Interval interval = phenTime.getAsInterval();
            return interval.getStart().plus(interval.toDuration().dividedBy(2));
        }
        return phenTime.getAsDateTime().toInstant();
    }

    public BigDecimal[] calculateAggregateResultFromOriginals(Interval interval, List<Observation> sourceObs) {
        BigDecimal[] result;
        int scale = 0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        Number prevResult = null;
        long prevMillis = 0;
        long curMillis = 0;
        long startMillis = interval.getStart().toEpochMilli();
        long endMillis = interval.getEnd().toEpochMilli();
        long totalMillis = endMillis - startMillis;
        double avg = 0;
        double curResult = 0;
        for (Observation obs : sourceObs) {
            Number number = handleResult(obs.getResult());
            if (number == null) {
                LOGGER.warn("Empty result in {}", obs);
                continue;
            }
            scale = Math.max(getScale(number), scale);

            curResult = handleResult(obs.getResult()).doubleValue();
            Instant curTime = handleTime(obs.getPhenomenonTime());
            curMillis = curTime.toEpochMilli();
            if (prevResult == null) {
                long deltaMillis = curMillis - startMillis;
                avg += curResult * deltaMillis / totalMillis;
            } else {
                long deltaMillis = curMillis - prevMillis;
                avg += 0.5 * (curResult + prevResult.doubleValue()) * deltaMillis / totalMillis;
            }
            prevMillis = curMillis;
            prevResult = curResult;
            stats.addValue(number.doubleValue());
        }
        long deltaMillis = endMillis - curMillis;
        avg += curResult * deltaMillis / totalMillis;

        BigDecimal average = BigDecimal.valueOf(avg).setScale(scale, RoundingMode.HALF_UP);
        result = new BigDecimal[]{
            average,
            new BigDecimal(stats.getMin()).setScale(scale, RoundingMode.HALF_UP),
            new BigDecimal(stats.getMax()).setScale(scale, RoundingMode.HALF_UP),
            new BigDecimal(stats.getStandardDeviation()).setScale(scale, RoundingMode.HALF_UP)
        };
        return result;
    }

    public BigDecimal[] calculateAggregateResultFromAggregates(List<Observation> sourceObs) {
        BigDecimal[] result;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int scale = 0;
        for (Observation obs : sourceObs) {
            Object input = obs.getResult();
            if (input instanceof List) {
                List list = (List) input;
                Number number = handleResult(list.get(0));
                scale = Math.max(getScale(number), scale);

                stats.addValue(number.doubleValue());
                min = Math.min(min, handleResult(list.get(1)).doubleValue());
                max = Math.max(max, handleResult(list.get(2)).doubleValue());
            } else {
                String type = input == null ? "null" : input.getClass().getName();
                LOGGER.error("Aggregate input of obs {} should be a List, not a {}", obs.getId(), type);
                throw new IllegalArgumentException("Expected List, got " + type);
            }
        }
        result = new BigDecimal[]{
            new BigDecimal(stats.getMean()).setScale(scale, RoundingMode.HALF_UP),
            new BigDecimal(min).setScale(scale, RoundingMode.HALF_UP),
            new BigDecimal(max).setScale(scale, RoundingMode.HALF_UP),
            new BigDecimal(stats.getStandardDeviation()).setScale(scale, RoundingMode.HALF_UP)
        };
        return result;
    }
}
