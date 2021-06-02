/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2021 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt;

/**
 * TODO
 */
public class MaxScalingCost implements SearchCost {

    static final double MIN_COST_PER_UNIT_DISTANCE = 1.0 / 256;

    private final double multiplier;

    public MaxScalingCost(double maximum) {
        this.multiplier = 256.0 / maximum;
    }

    @Override
    public double costMovingTo(double valueAtNewPoint) {
        if (valueAtNewPoint == 0) {
            return Double.MAX_VALUE;
        }
        valueAtNewPoint *= multiplier;
        if (valueAtNewPoint > 256) {
            valueAtNewPoint = 256;
        }
        return 1.0 / valueAtNewPoint;
    }

    @Override
    public double minimumCostPerUnitDistance() {
        return MIN_COST_PER_UNIT_DISTANCE;
    }

}