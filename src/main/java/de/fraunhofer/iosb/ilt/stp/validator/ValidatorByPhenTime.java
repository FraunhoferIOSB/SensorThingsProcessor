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
package de.fraunhofer.iosb.ilt.stp.validator;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.MultiDatastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class ValidatorByPhenTime implements Validator {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ValidatorByPhenTime.class);
    private EditorMap<Map<String, Object>> editor;
    private EditorBoolean editorUpdate;

    private boolean update;

    private boolean resultCompare(Object one, Object two) {
        if (one.equals(two)) {
            return true;
        }
        try {
            if (one instanceof Long && two instanceof Integer) {
                return ((Long) one).equals(new Long((Integer) two));
            }
            if (two instanceof Long && one instanceof Integer) {
                return ((Long) two).equals(new Long((Integer) one));
            }
            if (one instanceof BigDecimal) {
                return ((BigDecimal) one).equals(new BigDecimal(two.toString()));
            }
            if (two instanceof BigDecimal) {
                return ((BigDecimal) two).equals(new BigDecimal(one.toString()));
            }
            if (one instanceof Collection && two instanceof Collection) {
                Collection cOne = (Collection) one;
                Collection cTwo = (Collection) two;
                Iterator iTwo = cTwo.iterator();
                for (Object itemOne : cOne) {
                    if (!iTwo.hasNext()) {
                        // Collection one is longer than two
                        return false;
                    }
                    if (!resultCompare(itemOne, iTwo.next())) {
                        return false;
                    }
                }
                if (iTwo.hasNext()) {
                    // Collection two is longer than one.
                    return false;
                }
                return true;
            }
        } catch (NumberFormatException e) {
            LOGGER.trace("Not both bigdecimal.", e);
            // not both bigDecimal.
        }
        return false;
    }

    @Override
    public boolean isValid(Observation obs) throws ProcessException {
        try {
            Datastream ds = obs.getDatastream();
            if (ds != null) {
                Observation first = ds.observations().query().select("@iot.id", "result").filter("phenomenonTime eq " + obs.getPhenomenonTime().toString()).first();
                if (first == null) {
                    return true;
                } else {
                    if (!resultCompare(obs.getResult(), first.getResult())) {
                        LOGGER.warn("Observation {} with given phenomenonTime {} exists, but result not the same. {} != {}.", first.getId(), obs.getPhenomenonTime(), obs.getResult(), first.getResult());
                        if (update) {
                            obs.setId(first.getId());
                            return true;
                        }
                    }
                    return false;
                }
            }
            MultiDatastream mds = obs.getMultiDatastream();
            if (mds != null) {
                Observation first = mds.observations().query().select("@iot.id", "result").filter("phenomenonTime eq " + obs.getPhenomenonTime().toString()).first();
                if (first == null) {
                    return true;
                } else {
                    if (!resultCompare(obs.getResult(), first.getResult())) {
                        if (update) {
                            LOGGER.debug("Observation {} with given phenomenonTime {} exists. Result not the same. Updating. {} != {}.", first.getId(), obs.getPhenomenonTime(), obs.getResult(), first.getResult());
                            obs.setId(first.getId());
                            return true;
                        } else {
                            LOGGER.warn("Observation {} with given phenomenonTime {} exists, but result not the same. {} != {}.", first.getId(), obs.getPhenomenonTime(), obs.getResult(), first.getResult());
                        }
                    }
                    return false;
                }
            }
            throw new ProcessException("Observation has no Datastream of Multidatastream set!");
        } catch (ServiceFailureException ex) {
            throw new ProcessException("Failed to validate.", ex);
        }
    }

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx) {
        getConfigEditor(context, edtCtx).setConfig(config);
        update = editorUpdate.getValue();
    }

    @Override
    public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();

            editorUpdate = new EditorBoolean(false, "Update", "Update results that are different.");
            editor.addOption("update", editorUpdate, false);
        }
        return editor;
    }
}
