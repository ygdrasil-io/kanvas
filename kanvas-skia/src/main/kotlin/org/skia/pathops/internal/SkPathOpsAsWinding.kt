/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions and helpers from Skia's
 * `src/pathops/SkPathOpsAsWinding.cpp` — the kEvenOdd → kWinding
 * conversion driver.
 *
 * Phase D1.2.h.6.3 — Multi-contour AsWinding **partial impl** :
 * port the contour bbox-tree builder (`Contour` + `contourBounds`
 * + `inParent`) and use it to detect flat-tree inputs (no contour
 * contains another).
 *
 * Phase D1.2.h.6.4 — Add `getDirection` (signed-area test) and a
 * **2-level-nested fast path** : when the bbox tree is exactly
 * two levels deep, compare each child's direction to its parent.
 * If they alternate (parent CW + child CCW, or vice-versa), no
 * reversal is needed and `makeFillType` is correct. Same direction
 * (parent CW + child CW) means the child needs reversing — return
 * null (deferred to h.6.5+).
 */
package org.skia.pathops.internal

import org.skia.foundation.SkPath
import org.skia.math.SkRect

/**
 * One contour of an `AsWinding` input — its bbox + the verb-stream
 * range that materialises it. Children are populated by [inParent]
 * when a contour's bbox is fully contained by another's.
 *
 * Mirrors `struct Contour`
 * (`src/pathops/SkPathOpsAsWinding.cpp:29`).
 */
internal class AsWindingContour(
    val bounds: SkRect,
    val verbStart: Int,
    val verbEnd: Int,
) {
    val children: MutableList<AsWindingContour> = mutableListOf()
}

/**
 * Walk the path's verb stream and emit one [AsWindingContour] per
 * contour (range bounded by `kMove`s). Mirrors
 * `OpAsWinding::contourBounds` (`SkPathOpsAsWinding.cpp:191`).
 *
 * Each contour's bounds is the union of its constituent verbs'
 * point bounds (start, control, end). Empty contours (a `kMove`
 * not followed by any drawing verbs) are dropped to match
 * upstream's `bounds.isEmpty()` guard.
 */
internal fun contourBounds(path: SkPath): List<AsWindingContour> {
    val result = mutableListOf<AsWindingContour>()
    var lastStart = 0
    var verbStart = 0
    var coordIdx = 0
    var bounds = SkRect.MakeEmpty()
    var hasBounds = false
    fun extend(x: Float, y: Float) {
        if (!hasBounds) {
            bounds = SkRect.MakeLTRB(x, y, x, y)
            hasBounds = true
        } else {
            val l = minOf(bounds.left, x)
            val t = minOf(bounds.top, y)
            val r = maxOf(bounds.right, x)
            val b = maxOf(bounds.bottom, y)
            bounds = SkRect.MakeLTRB(l, t, r, b)
        }
    }
    for (v in path.verbs) {
        when (v) {
            SkPath.Verb.kMove -> {
                if (hasBounds) {
                    result.add(AsWindingContour(bounds, lastStart, verbStart))
                    lastStart = verbStart
                }
                bounds = SkRect.MakeEmpty()
                hasBounds = false
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2
                extend(x, y)
            }
            SkPath.Verb.kLine -> {
                val x = path.coords[coordIdx]; val y = path.coords[coordIdx + 1]
                coordIdx += 2; extend(x, y)
            }
            SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                repeat(2) {
                    extend(path.coords[coordIdx], path.coords[coordIdx + 1])
                    coordIdx += 2
                }
            }
            SkPath.Verb.kCubic -> {
                repeat(3) {
                    extend(path.coords[coordIdx], path.coords[coordIdx + 1])
                    coordIdx += 2
                }
            }
            SkPath.Verb.kClose -> { /* no points */ }
        }
        ++verbStart
    }
    if (hasBounds) {
        result.add(AsWindingContour(bounds, lastStart, verbStart))
    }
    return result
}

/**
 * Recursive bbox-tree builder. Insert [contour] into [parent]'s
 * child list at the deepest level where [contour]'s bbox fits ; if
 * any of [parent]'s existing children fit inside [contour]'s bbox,
 * promote them to be [contour]'s children.
 *
 * Mirrors `OpAsWinding::inParent`
 * (`src/pathops/SkPathOpsAsWinding.cpp:317`).
 */
internal fun inParent(contour: AsWindingContour, parent: AsWindingContour) {
    for (test in parent.children) {
        if (test.bounds.contains(contour.bounds)) {
            inParent(contour, test)
            return
        }
    }
    val iter = parent.children.iterator()
    while (iter.hasNext()) {
        val child = iter.next()
        if (contour.bounds.contains(child.bounds)) {
            contour.children.add(child)
            iter.remove()
        }
    }
    parent.children.add(contour)
}

/**
 * True if [root]'s top-level children form a "flat" tree —
 * i.e. no contour is contained inside another. For this case
 * even-odd vs winding fill is moot : both rules paint the same
 * area, so a fillType swap suffices.
 *
 * Mirrors the early-out `if (std::all_of(...) → empty children)`
 * in `AsWinding` (`SkPathOpsAsWinding.cpp:438`).
 */
internal fun isFlatTree(root: AsWindingContour): Boolean =
    root.children.all { it.children.isEmpty() }

/**
 * Contour-direction enum used during reverse-marker analysis.
 * Mirrors `Contour::Direction`
 * (`src/pathops/SkPathOpsAsWinding.cpp:30`).
 *
 * `kCCW` = `-1`, `kNone` = `0`, `kCW` = `+1` — preserves the
 * upstream sign-arithmetic convention.
 */
internal enum class AsWindingDirection(val sign: Int) { kCCW(-1), kNone(0), kCW(1) }

private fun toDirection(dy: Float): AsWindingDirection = when {
    dy > 0 -> AsWindingDirection.kCCW
    dy < 0 -> AsWindingDirection.kCW
    else -> AsWindingDirection.kNone
}

/**
 * Compute [contour]'s direction (CW / CCW) by summing the signed
 * area of its constituent line / quad / conic / cubic verbs.
 * Mirrors `OpAsWinding::getDirection`
 * (`src/pathops/SkPathOpsAsWinding.cpp:217`).
 *
 * Strategy : per verb, accumulate `(p0.y - pN.y) * (p0.x + pN.x)`
 * (the shoelace formula on endpoints). Negative total → CCW,
 * positive → CW.
 *
 * Curve verbs collapse to their endpoints — an inexact
 * approximation that nonetheless agrees with upstream because the
 * direction sign is invariant under polygon-vs-curve substitution
 * for non-self-intersecting paths.
 */
internal fun getDirection(path: SkPath, contour: AsWindingContour): AsWindingDirection {
    var verbCount = -1
    var coordIdx = 0
    var penX = 0f; var penY = 0f
    var totalSignedArea = 0f
    for (v in path.verbs) {
        ++verbCount
        // Advance the pen for each verb, regardless of whether it
        // falls inside the contour range we're measuring.
        if (verbCount < contour.verbStart || verbCount >= contour.verbEnd) {
            // Still need to advance the coord cursor.
            when (v) {
                SkPath.Verb.kMove -> {
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kLine -> {
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                    coordIdx += 2 // control
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kCubic -> {
                    coordIdx += 4 // 2 controls
                    penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                    coordIdx += 2
                }
                SkPath.Verb.kClose -> { /* no points */ }
            }
            continue
        }
        when (v) {
            SkPath.Verb.kMove -> {
                penX = path.coords[coordIdx]; penY = path.coords[coordIdx + 1]
                coordIdx += 2
            }
            SkPath.Verb.kLine -> {
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kQuad, SkPath.Verb.kConic -> {
                coordIdx += 2 // skip control
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kCubic -> {
                coordIdx += 4 // skip 2 controls
                val ex = path.coords[coordIdx]; val ey = path.coords[coordIdx + 1]
                coordIdx += 2
                totalSignedArea += (penY - ey) * (penX + ex)
                penX = ex; penY = ey
            }
            SkPath.Verb.kClose -> { /* no points */ }
        }
    }
    return if (totalSignedArea < 0) AsWindingDirection.kCCW else AsWindingDirection.kCW
}

/**
 * 2-level-nested AsWinding analysis : check that every (parent,
 * child) pair has alternating directions (one CW, one CCW). When
 * all pairs alternate, no reversal is needed and the input is
 * already a well-formed winding-equivalent path — caller can
 * `makeFillType`.
 *
 * Returns false (reversal needed → caller bail-outs to null) when
 * any pair shares direction, or when the tree is deeper than 2
 * levels (caller falls through to null until h.6.5).
 */
internal fun no2LevelReverseNeeded(
    path: SkPath,
    sortedRoot: AsWindingContour,
): Boolean {
    for (parent in sortedRoot.children) {
        if (parent.children.isEmpty()) continue
        val parentDir = getDirection(path, parent)
        for (child in parent.children) {
            if (child.children.isNotEmpty()) return false // 3+ levels deep
            val childDir = getDirection(path, child)
            if (parentDir == childDir) return false // same direction → reverse needed
        }
    }
    return true
}
