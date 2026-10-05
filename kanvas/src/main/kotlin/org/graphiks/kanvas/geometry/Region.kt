package org.graphiks.kanvas.geometry

import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RegionBooleanOp
import org.graphiks.math.geometry.RegionF32

enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

/** Mutable compatibility facade over immutable renderer-neutral [RegionF32] values. */
class Region {
    private var geometry: RegionF32

    internal val rects: List<RectF32> get() = geometry.rects

    constructor() {
        geometry = RegionF32.empty()
    }

    constructor(rect: RectF32) {
        geometry = RegionF32(rect)
    }

    constructor(region: Region) {
        geometry = region.geometry
    }

    val isEmpty: Boolean get() = geometry.isEmpty

    val isRect: Boolean get() = geometry.isRect

    val isComplex: Boolean get() = geometry.isComplex

    val bounds: RectF32
        get() = geometry.bounds.let { bounds ->
            RectF32(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }

    fun setEmpty() {
        geometry = RegionF32.empty()
    }

    fun setRect(rect: RectF32) {
        geometry = RegionF32(rect)
    }

    fun setRegion(region: Region) {
        geometry = region.geometry
    }

    fun op(rect: RectF32, op: RegionOp): Boolean {
        geometry = geometry.op(rect, op.toRegionBooleanOp())
        return true
    }

    fun op(region: Region, op: RegionOp): Boolean {
        geometry = geometry.op(region.geometry, op.toRegionBooleanOp())
        return true
    }

    fun contains(x: Float, y: Float): Boolean = geometry.contains(x, y)

    fun quickReject(rect: RectF32): Boolean = geometry.quickReject(rect)

    fun translate(dx: Float, dy: Float) {
        geometry = geometry.translated(dx, dy)
    }
}

private fun RegionOp.toRegionBooleanOp(): RegionBooleanOp = when (this) {
    RegionOp.DIFFERENCE -> RegionBooleanOp.DIFFERENCE
    RegionOp.INTERSECT -> RegionBooleanOp.INTERSECT
    RegionOp.UNION -> RegionBooleanOp.UNION
    RegionOp.XOR -> RegionBooleanOp.XOR
    RegionOp.REVERSE_DIFFERENCE -> RegionBooleanOp.REVERSE_DIFFERENCE
    RegionOp.REPLACE -> RegionBooleanOp.REPLACE
}
