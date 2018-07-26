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
import java.util.Collections;
import java.util.List;

/**
 *
 * @author scf
 */
public abstract class OptionBase implements Option {

	/**
	 * A List because order is important.
	 */
	private List<String> keys = new ArrayList<>();
	private boolean set = false;
	private String[] description = {""};

	/**
	 *
	 * @param keys The keys, longest first!
	 */
	public OptionBase(String... keys) {
		for (String key : keys) {
			this.keys.add(key.toLowerCase());
		}
	}

	@Override
	public boolean matches(String arg) {
		for (String key : keys) {
			if (arg.startsWith(key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the first key that matches the argument. Never returns null.
	 *
	 * @param arg The argument to match.
	 * @return The first key found, or an empty string if none matched.
	 */
	public String findKey(String arg) {
		if (arg == null) {
			throw new IllegalArgumentException("Null arguments not allowed.");
		}
		arg = arg.toLowerCase();
		for (String key : getKeys()) {
			if (arg.startsWith(key)) {
				return key;
			}
		}
		return "";
	}

	@Override
	public List<String> getKeys() {
		return Collections.unmodifiableList(keys);
	}

	/**
	 * @return the description
	 */
	@Override
	public String[] getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 * @return this
	 */
	public OptionBase setDescription(String... description) {
		this.description = description;
		return this;
	}

	/**
	 * @return the set
	 */
	@Override
	public boolean isSet() {
		return set;
	}

	/**
	 * @param set the set to set
	 */
	protected void setSet(boolean set) {
		this.set = set;
	}

}
