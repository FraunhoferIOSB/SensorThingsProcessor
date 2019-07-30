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
package de.fraunhofer.iosb.ilt.stp.sta;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorNull;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

/**
 *
 * @author scf
 */
public class AuthNone implements AuthMethod {

    @Override
    public void configure(JsonElement config, Object context, Object edtCtx, ConfigEditor<?> ce) {
        // Nothing to configure
    }

    @Override
    public EditorNull getConfigEditor(Object context, Object edtCtx) {
        return new EditorNull();
    }

    @Override
    public void setAuth(SensorThingsService service) {
        // Do Nothing.
    }

}
