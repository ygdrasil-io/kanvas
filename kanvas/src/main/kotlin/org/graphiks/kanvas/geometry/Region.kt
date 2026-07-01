package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.Rect

enum class RegionOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE, REPLACE }

class Region {
    internal val rects = mutableListOf<Rect>()

    constructor()

    constructor(rect: Rect) {
        if (!rect.isEmpty) rects.add(Rect(rect.left, rect.top, rect.right, rect.bottom))
    }

    constructor(region: Region) {
        rects.addAll(region.rects.map { Rect(it.left, it.top, it.right, it.bottom) })
    }

    val isEmpty: Boolean get() = rects.isEmpty()

    val isRect: Boolean get() = rects.size == 1

    val isComplex: Boolean get() = rects.size > 1

    val bounds: Rect
        get() {
            if (rects.isEmpty()) return Rect.EMPTY
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (r in rects) {
                minX = minOf(minX, r.left); minY = minOf(minY, r.top)
                maxX = maxOf(maxX, r.right); maxY = maxOf(maxY, r.bottom)
            }
            return Rect(minX, minY, maxX, maxY)
        }

    fun setEmpty() { rects.clear() }

    fun setRect(rect: Rect) {
        rects.clear()
        if (!rect.isEmpty) rects.add(Rect(rect.left, rect.top, rect.right, rect.bottom))
    }

    fun setRegion(region: Region) {
        rects.clear()
        rects.addAll(region.rects.map { Rect(it.left, it.top, it.right, it.bottom) })
    }

    fun op(rect: Rect, op: RegionOp): Boolean {
        val other = Region(rect)
        return op(other, op)
    }

    fun op(region: Region, op: RegionOp): Boolean {
        val result = when (op) {
            RegionOp.UNION -> unionOp(region)
            RegionOp.INTERSECT -> intersectOp(region)
            RegionOp.DIFFERENCE -> differenceOp(region)
            RegionOp.XOR -> xorOp(region)
            RegionOp.REVERSE_DIFFERENCE -> {
                val oldRects = rects.toList()
                rects.clear()
                rects.addAll(region.rects.map { Rect(it.left, it.top, it.right, it.bottom) })
                val tmp = differenceOp(Region().also { it.rects.addAll(oldRects) })
                if (tmp != null) {
                    rects.clear()
                    rects.addAll(tmp)
                }
                return tmp != null
            }
            RegionOp.REPLACE -> {
                rects.clear()
                rects.addAll(region.rects.map { Rect(it.left, it.top, it.right, it.bottom) })
                return true
            }
        }
        return if (result != null) {
            rects.clear()
            rects.addAll(result)
            true
        } else false
    }

    fun contains(x: Float, y: Float): Boolean {
        return rects.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
    }

    fun quickReject(rect: Rect): Boolean {
        val b = bounds
        return rect.right <= b.left || rect.left >= b.right ||
            rect.bottom <= b.top || rect.top >= b.bottom
    }

    fun translate(dx: Float, dy: Float) {
        for (r in rects) {
            r.left += dx; r.top += dy
            r.right += dx; r.bottom += dy
        }
    }

    private fun unionOp(other: Region): List<Rect>? {
        val all = mutableListOf<Rect>()
        all.addAll(rects.map { Rect(it.left, it.top, it.right, it.bottom) })
        all.addAll(other.rects.map { Rect(it.left, it.top, it.right, it.bottom) })
        return mergeRects(all)
    }

    private fun intersectOp(other: Region): List<Rect>? {
        val result = mutableListOf<Rect>()
        for (a in rects) {
            for (b in other.rects) {
                val l = maxOf(a.left, b.left)
                val t = maxOf(a.top, b.top)
                val r = minOf(a.right, b.right)
                val btm = minOf(a.bottom, b.bottom)
                if (l < r && t < btm) {
                    result.add(Rect(l, t, r, btm))
                }
            }
        }
        return mergeRects(result)
    }

    private fun differenceOp(other: Region): List<Rect>? {
        var current = rects.toList()
        for (b in other.rects) {
            val next = mutableListOf<Rect>()
            for (a in current) {
                if (a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom) {
                    next.add(Rect(a.left, a.top, a.right, a.bottom))
                    continue
                }
                if (a.top < b.top) next.add(Rect(a.left, a.top, a.right, b.top))
                if (a.bottom > b.bottom) next.add(Rect(a.left, b.bottom, a.right, a.bottom))
                if (a.left < b.left) next.add(Rect(a.left, maxOf(a.top, b.top), b.left, minOf(a.bottom, b.bottom)))
                if (a.right > b.right) next.add(Rect(b.right, maxOf(a.top, b.top), a.right, minOf(a.bottom, b.bottom)))
            }
            current = next
        }
        return mergeRects(current)
    }

    private fun xorOp(other: Region): List<Rect>? {
        val union = unionOp(other) ?: return null
        val intersect = intersectOp(other) ?: return null
        var current = union
        for (b in intersect) {
            val next = mutableListOf<Rect>()
            for (a in current) {
                if (a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom) {
                    next.add(Rect(a.left, a.top, a.right, a.bottom))
                    continue
                }
                if (a.top < b.top) next.add(Rect(a.left, a.top, a.right, b.top))
                if (a.bottom > b.bottom) next.add(Rect(a.left, b.bottom, a.right, a.bottom))
                if (a.left < b.left) next.add(Rect(a.left, maxOf(a.top, b.top), b.left, minOf(a.bottom, b.bottom)))
                if (a.right > b.right) next.add(Rect(b.right, maxOf(a.top, b.top), a.right, minOf(a.bottom, b.bottom)))
            }
            current = next
        }
        return mergeRects(current)
    }

    private fun mergeRects(input: List<Rect>): List<Rect> {
        if (input.isEmpty()) return emptyList()
        val sorted = input.sortedWith(compareBy({ it.top }, { it.left }, { it.bottom }, { it.right }))
        val merged = mutableListOf<Rect>()
        for (r in sorted) {
            if (r.isEmpty) continue
            var added = false
            for (m in merged) {
                if (m.top == r.top && m.bottom == r.bottom && r.left >= m.left && r.left <= m.right + 1e-6f) {
                    m.right = maxOf(m.right, r.right)
                    added = true
                    break
                }
            }
            if (!added) merged.add(Rect(r.left, r.top, r.right, r.bottom))
        }
        return merged
    }
}
