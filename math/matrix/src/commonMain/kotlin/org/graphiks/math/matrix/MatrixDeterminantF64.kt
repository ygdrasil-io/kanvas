package org.graphiks.math.matrix

/** Exact cofactor evaluation used to distinguish singularity from an unrepresentable inverse. */
public fun Matrix3x3F64.determinantF64(): Double =
    sxF64 * (syF64 * persp2F64 - tyF64 * persp1F64) +
        kxF64 * (tyF64 * persp0F64 - kyF64 * persp2F64) +
        txF64 * (kyF64 * persp1F64 - syF64 * persp0F64)
