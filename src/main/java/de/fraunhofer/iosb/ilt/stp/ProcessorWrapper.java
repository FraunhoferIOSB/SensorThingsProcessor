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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.Configurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorSubclass;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class ProcessorWrapper implements Configurable<Void, Void> {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorWrapper.class);

    private EditorMap<Map<String, Object>> editor;
    private EditorSubclass<Void, Void, Processor> editorProcessor;

    private boolean noAct = false;
    private boolean online = false;
    private boolean daemon = false;
    private Processor processor;
    private Thread shutdownHook;

    @Override
    public void configure(JsonElement config, Void context, Void edtCtx, ConfigEditor<?> ce) throws ConfigurationException {
        getConfigEditor(context, edtCtx).setConfig(config);
        processor = editorProcessor.getValue();
    }

    @Override
    public EditorMap<Map<String, Object>> getConfigEditor(Void context, Void edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();

            editorProcessor = new EditorSubclass<>(null, null, Processor.class, "Processor", "The class to use for processing.");
            editor.addOption("uploader", editorProcessor, false);
        }
        return editor;
    }

    public void stopProcess() {
        LOGGER.debug("Telling processor to stop listening...");
        if (processor != null) {
            removeShutdownHook();
            processor.stopListening();
        }
    }

    private synchronized void addShutdownHook() {
        if (this.shutdownHook == null) {
            LOGGER.debug("Creating shutdown hook...");
            this.shutdownHook = new Thread(() -> {
                LOGGER.info("Shutting down...");
                try {
                    if (processor != null) {
                        processor.stopListening();
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Exception stopping listeners.", ex);
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    private synchronized void removeShutdownHook() {
        if (shutdownHook != null) {
            LOGGER.debug("Removing shutdown hook...");
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
    }

    private void doProcess() throws ProcessException, ServiceFailureException {
        Calendar start = Calendar.getInstance();

        processor.setNoAct(noAct);
        if (online || daemon) {
            LOGGER.info("Processing in on-line mode...");
            addShutdownHook();
            processor.startListening();
        } else {
            LOGGER.info("Processing in batch mode...");
            processor.process();
        }

        Calendar now = Calendar.getInstance();
        double seconds = 1e-3 * (now.getTimeInMillis() - start.getTimeInMillis());
        LOGGER.info("Done in {}s.", String.format("%.1f", seconds));
    }

    public void doProcess(Options options) {
        this.noAct = options.getNoAct().isSet();
        this.online = options.getOnline().isSet();
        this.daemon = options.getDaemon().isSet();
        String fileName = options.getFileName().getValue();
        File configFile = new File(fileName);
        try {
            String config = FileUtils.readFileToString(configFile, "UTF-8");
            doProcess(config, noAct, online);
        } catch (IOException ex) {
            LOGGER.error("Failed to read config file.", ex);
        }
    }

    public void doProcess(String config, boolean noAct, boolean online) {
        this.noAct = noAct;
        this.online = online;
        try {
            JsonElement json = new JsonParser().parse(config);
            configure(json, null, null, null);
            processor.setNoAct(noAct);
            doProcess();
        } catch (JsonSyntaxException exc) {
            LOGGER.error("Failed to parse {}", config);
            LOGGER.debug("Failed to parse.", exc);
        } catch (ConfigurationException | ProcessException | ServiceFailureException exc) {
            LOGGER.error("Failed to import.", exc);
        }
    }

    public static void importConfig(String config, boolean noAct, boolean online) {
        ProcessorWrapper wrapper = new ProcessorWrapper();
        wrapper.doProcess(config, noAct, online);
    }

    public static void importCmdLine(List<String> arguments) throws URISyntaxException, IOException, MalformedURLException, ServiceFailureException {
        Options options = new Options().parseArguments(arguments);

        ProcessorWrapper wrapper = new ProcessorWrapper();
        wrapper.doProcess(options);
        boolean isOnline = options.getOnline().isSet();
        if (isOnline) {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"))) {
                LOGGER.warn("Press Enter to exit.");
                input.read();
            }
            System.exit(0);
        }
    }

}
