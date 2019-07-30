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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorNull;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Id;
import de.fraunhofer.iosb.ilt.sta.model.MultiDatastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks if the observation has a phenomenonTime that is later than the latest
 * in the configured datastream.
 *
 * @author scf
 */
public class ValidatorNewer implements Validator {

    private EditorNull editor = new EditorNull("Validator", "Validates the observation against the datastream");
    private final Map<Id, Instant> datastreamCache = new HashMap<>();
    private final Map<Id, Instant> multiDatastreamCache = new HashMap<>();

    @Override
    public boolean isValid(Observation obs) throws ProcessException {
        try {
            Instant latest;
            Datastream ds = obs.getDatastream();
            if (ds == null) {
                MultiDatastream mds = obs.getMultiDatastream();
                if (mds == null) {
                    throw new ProcessException("Observation has no Datastream of Multidatastream set!");
                }
                latest = getTimeForMultiDatastream(mds);
            } else {
                latest = getTimeForDatastream(ds);
            }
            TimeObject phenomenonTime = obs.getPhenomenonTime();
            Instant obsInstant;
            if (phenomenonTime.isInterval()) {
                obsInstant = phenomenonTime.getAsInterval().getStart();
            } else {
                obsInstant = phenomenonTime.getAsDateTime().toInstant();
            }
            return latest.isBefore(obsInstant);
        } catch (ServiceFailureException ex) {
            throw new ProcessException("Failed to validate.", ex);
        }
    }

    private Instant getTimeForDatastream(Datastream ds) throws ServiceFailureException {
        Id dsId = ds.getId();
        Instant latest = datastreamCache.get(dsId);
        if (latest == null) {
            Observation firstObs = ds.observations().query().select("@iot.id", "phenomenonTime").orderBy("phenomenonTime desc").first();
            if (firstObs == null) {
                latest = Instant.MIN;
            } else {
                TimeObject phenomenonTime = firstObs.getPhenomenonTime();
                if (phenomenonTime.isInterval()) {
                    latest = phenomenonTime.getAsInterval().getStart();
                } else {
                    latest = phenomenonTime.getAsDateTime().toInstant();
                }
            }
            datastreamCache.put(dsId, latest);
        }
        return latest;
    }

    private Instant getTimeForMultiDatastream(MultiDatastream mds) throws ServiceFailureException {
        Id dsId = mds.getId();
        Instant latest = multiDatastreamCache.get(dsId);
        if (latest == null) {
            Observation firstObs = mds.observations().query().select("@iot.id", "phenomenonTime").orderBy("phenomenonTime desc").first();
            if (firstObs == null) {
                latest = Instant.MIN;
            } else {
                TimeObject phenomenonTime = firstObs.getPhenomenonTime();
                if (phenomenonTime.isInterval()) {
                    latest = phenomenonTime.getAsInterval().getStart();
                } else {
                    latest = phenomenonTime.getAsDateTime().toInstant();
                }
            }
            multiDatastreamCache.put(dsId, latest);
        }
        return latest;
    }

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> ce) {
    }

    @Override
    public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
        return editor;
    }

}
