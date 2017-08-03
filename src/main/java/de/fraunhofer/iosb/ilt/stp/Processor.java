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

import de.fraunhofer.iosb.ilt.configurable.Configurable;

/**
 *
 * @author scf
 */
public interface Processor extends Configurable<Void, Void> {

    /**
     * Set the no-act or dry-run state. If set to true, the processor makes no
     * changes.
     *
     * @param noAct the no-act or dry-run state.
     */
    public void setNoAct(boolean noAct);

    /**
     * Run the process.
     */
    public void process();

    /**
     * Start threads that listen for changes and process on-the-fly.
     */
    public void startListening();

    /**
     * Stop threads.
     */
    public void stopListening();

}
