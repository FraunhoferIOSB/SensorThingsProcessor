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
package de.fraunhofer.iosb.ilt.stp.validator;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.Configurable;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorNull;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;

/**
 *
 * @author scf
 */
public interface Validator extends Configurable<SensorThingsService, Object> {

    public boolean isValid(Observation obs) throws ProcessException;

    /**
     * Always returns true.
     */
    public static class ValidatorNull implements Validator {

        @Override
        public boolean isValid(Observation obs) throws ProcessException {
            return true;
        }

        @Override
        public void configure(JsonElement config, SensorThingsService context, Object edtCtx) {
        }

        @Override
        public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
            return new EditorNull();
        }

    }
}
