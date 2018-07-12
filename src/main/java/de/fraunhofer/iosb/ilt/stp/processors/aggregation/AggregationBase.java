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
package de.fraunhofer.iosb.ilt.stp.processors.aggregation;

import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.MultiDatastream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class AggregationBase {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AggregationBase.class);

    private final StringProperty baseName;
    private Datastream baseDatastream;
    private MultiDatastream baseMultiDatastream;

    private final Set<AggregateCombo> combos = new TreeSet<>();
    private final Map<AggregationLevel, AggregateCombo> combosByLevel = new HashMap<>();
    private final Map<AggregationLevel, BooleanProperty> levelToggles = new HashMap<>();
    private final Map<AggregationLevel, Boolean> wantedLevels = new HashMap<>();

    public AggregationBase(String baseName) {
        this.baseName = new SimpleStringProperty(baseName);
    }

    public AggregationBase(StringProperty baseName, Datastream baseDatastream, MultiDatastream baseMultiDatastream) {
        this.baseName = baseName;
        this.baseDatastream = baseDatastream;
        this.baseMultiDatastream = baseMultiDatastream;
    }

    public String getBaseName() {
        return baseName.get();
    }

    public StringProperty getBaseNameProperty() {
        return baseName;
    }

    public BooleanProperty getLevelProperty(final AggregationLevel level) {
        BooleanProperty property = levelToggles.get(level);
        if (property == null) {
            boolean hasProp = combosByLevel.containsKey(level);
            property = new SimpleBooleanProperty(hasProp);
            levelToggles.put(level, property);
            property.addListener(
                    (ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
                        toggleLevel(level, newValue);
                    });
        }
        return property;
    }

    private void toggleLevel(final AggregationLevel level, boolean toValue) {
        if (toValue) {
            LOGGER.info("Adding level {} to base {}.", level, baseName.get());
            wantedLevels.put(level, true);
        } else {
            LOGGER.info("Removing level {} from base {}.", level, baseName.get());
            wantedLevels.put(level, false);
        }
    }

    public Set<AggregateCombo> getCombos() {
        return combos;
    }

    public Map<AggregationLevel, AggregateCombo> getCombosByLevel() {
        return combosByLevel;
    }

    public void addCombo(AggregateCombo combo) {
        AggregationLevel level = combo.level;

        combos.add(combo);

        AggregateCombo old = combosByLevel.put(level, combo);
        if (old != null) {
            LOGGER.warn("Multiple combos of level {} found for base {}.", level, getBaseName());
        }

        BooleanProperty presentProp = levelToggles.get(level);
        if (presentProp != null) {
            presentProp.set(true);
        }
    }

    public Map<AggregationLevel, BooleanProperty> getLevelToggles() {
        return levelToggles;
    }

    public Map<AggregationLevel, Boolean> getWantedLevels() {
        return wantedLevels;
    }

    public Datastream getBaseDatastream() {
        return baseDatastream;
    }

    public void setBaseDatastream(Datastream baseDatastream) {
        this.baseDatastream = baseDatastream;
    }

    public MultiDatastream getBaseMultiDatastream() {
        return baseMultiDatastream;
    }

    public void setBaseMultiDatastream(MultiDatastream baseMultiDatastream) {
        this.baseMultiDatastream = baseMultiDatastream;
    }

}
