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
package de.fraunhofer.iosb.ilt.stp.options;

/**
 *
 * @author scf
 */
public class ParameterLong implements Parameter<Long> {

	private final String name;
	private Long value;

	/**
	 * @param name The name of the parameter used in the help.
	 * @param value The default value.
	 */
	public ParameterLong(String name, Long value) {
		this.name = name;
		this.value = value;
	}

	@Override
	public Long parse(String arg) {
		value = Long.parseLong(arg);
		return value;
	}

	@Override
	public Long getValue() {
		return value;
	}

	@Override
	public String getName() {
		return name;
	}
}
