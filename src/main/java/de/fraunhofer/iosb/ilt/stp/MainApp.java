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

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.stp.options.Option;
import de.fraunhofer.iosb.ilt.stp.options.Parameter;
import de.fraunhofer.iosb.ilt.stp.utils.GitVersionInfo;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class MainApp {

    static final public String TAG_CONFIGURATION = "processor_config";
    static final public String TAG_ONLINE = "processor_online";
    static final public String TAG_DRY_RUN = "processor_dryrun";
    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MainApp.class);

    private static boolean getEnv(String name, boolean dflt) {
        String value = System.getenv(name);
        if (Utils.isNullOrEmpty(value)) {
            LOGGER.info("Parameter {} not set, using default value: {}", name, dflt);
            return dflt;
        }
        LOGGER.info("Parameter {} set with value: {}", name, value);
        return Boolean.parseBoolean(value);
    }

    private static String getEnv(String name, String dflt) {
        String value = System.getenv(name);
        if (Utils.isNullOrEmpty(value)) {
            LOGGER.info("Parameter {} not set, using default value: {}", name, dflt);
            return dflt;
        }
        LOGGER.info("Parameter {} set with value: {}", name, value);
        return value;
    }

    /**
     * @param args the command line arguments
     * @throws IOException
     * @throws URISyntaxException
     * @throws java.net.MalformedURLException
     * @throws ServiceFailureException
     */
    public static void main(String[] args) throws URISyntaxException, IOException, MalformedURLException, ServiceFailureException {
        GitVersionInfo.logGitInfo();
        String configuration = getEnv(TAG_CONFIGURATION, "");
        boolean onLine = getEnv(TAG_ONLINE, true);
        boolean dryRun = getEnv(TAG_DRY_RUN, false);
        if (!Utils.isNullOrEmpty(configuration)) {
            ProcessorWrapper.importConfig(configuration, dryRun, onLine);
            return;
        }

        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        if (arguments.isEmpty()) {
            showHelp();
            ProcessorGui.main(args);
        } else if (arguments.contains("--help") || arguments.contains("-help") || arguments.contains("-h")) {
            showHelp();
            System.exit(0);
        } else {
            ProcessorWrapper.importCmdLine(arguments);
        }
        LOGGER.debug("Main thread done.");
    }

    public static void showHelp() {
        Options options = new Options();
        for (Option option : options.getOptions()) {
            StringBuilder text = new StringBuilder();
            for (String key : option.getKeys()) {
                text.append(key).append(" ");
            }
            for (Parameter param : option.getParameters()) {
                text.append("[").append(param.getName()).append("] ");
            }
            text.append(":\n");
            for (String descLine : option.getDescription()) {
                text.append("    ").append(descLine).append("\n");
            }
            System.out.println(text.toString());
        }
    }

}
