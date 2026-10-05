package com.ballisticfx.core;

/**
 * Everything the solver needs for one shot.
 *
 * @param atmosphere     conditions today, at the shooter
 * @param zeroAtmosphere conditions when the rifle was zeroed; {@code null} means "same as today"
 */
public record ShotInput(
        Projectile projectile,
        Rifle rifle,
        Atmosphere atmosphere,
        Atmosphere zeroAtmosphere,
        Wind wind,
        ShotGeometry geometry,
        SolverOptions options) {

    public ShotInput {
        if (projectile == null || rifle == null || atmosphere == null || wind == null
                || geometry == null || options == null) {
            throw new IllegalArgumentException("Incomplete shot input");
        }
    }

    public ShotInput(Projectile projectile, Rifle rifle, Atmosphere atmosphere, Wind wind, ShotGeometry geometry) {
        this(projectile, rifle, atmosphere, null, wind, geometry, SolverOptions.DEFAULT);
    }

    public Atmosphere effectiveZeroAtmosphere() {
        return zeroAtmosphere != null ? zeroAtmosphere : atmosphere;
    }

    public ShotInput withGeometry(ShotGeometry g) {
        return new ShotInput(projectile, rifle, atmosphere, zeroAtmosphere, wind, g, options);
    }

    public ShotInput withOptions(SolverOptions o) {
        return new ShotInput(projectile, rifle, atmosphere, zeroAtmosphere, wind, geometry, o);
    }
}
