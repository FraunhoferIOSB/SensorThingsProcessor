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
package de.fraunhofer.iosb.ilt.stp.aggregation;

import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.TimeObject;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 *
 * @author scf
 */
public class Utils {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    public static final String KEY_AGGREGATE_FOR = "aggregateFor";
    public static final String LB = Pattern.quote("[");
    public static final String RB = Pattern.quote("]");
    public static final Pattern POSTFIX_PATTERN = Pattern.compile("(.+)" + LB + "([0-9]+ [a-zA-Z]+)" + RB);

    private Utils() {
        // Not to be instantiated.
    }

    public static Instant getPhenTimeStart(Observation obs) {
        TimeObject phenTime = obs.getPhenomenonTime();
        return getPhenTimeStart(phenTime);
    }

    public static Instant getPhenTimeStart(TimeObject phenTime) {
        if (phenTime.isInterval()) {
            return phenTime.getAsInterval().getStart();
        }
        return phenTime.getAsDateTime().toInstant();
    }

    public static Instant getPhenTimeEnd(Observation obs) {
        TimeObject phenTime = obs.getPhenomenonTime();
        return getPhenTimeEnd(phenTime);
    }

    public static Instant getPhenTimeEnd(TimeObject phenTime) {
        if (phenTime.isInterval()) {
            return phenTime.getAsInterval().getEnd();
        }
        return phenTime.getAsDateTime().toInstant();
    }
}
