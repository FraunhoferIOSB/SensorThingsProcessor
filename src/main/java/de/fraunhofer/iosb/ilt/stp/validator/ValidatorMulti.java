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
package de.fraunhofer.iosb.ilt.stp.validator;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.EditorFactory;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorList;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorSubclass;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import de.fraunhofer.iosb.ilt.stp.ProcessException;
import java.util.List;
import java.util.Map;

/**
 *
 * @author scf
 */
public class ValidatorMulti implements Validator {

    private EditorMap<Map<String, Object>> editor;
    private EditorList<Validator, EditorSubclass<SensorThingsService, Object, Validator>> editorValidators;

    public List<Validator> validators;

    @Override
    public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> ce) throws ConfigurationException {
        getConfigEditor(context, edtCtx).setConfig(config);
        validators = editorValidators.getValue();
    }

    @Override
    public ConfigEditor<?> getConfigEditor(SensorThingsService context, Object edtCtx) {
        if (editor == null) {
            editor = new EditorMap<>();

            EditorFactory<EditorSubclass<SensorThingsService, Object, Validator>> factory;
            factory = () -> {
                return new EditorSubclass<>(context, edtCtx, Validator.class, "Validators", "The validators to use.");
            };
            editorValidators = new EditorList<>(factory, "Validators", "The validators to use.");
            editor.addOption("validators", editorValidators, false);
        }
        return editor;
    }

    @Override
    public boolean isValid(Observation obs) throws ProcessException {
        for (Validator validator : validators) {
            if (!validator.isValid(obs)) {
                return false;
            }
        }
        return true;
    }

}
