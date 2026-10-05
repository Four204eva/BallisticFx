package com.ballisticfx.core;

/**
 * The firearm and optic.
 *
 * @param sightHeightM         distance from the bore axis up to the scope axis
 * @param zeroRangeM           the (level) range at which the scope is zeroed; elevation dial values are
 *                             measured from this zero, like turret readings on a real scope
 * @param twistInchesPerTurn   barrel twist, e.g. 10 for a 1:10" barrel
 * @param rightHandTwist       true for the usual right-hand rifling (spin drift goes right)
 */
public record Rifle(double sightHeightM, double zeroRangeM, double twistInchesPerTurn, boolean rightHandTwist) {

    public Rifle {
        if (!(sightHeightM >= 0.0 && sightHeightM <= 0.3)) {
            throw new IllegalArgumentException("Sight height must be between 0 and 300 mm");
        }
        if (!(zeroRangeM >= 5.0 && zeroRangeM <= 2000.0)) {
            throw new IllegalArgumentException("Zero range must be between 5 m and 2000 m");
        }
        if (!(twistInchesPerTurn >= 3.0 && twistInchesPerTurn <= 60.0)) {
            throw new IllegalArgumentException("Twist must be between 3 and 60 inches per turn");
        }
    }
}
