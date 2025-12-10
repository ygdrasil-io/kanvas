package com.kanvas.core

/**
 * Clip operations that match Skia's SkClipOp enum
 */
enum class SkClipOp {
    /** Replace the current clip with the intersection of the current clip and the rectangle */
    INTERSECT,
    /** Replace the current clip with the difference of the current clip and the rectangle */
    DIFFERENCE
}

