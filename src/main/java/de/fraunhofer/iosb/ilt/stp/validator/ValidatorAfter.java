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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorInt;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import java.time.Instant;
import java.util.Map;
import org.threeten.extra.Days;
import org.threeten.extra.Minutes;

/**
 * Checks if the observation has a phenomenonTime that is later than the latest
 * in the configured datastream.
 *
 * @author scf
 */
public class ValidatorAfter implements Validator {

    private EditorMap<Map<String, Object>> editor;
    private EditorInt editorDays;
    private EditorInt editorMinutes;

    private Instant refTime;

    @Override
    public boolean isValid(Observation obs) throws ProcessException {
        TimeObject phenomenonTime = obs.getPhenomenonTime();
        Instant obsInstant;
        if (phenomenonTime.isInterval()) {
            obsInstant = phenomenonTime.getAsInterval().getStart();
        } else {
            obsInstant = phenomenonTime.getAsDateTime().toInstant();
        }
        return refTime.isBefore(obsInstant);
    }

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> ce) {
        getConfigEditor(context, edtCtx).setConfig(config);
        Instant now = Instant.now();
        refTime = now.minus(Days.of(editorDays.getValue()));
        refTime = refTime.minus(Minutes.of(editorMinutes.getValue()));
    }

    @Override
    public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();

            editorDays = new EditorInt(0, 999999, 1, 1, "days", "The number of days before now.");
            editor.addOption("days", editorDays, false);

            editorMinutes = new EditorInt(0, 999999, 1, 0, "minutes", "The number of minutes before now.");
            editor.addOption("minutes", editorMinutes, false);
        }
        return editor;
    }

}
