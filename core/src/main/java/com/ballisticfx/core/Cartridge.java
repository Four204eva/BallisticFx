package com.ballisticfx.core;

/**
 * A named load: bullet plus typical velocity and barrel twist. Preset values are approximate published
 * figures for typical factory or match ammunition. Muzzle velocity in particular depends on barrel length
 * and lot, so replace it with your own chronographed value for real work.
 *
 * @param group     category used to organise pickers ("Rimfire", "Pistol", "Rifle", ...)
 * @param bc        ballistic coefficient on {@code dragModel}
 */
public record Cartridge(
        String name,
        String group,
        DragModel dragModel,
        double bc,
        double weightGrains,
        double diameterInches,
        double lengthInches,
        double muzzleVelocityFps,
        double twistInchesPerTurn) {

    public Projectile toProjectile() {
        return new Projectile(dragModel, bc, weightGrains, diameterInches, lengthInches,
                muzzleVelocityFps * Units.FPS_TO_MPS);
    }

    @Override
    public String toString() {
        return name;
    }
}
