/*
 * Copyright (C) 2020 Fraunhofer IOSB
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
package de.fraunhofer.iosb.ilt.stp.utils;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/**
 * A logger that regularly logs a status message, if the status has changed.
 *
 * @author hylke
 */
public class ChangingStatusLogger {

    /**
     * Implementations MUST override the equals method.
     */
    public static interface ChangingStatus {

        /**
         * Get a copy of the current status.
         *
         * @return A copy of the current status.
         */
        public ChangingStatus getCurrentStatus();

        /**
         * Get the parameters to pass to the logger when logging a message.
         *
         * @return The parameters to pass to the logger.
         */
        public Object[] getLogParams();

    }

    public static class ChangingStatusDefault implements ChangingStatus {

        private final Object[] status;

        public ChangingStatusDefault(Object[] status) {
            this.status = status;
        }

        @Override
        public ChangingStatus getCurrentStatus() {
            return new ChangingStatusDefault(Arrays.copyOf(status, status.length));
        }

        @Override
        public final Object[] getLogParams() {
            return status;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            return 53 * hash + Arrays.deepHashCode(this.status);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ChangingStatusDefault other = (ChangingStatusDefault) obj;
            return Arrays.deepEquals(this.status, other.status);
        }

    }

    private long logIntervalMs = 1000;

    private final Logger logger;
    private final String message;
    private final ChangingStatus statusCurrent;
    private ChangingStatus statusPrevious;

    private final ScheduledExecutorService executor;
    private final Runnable task;
    private boolean running = false;

    /**
     * Create a new logger, with the given logger, message and status. The
     * status should match the message in that the number of placeholders in the
     * message must be the same as the number of items in the status.
     *
     * @param logger The logger to log to.
     * @param message The message to log.
     * @param status The status to use when logging the message.
     */
    public ChangingStatusLogger(Logger logger, String message, ChangingStatus status) {
        this.logger = logger;
        this.message = message;
        this.statusCurrent = status;
        this.statusPrevious = status.getCurrentStatus();

        executor = Executors.newSingleThreadScheduledExecutor();
        task = this::maybeLog;

    }

    public ChangingStatusLogger setLogIntervalMs(long logIntervalMs) {
        this.logIntervalMs = logIntervalMs;
        return this;
    }

    public ChangingStatusLogger start() {
        if (running) {
            return this;
        }
        running = true;
        executor.scheduleAtFixedRate(task, logIntervalMs, logIntervalMs, TimeUnit.MILLISECONDS);
        return this;
    }

    public void stop() {
        executor.shutdown();
        running = false;
    }

    private void maybeLog() {
        ChangingStatus statusNew = statusCurrent.getCurrentStatus();
        if (!statusNew.equals(statusPrevious)) {
            statusPrevious = statusNew;
            executeLog();
        }
    }

    private void executeLog() {
        logger.info(message, statusPrevious.getLogParams());
    }
}
