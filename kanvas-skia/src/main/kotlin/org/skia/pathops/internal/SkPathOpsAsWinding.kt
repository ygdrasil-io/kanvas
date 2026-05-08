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
 * contains another). For flat trees, even-odd vs winding fill is
 * still correct via a simple `makeFillType` swap. For nested trees
 * (donut hole, letter "O", etc.), return null until the full
 * upstream machinery (containment ray-cast + reverse-marker pass +
 * `reverseAddPath`) lands in a follow-up.
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
