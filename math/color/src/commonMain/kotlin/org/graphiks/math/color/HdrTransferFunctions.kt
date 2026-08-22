package org.graphiks.math.color

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

private const val PQ_M1: Double = 2610.0 / 16384.0
private const val PQ_M2: Double = 2523.0 / 32.0
private const val PQ_C1: Double = 3424.0 / 4096.0
private const val PQ_C2: Double = 2413.0 / 128.0
private const val PQ_C3: Double = 2392.0 / 128.0
private const val HLG_A: Double = 0.17883277
private const val HLG_B: Double = 0.28466892
private const val HLG_C: Double = 0.55991073

/** Converts a normalized PQ encoded value to linear light. */
public fun pqEotf(encoded: Double): Double {
    require(encoded in 0.0..1.0)
    val signal = encoded.pow(1.0 / PQ_M2)
    return ((signal - PQ_C1).coerceAtLeast(0.0) / (PQ_C2 - PQ_C3 * signal)).pow(1.0 / PQ_M1)
}

/** Converts a normalized PQ linear-light value to its encoded signal. */
public fun pqInverseEotf(linear: Double): Double {
    require(linear in 0.0..1.0)
    val power = linear.pow(PQ_M1)
    return ((PQ_C1 + PQ_C2 * power) / (1.0 + PQ_C3 * power)).pow(PQ_M2)
}

/** Converts a normalized HLG encoded value to scene-linear light. */
public fun hlgInverseOetf(encoded: Double): Double {
    require(encoded in 0.0..1.0)
    return if (encoded <= 0.5) encoded * encoded / 3.0 else
        (exp((encoded - HLG_C) / HLG_A) + HLG_B) / 12.0
}

/** Converts non-negative finite HLG scene-linear light to its encoded signal. */
public fun hlgOetf(sceneLinear: Double): Double {
    require(sceneLinear.isFinite() && sceneLinear >= 0.0)
    return if (sceneLinear <= 1.0 / 12.0) sqrt(3.0 * sceneLinear) else
        HLG_A * ln(12.0 * sceneLinear - HLG_B) + HLG_C
}
