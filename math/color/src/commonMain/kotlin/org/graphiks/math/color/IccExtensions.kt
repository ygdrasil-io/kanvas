package org.graphiks.math.color

import org.graphiks.math.matrix.Matrix3x3F32

public fun Matrix3x3F32.iccGet(row: Int, col: Int): Float {
    require(row in 0..2 && col in 0..2) { "iccGet: indices ($row, $col) out of [0..2]" }
    return when (row) {
        0 -> when (col) { 0 -> sx; 1 -> kx; else -> tx }
        1 -> when (col) { 0 -> ky; 1 -> sy; else -> ty }
        else -> when (col) { 0 -> persp0; 1 -> persp1; else -> persp2 }
    }
}

public fun Matrix3x3F32.iccSet(row: Int, col: Int, value: Float): Matrix3x3F32 {
    require(row in 0..2 && col in 0..2) { "iccSet: indices ($row, $col) out of [0..2]" }
    return when (row) {
        0 -> when (col) {
            0 -> copy(sx = value); 1 -> copy(kx = value); else -> copy(tx = value)
        }
        1 -> when (col) {
            0 -> copy(ky = value); 1 -> copy(sy = value); else -> copy(ty = value)
        }
        else -> when (col) {
            0 -> copy(persp0 = value); 1 -> copy(persp1 = value); else -> copy(persp2 = value)
        }
    }
}

public val Matrix3x3F32.iccIsIdentity: Boolean
    get() = sx == 1f && kx == 0f && tx == 0f &&
            ky == 0f && sy == 1f && ty == 0f &&
            persp0 == 0f && persp1 == 0f && persp2 == 1f
