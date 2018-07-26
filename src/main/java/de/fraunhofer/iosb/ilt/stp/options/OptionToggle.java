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

import java.util.List;

/**
 * An option that has value true if present, false if not.
 *
 * @author scf
 */
public class OptionToggle extends OptionBase {

	public OptionToggle(String... keys) {
		super(keys);
	}

	@Override
	public void consume(List<String> args) {
		String first = args.remove(0);
		String matchedKey = findKey(first);
		if (matchedKey.isEmpty()) {
			throw new IllegalStateException("First argument does not mach any key!");
		}
		setSet(true);
	}

	@Override
	public OptionToggle setDescription(String... description) {
		super.setDescription(description);
		return this;
	}

}
