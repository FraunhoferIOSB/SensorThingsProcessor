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
package de.fraunhofer.iosb.ilt.stp.options;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author scf
 */
public interface Option {

	/**
	 * A List because order is important.
	 *
	 * @return The keys of the option, in the order they are matched.
	 */
	public List<String> getKeys();

	public boolean matches(String arg);

	/**
	 * Will consume arguments from the list until satisfied. Will always consume
	 * at least the first argument, which should start with, or be, the key.
	 *
	 * @param args The arguments to consume at least one of.
	 */
	public void consume(List<String> args);

	/**
	 * Returns true if the option was explicitly set, false otherwise. If the
	 * option was not set, it might still return a default value.
	 *
	 * @return true if the option was set.
	 */
	public boolean isSet();

	public String[] getDescription();

	public default List<Parameter> getParameters() {
		return new ArrayList<>();
	}

}
