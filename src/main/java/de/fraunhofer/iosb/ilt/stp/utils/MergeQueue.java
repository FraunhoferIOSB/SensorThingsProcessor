/*
 * Copyright (C) 2018 Fraunhofer IOSB
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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 * @param <T> The type of object on the queues.
 */
public class MergeQueue<T> {

    /**
     * The logger for this class.
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MergeQueue.class);

    private List<BlockingQueue<T>> queues = new CopyOnWriteArrayList<>();
    private final BlockingQueue<T> outputQueue;
    private boolean running = false;
    private Thread loopThread;

    public MergeQueue(BlockingQueue<T> outputQueue) {
        this.outputQueue = outputQueue;
    }

    public void addQueue(BlockingQueue<T> queue) {
        queues.add(queue);
    }

    public void removeQueue(BlockingQueue<T> queue) {
        queues.remove(queue);
    }

    public BlockingQueue<T> getOutputQueue() {
        return outputQueue;
    }

    private void checkLoop() {
        while (running) {
            checkQueues();
        }
        loopThread = null;
    }

    private void checkQueues() {
        boolean allEmpty = true;
        for (BlockingQueue<T> queue : queues) {
            if (queue.isEmpty()) {
                continue;
            }
            allEmpty = false;
            try {
                outputQueue.offer(queue.take(), 1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                LOGGER.warn("Rude wakeup!", ex);
            }
        }
        if (allEmpty) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                LOGGER.warn("Rude wakeup!", ex);
            }
        }
    }

    public synchronized void start() {
        if (loopThread != null) {
            LOGGER.error("Already a thread there.");
            return;
        }
        running = true;
        loopThread = new Thread(this::checkLoop);
        loopThread.start();
    }

    public synchronized void stop() {
        running = false;
    }

    public boolean isRunning() {
        return (loopThread != null);
    }
}
