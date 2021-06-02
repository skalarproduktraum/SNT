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

import smile.math.special.Erf;

/**
 * A cost function inspired by an A* search implementation in the
 * <a href="https://github.com/JaneliaSciComp/workstation">Janelia Workstation</a>,
 * where the cost of moving to a new voxel is given by the complementary error function
 * 1 - erf(z), where
 * <p>erf(x) is the <a href="https://mathworld.wolfram.com/Erf.html">Error Function</a></p>
 * <p>z is the <a href="https://en.wikipedia.org/wiki/Standard_score">Standard Score</a> of the voxel intensity
 * at the new point, given the intensity statistics of the underlying image</p>
 *
 * @author Cameron Arshadi
 */
public class OneMinusErfCost implements SearchCost {

    static final double STEP_COST_LOWER_BOUND = 1e-60;
    final double minCostPerUnitDistance;

    private final double min;
    private final double max;
    private final double avg;
    private final double stdDev;

    // multiplier for the z-score
    double zFudge = 0.8;

    /**
     *
     * @param min the minimum intensity value of the image
     * @param max the maximum intensity value of the image
     * @param average the average intensity value of the image
     * @param standardDeviation the standard deviation of the intensity values of the image
     */
    public OneMinusErfCost(final double min, final double max, final double average, final double standardDeviation)
    {
        this.min = min;
        this.max = max;
        this.avg = average;
        this.stdDev = standardDeviation;
        this.minCostPerUnitDistance = computeMinStepCost();
        SNTUtils.log("min step cost = " + minCostPerUnitDistance);
    }

    @Override
    public double costMovingTo(final double valueAtNewPoint) {
        return Erf.erfc(zFudge * zScore(valueAtNewPoint));
    }

    public void setFudge(final double zFudge) {
        this.zFudge = zFudge;
    }

    protected double zScore(final double valueAtNewPoint) {
        return (valueAtNewPoint - this.avg) / this.stdDev;
    }

    private double computeMinStepCost() {
        return Erf.erfc(zFudge * zScore(this.max)) + STEP_COST_LOWER_BOUND;
    }

    @Override
    public double minimumCostPerUnitDistance() {
        return minCostPerUnitDistance;
    }

}