package com.ballisticfx.core;

import java.util.Arrays;

/**
 * Standard drag functions: the drag coefficient of a reference projectile versus Mach number.
 * A ballistic coefficient (BC) is only meaningful together with the model it was measured against,
 * so a bullet is always described as "BC x on G1" or "BC x on G7".
 *
 * <ul>
 *   <li>G1: flat-base reference shape. Older and still the most common published figure.</li>
 *   <li>G7: long boat-tail reference shape. Matches modern match/hunting bullets much better.</li>
 * </ul>
 *
 * <p>Tables cross-checked row by row against the py_ballisticcalc standard tables.
 */
public enum DragModel {
    G1("G1", Tables.G1_MACH, Tables.G1_CD),
    G7("G7", Tables.G7_MACH, Tables.G7_CD);

    private final String label;
    private final double[] mach;
    private final double[] cd;

    DragModel(String label, double[] mach, double[] cd) {
        if (mach.length != cd.length) {
            throw new IllegalStateException(label + " drag table arrays must have equal length");
        }
        this.label = label;
        this.mach = mach;
        this.cd = cd;
    }

    /** Linearly interpolated Cd, clamped to the end values outside 0..5 Mach. */
    public double cd(double machNumber) {
        if (machNumber <= mach[0]) return cd[0];
        int last = mach.length - 1;
        if (machNumber >= mach[last]) return cd[last];
        int idx = Arrays.binarySearch(mach, machNumber);
        if (idx >= 0) return cd[idx];
        idx = -idx - 2; // node just below
        double f = (machNumber - mach[idx]) / (mach[idx + 1] - mach[idx]);
        return cd[idx] + f * (cd[idx + 1] - cd[idx]);
    }

    /** Number of table nodes; exposed for tests. */
    int nodeCount() {
        return mach.length;
    }

    double nodeMach(int i) {
        return mach[i];
    }

    @Override
    public String toString() {
        return label;
    }

    private static final class Tables {
        static final double[] G1_MACH = {
            0.00, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45,
            0.50, 0.55, 0.60, 0.70, 0.725, 0.75, 0.775, 0.80, 0.825, 0.85,
            0.875, 0.90, 0.925, 0.95, 0.975, 1.00, 1.025, 1.05, 1.075, 1.10,
            1.125, 1.15, 1.20, 1.25, 1.30, 1.35, 1.40, 1.45, 1.50, 1.55,
            1.60, 1.65, 1.70, 1.75, 1.80, 1.85, 1.90, 1.95, 2.00, 2.05,
            2.10, 2.15, 2.20, 2.25, 2.30, 2.35, 2.40, 2.45, 2.50, 2.60,
            2.70, 2.80, 2.90, 3.00, 3.10, 3.20, 3.30, 3.40, 3.50, 3.60,
            3.70, 3.80, 3.90, 4.00, 4.20, 4.40, 4.60, 4.80, 5.00,
        };
        static final double[] G1_CD = {
            0.2629, 0.2558, 0.2487, 0.2413, 0.2344, 0.2278, 0.2214, 0.2155, 0.2104, 0.2061,
            0.2032, 0.2020, 0.2034, 0.2165, 0.2230, 0.2313, 0.2417, 0.2546, 0.2706, 0.2901,
            0.3136, 0.3415, 0.3734, 0.4084, 0.4448, 0.4805, 0.5136, 0.5427, 0.5677, 0.5883,
            0.6053, 0.6191, 0.6393, 0.6518, 0.6589, 0.6621, 0.6625, 0.6607, 0.6573, 0.6528,
            0.6474, 0.6413, 0.6347, 0.6280, 0.6210, 0.6141, 0.6072, 0.6003, 0.5934, 0.5867,
            0.5804, 0.5743, 0.5685, 0.5630, 0.5577, 0.5527, 0.5481, 0.5438, 0.5397, 0.5325,
            0.5264, 0.5211, 0.5168, 0.5133, 0.5105, 0.5084, 0.5067, 0.5054, 0.5040, 0.5030,
            0.5022, 0.5016, 0.5010, 0.5006, 0.4998, 0.4995, 0.4992, 0.4990, 0.4988,
        };

        static final double[] G7_MACH = {
            0.00, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45,
            0.50, 0.55, 0.60, 0.65, 0.70, 0.725, 0.75, 0.775, 0.80, 0.825,
            0.85, 0.875, 0.90, 0.925, 0.95, 0.975, 1.00, 1.025, 1.05, 1.075,
            1.10, 1.125, 1.15, 1.20, 1.25, 1.30, 1.35, 1.40, 1.50, 1.55,
            1.60, 1.65, 1.70, 1.75, 1.80, 1.85, 1.90, 1.95, 2.00, 2.05,
            2.10, 2.15, 2.20, 2.25, 2.30, 2.35, 2.40, 2.45, 2.50, 2.55,
            2.60, 2.65, 2.70, 2.75, 2.80, 2.85, 2.90, 2.95, 3.00, 3.10,
            3.20, 3.30, 3.40, 3.50, 3.60, 3.70, 3.80, 3.90, 4.00, 4.20,
            4.40, 4.60, 4.80, 5.00,
        };
        static final double[] G7_CD = {
            0.1198, 0.1197, 0.1196, 0.1194, 0.1193, 0.1194, 0.1194, 0.1194, 0.1193, 0.1193,
            0.1194, 0.1193, 0.1194, 0.1197, 0.1202, 0.1207, 0.1215, 0.1226, 0.1242, 0.1266,
            0.1306, 0.1368, 0.1464, 0.1660, 0.2054, 0.2993, 0.3803, 0.4015, 0.4043, 0.4034,
            0.4014, 0.3987, 0.3955, 0.3884, 0.3810, 0.3732, 0.3657, 0.3580, 0.3440, 0.3376,
            0.3315, 0.3260, 0.3209, 0.3160, 0.3117, 0.3078, 0.3042, 0.3010, 0.2980, 0.2951,
            0.2922, 0.2892, 0.2864, 0.2835, 0.2807, 0.2779, 0.2752, 0.2725, 0.2697, 0.2670,
            0.2643, 0.2615, 0.2588, 0.2561, 0.2533, 0.2506, 0.2479, 0.2451, 0.2424, 0.2368,
            0.2313, 0.2258, 0.2205, 0.2154, 0.2106, 0.2060, 0.2017, 0.1975, 0.1935, 0.1861,
            0.1793, 0.1730, 0.1672, 0.1618,
        };
    }
}
