package com.ballisticfx.core;

import java.util.List;

import static com.ballisticfx.core.DragModel.G1;
import static com.ballisticfx.core.DragModel.G7;

/**
 * Built-in starter loads. Figures are approximate published values for typical factory/match ammunition
 * (the BC is quoted against the drag model it was published for). They pre-fill the form, nothing more:
 * use your own chronographed velocity and the manufacturer's current BC for anything that matters.
 */
public final class Cartridges {
    private Cartridges() {}

    public static final List<Cartridge> ALL = List.of(
            //        name                                   group      model  BC     gr     dia    len   fps   twist
            new Cartridge(".22 LR 40 gr LRN",                "Rimfire", G1, 0.138,  40, 0.223, 0.42, 1070, 16.0),
            new Cartridge("9mm Luger 124 gr FMJ",            "Pistol",  G1, 0.150, 124, 0.355, 0.60, 1150, 10.0),
            new Cartridge(".45 ACP 230 gr FMJ",              "Pistol",  G1, 0.160, 230, 0.451, 0.68,  850, 16.0),
            new Cartridge(".223 Rem 55 gr FMJ",              "Rifle",   G1, 0.243,  55, 0.224, 0.74, 3240,  9.0),
            new Cartridge("5.56 NATO 62 gr M855",            "Rifle",   G1, 0.304,  62, 0.224, 0.91, 3020,  7.0),
            new Cartridge(".223 Rem 77 gr OTM match",        "Rifle",   G7, 0.190,  77, 0.224, 0.99, 2700,  7.0),
            new Cartridge("7.62x39 123 gr FMJ",              "Rifle",   G1, 0.275, 123, 0.311, 0.98, 2350,  9.5),
            new Cartridge("6.5 Creedmoor 140 gr ELD-M",      "Rifle",   G7, 0.326, 140, 0.264, 1.37, 2710,  8.0),
            new Cartridge("6.5 PRC 147 gr ELD-M",            "Rifle",   G7, 0.351, 147, 0.264, 1.45, 2910,  8.0),
            new Cartridge("7mm Rem Mag 162 gr ELD-X",        "Rifle",   G7, 0.318, 162, 0.284, 1.50, 2940,  9.0),
            new Cartridge(".308 Win 168 gr SMK",             "Rifle",   G1, 0.462, 168, 0.308, 1.22, 2650, 11.0),
            new Cartridge(".308 Win 175 gr SMK",             "Rifle",   G7, 0.243, 175, 0.308, 1.24, 2600, 10.0),
            new Cartridge("7.62x51 NATO 147 gr M80 FMJ",     "Rifle",   G1, 0.400, 147, 0.308, 1.12, 2750, 12.0),
            new Cartridge(".300 Win Mag 190 gr SMK",         "Magnum",  G1, 0.533, 190, 0.308, 1.35, 2900, 10.0),
            new Cartridge(".300 Win Mag 215 gr Hybrid OTM",  "Magnum",  G7, 0.354, 215, 0.308, 1.62, 2800,  9.0),
            new Cartridge(".338 Lapua Mag 250 gr Scenar",    "Magnum",  G7, 0.337, 250, 0.338, 1.49, 2970, 10.0),
            new Cartridge(".50 BMG 750 gr A-MAX",            "Magnum",  G1, 1.050, 750, 0.510, 2.33, 2820, 15.0));

    /** The preset used when the app starts. */
    public static Cartridge defaultCartridge() {
        return ALL.stream().filter(c -> c.name().equals(".308 Win 175 gr SMK")).findFirst().orElse(ALL.get(0));
    }
}
