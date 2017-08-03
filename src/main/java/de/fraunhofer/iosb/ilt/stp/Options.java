/*
 * Copyright (C) 2017 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
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

import de.fraunhofer.iosb.ilt.stp.options.Option;
import de.fraunhofer.iosb.ilt.stp.options.OptionSingle;
import de.fraunhofer.iosb.ilt.stp.options.OptionToggle;
import de.fraunhofer.iosb.ilt.stp.options.ParameterString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class Options {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Options.class);
    private static final Comparator<String> KEY_COMPARATOR = (String o1, String o2) -> {
        if (o1.length() == o2.length()) {
            return String.CASE_INSENSITIVE_ORDER.compare(o1, o2);
        }
        return Integer.compare(o1.length(), o2.length());
    };
    private final Set<String> keys = new HashSet<>();
    private final Map<String, Option> optionMap = new TreeMap<>(KEY_COMPARATOR);
    private final List<Option> options = new ArrayList<>();

    private final OptionToggle noAct;
    private final OptionToggle online;
    private final OptionSingle<String> fileName;

    public Options() {
        noAct = addOption(
                new OptionToggle("-noact", "-n")
                        .setDescription("Read the file and give output, but do not actually post observations."));
        online = addOption(
                new OptionToggle("-online", "-o")
                        .setDescription("Run in on-line mode, listening for changes and processing as needed."));
        fileName = addOption(
                new OptionSingle<String>("-config", "-c")
                        .setParam(new ParameterString("file path", ""))
                        .setDescription("The path to the config json file."));
    }

    public List<Option> getOptions() {
        return options;
    }

    private <T extends Option> T addOption(T o) {
        for (String key : o.getKeys()) {
            if (keys.contains(key)) {
                throw new IllegalStateException("Key " + key + " is defined by more than one option.");
            }
            keys.add(key);
            optionMap.put(key, o);
        }
        options.add(o);
        return o;
    }

    public Options parseArguments(List<String> args) {
        List<String> arguments = new ArrayList<>(args);
        while (!arguments.isEmpty()) {
            String key = arguments.get(0);
            if (!key.startsWith("-")) {
                // Not an option.
                LOGGER.debug("Not an option: {}", key);
                continue;
            }

            Option option = optionMap.get(key);
            if (option == null) {
                for (String optionKey : optionMap.keySet()) {
                    if (key.startsWith(optionKey)) {
                        option = optionMap.get(optionKey);
                        break;
                    }
                }
            }

            if (option == null) {
                LOGGER.debug("Unknown option: {}", key);
                arguments.remove(0);
            } else {
                option.consume(arguments);
            }
        }
        return this;
    }

    public OptionToggle getNoAct() {
        return noAct;
    }

    public OptionToggle getOnline() {
        return online;
    }

    public OptionSingle<String> getFileName() {
        return fileName;
    }

}
