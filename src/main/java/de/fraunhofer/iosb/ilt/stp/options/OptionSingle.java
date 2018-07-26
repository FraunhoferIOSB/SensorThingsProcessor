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

import java.util.Arrays;
import java.util.List;

/**
 * An option that takes exactly one value.
 *
 * @param <T>
 */
public class OptionSingle<T> extends OptionBase {

	public Parameter<T> parameter;

	/**
	 * @param keys The keys, longest first!
	 */
	public OptionSingle(String... keys) {
		super(keys);
	}

	public OptionSingle<T> setParam(Parameter<T> param) {
		this.parameter = param;
		return this;
	}

	public String findValue(List<String> args) {
		String first = args.remove(0);
		String matchedKey = findKey(first);
		if (matchedKey.isEmpty()) {
			throw new IllegalStateException("First argument does not mach any key! " + first);
		}
		if (first.length() > matchedKey.length()) {
			// parameter is glued to key
			return first.substring(matchedKey.length());
		}
		return args.remove(0);
	}

	@Override
	public void consume(List<String> args) {
		parameter.parse(findValue(args));
		setSet(true);
	}

	public T getValue() {
		return parameter.getValue();
	}

	@Override
	public OptionSingle<T> setDescription(String... description) {
		super.setDescription(description);
		return this;
	}

	public List<Parameter> getParameters() {
		return Arrays.asList(new Parameter[]{parameter});
	}

}
