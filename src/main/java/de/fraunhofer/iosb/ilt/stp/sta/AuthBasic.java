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
package de.fraunhofer.iosb.ilt.stp.sta;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorMap;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class AuthBasic implements AuthMethod {

    /**
     * The logger for this class.
     */
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AuthBasic.class);
    private EditorMap configEditor;
    private EditorString editorUsername;
    private EditorString editorPassword;

    @Override
    public void configure(JsonElement config, Object context, Object edtCtx) {
        getConfigEditor(context, edtCtx).setConfig(config);
    }

    @Override
    public EditorMap<?> getConfigEditor(Object context, Object edtCtx) {
        if (configEditor == null) {
            configEditor = new EditorMap();
            editorUsername = new EditorString("username", 1, "Username", "The username to use for authentication.");
            configEditor.addOption("username", editorUsername, false);
            editorPassword = new EditorString("*****", 1, "Password", "The password to use for authentication.");
            configEditor.addOption("password", editorPassword, false);
        }
        return configEditor;
    }

    @Override
    public void setAuth(SensorThingsService service) {
        try {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            URL url = service.getEndpoint().toURL();
            credsProvider.setCredentials(
                    new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(editorUsername.getValue(), editorPassword.getValue()));
            CloseableHttpClient httpclient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .build();
            service.setClient(httpclient);
        } catch (MalformedURLException ex) {
            LOGGER.error("Failed to initialise basic auth.", ex);
        }
    }

}
