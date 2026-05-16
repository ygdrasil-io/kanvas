package org.skia.utils

import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkPath
import org.skia.foundation.SkVertices
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * R-suivi.30 — pure-Kotlin port of upstream Skia's
 * [`SkShadowTessellator`](https://github.com/google/skia/blob/main/src/utils/SkShadowTessellator.cpp).
 *
 * Produces analytic per-vertex-coloured triangle meshes for the two
 * shadow layers (ambient halo + spot offset) — replacing the legacy
 * blur-image-filter approximation in [SkShadowUtils.DrawShadow].
 *
 * **Scope of this port** : convex closed polygonal paths (and Bezier
 * curves flattened to line segments). Concave / self-intersecting
 * paths fall through to the legacy blur implementation — upstream
 * relies on `SkOffsetSimplePolygon` + `SkTriangulateSimplePolygon`
 * which are themselves ~1.5 kLOC and tracked as a separate follow-up.
 *
 * The output mesh layout follows upstream's "ring strip" pattern :
 *   - Inner ring : path vertices, all painted with [kUmbraColor]
 *     (= [SK_ColorBLACK]) ⇒ full alpha contribution.
 *   - Outer ring : path vertices outset by `radius`, all painted with
 *     [kPenumbraColor] (= [SK_ColorTRANSPARENT]) ⇒ zero alpha.
 *   - Triangulated quad strip between inner and outer rings, plus a
 *     fan covering the umbra interior (only when `transparent = true`
 *     or in the geometric-only path).
 *
 * `drawVertices` with [org.skia.foundation.SkVertices.VertexMode.kTriangles]
 * + per-vertex colours then renders the soft-falloff gradient.
 */
internal object SkShadowTessellator {

    /** Inner-ring colour ⇒ full shadow contribution. */
    private val kUmbraColor: Int = SK_ColorBLACK

    /** Outer-ring colour ⇒ zero shadow contribution (soft fall-off). */
    private val kPenumbraColor: Int = SK_ColorTRANSPARENT

    /** Distance below which two points are considered coincident
     *  (matches upstream's `SkShadowTessellator.cpp:160` `kClose = 1/16`).
     */
    private const val kCloseSqd: Float = (1f / 16f) * (1f / 16f)

    /** Tolerance for Bezier flattening, in source-coordinate pixels.
     *  Upstream uses `0.2` (`SkShadowTessellator.cpp:743`) for the GPU
     *  path ; we use the same value for parity.
     */
    private const val kCurveTolerance: Float = 0.2f

    /**
     * Result of [tessellatePath] : the inner (path silhouette) polygon
     * plus a [convex] flag. When [convex] is `false` the caller falls
     * back to the legacy blur path.
     */
    internal data class Polygon(
        val points: Array<SkPoint>,
        val convex: Boolean,
        val centroid: SkPoint,
        val area: Float,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Walk [path] via [SkPath.Iter], flatten curves at [kCurveTolerance],
     * drop coincident points and check convexity. Returns `null` when
     * the path is empty or has fewer than 3 distinct points.
     */
    internal fun tessellatePath(path: SkPath): Polygon? {
        val raw = ArrayList<SkPoint>(max(8, path.countPoints()))
        val iter = SkPath.Iter(path, forceClose = false)
        val pts = FloatArray(8)
        var moveX = 0f; var moveY = 0f
        var lastX = 0f; var lastY = 0f
        loop@ while (true) {
            val v = iter.next(pts)
            when (v) {
                SkPath.Verb.kDone -> break@loop
                SkPath.Verb.kMove -> {
                    moveX = pts[0]; moveY = pts[1]
                    lastX = moveX; lastY = moveY
                    appendSanitized(raw, moveX, moveY)
                }
                SkPath.Verb.kLine -> {
                    lastX = pts[2]; lastY = pts[3]
                    appendSanitized(raw, lastX, lastY)
                }
                SkPath.Verb.kQuad -> {
                    flattenQuad(raw, pts[0], pts[1], pts[2], pts[3], pts[4], pts[5])
                    lastX = pts[4]; lastY = pts[5]
                }
                SkPath.Verb.kConic -> {
                    // Approximate conic by its two control-quad
                    // halves at midpoint — same as upstream's
                    // SkAutoConicToQuads but simplified to a single
                    // midpoint subdivision for the tolerance we use.
                    flattenQuad(raw, pts[0], pts[1], pts[2], pts[3], pts[4], pts[5])
                    lastX = pts[4]; lastY = pts[5]
                }
                SkPath.Verb.kCubic -> {
                    flattenCubic(
                        raw,
                        pts[0], pts[1], pts[2], pts[3],
                        pts[4], pts[5], pts[6], pts[7],
                    )
                    lastX = pts[6]; lastY = pts[7]
                }
                SkPath.Verb.kClose -> {
                    // Close back to move target if not coincident.
                    if (!nearlyEqual(lastX, lastY, moveX, moveY)) {
                        appendSanitized(raw, moveX, moveY)
                    }
                }
            }
        }
        // Drop trailing close-loop duplicate.
        if (raw.size >= 2 && nearlyEqual(raw[0], raw[raw.size - 1])) {
            raw.removeAt(raw.size - 1)
        }
        if (raw.size < 3) return null

        // Compute signed area + centroid (shoelace formula).
        var area = 0f
        var cx = 0f
        var cy = 0f
        for (i in raw.indices) {
            val j = (i + 1) % raw.size
            val cross = raw[i].fX * raw[j].fY - raw[j].fX * raw[i].fY
            area += cross
            cx += (raw[i].fX + raw[j].fX) * cross
            cy += (raw[i].fY + raw[j].fY) * cross
        }
        if (!area.isFinite() || abs(area) < 1e-6f) return null
        area *= 0.5f
        cx /= 6f * area
        cy /= 6f * area

        // Convexity check : all consecutive cross products must share
        // the same sign (matches upstream's `checkConvexity`).
        var convex = true
        var sign = 0f
        for (i in raw.indices) {
            val a = raw[i]
            val b = raw[(i + 1) % raw.size]
            val c = raw[(i + 2) % raw.size]
            val cr = (b.fX - a.fX) * (c.fY - b.fY) - (b.fY - a.fY) * (c.fX - b.fX)
            if (abs(cr) < 1e-6f) continue // collinear — ignore
            if (sign == 0f) sign = cr
            else if (sign * cr < 0f) { convex = false; break }
        }

        return Polygon(raw.toTypedArray(), convex, SkPoint(cx, cy), area)
    }

    private fun appendSanitized(out: ArrayList<SkPoint>, x: Float, y: Float) {
        // Snap to nearest 1/16-pixel (matches `sanitize_point` upstream).
        val sx = (x * 16f).let { kotlin.math.round(it) } / 16f
        val sy = (y * 16f).let { kotlin.math.round(it) } / 16f
        if (out.isNotEmpty()) {
            val last = out[out.size - 1]
            if (nearlyEqual(last.fX, last.fY, sx, sy)) return
        }
        out.add(SkPoint(sx, sy))
    }

    private fun flattenQuad(
        out: ArrayList<SkPoint>,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ) {
        // Recursive midpoint subdivision until the control point lies
        // within [kCurveTolerance] of the chord.
        val dx = x2 - x0
        val dy = y2 - y0
        val chordLen = sqrt(dx * dx + dy * dy)
        val dev = if (chordLen > 0f) abs(dx * (y0 - y1) - dy * (x0 - x1)) / chordLen else 0f
        if (dev <= kCurveTolerance || chordLen < 1f) {
            appendSanitized(out, x2, y2)
            return
        }
        val mx01 = (x0 + x1) * 0.5f; val my01 = (y0 + y1) * 0.5f
        val mx12 = (x1 + x2) * 0.5f; val my12 = (y1 + y2) * 0.5f
        val mx = (mx01 + mx12) * 0.5f; val my = (my01 + my12) * 0.5f
        flattenQuad(out, x0, y0, mx01, my01, mx, my)
        flattenQuad(out, mx, my, mx12, my12, x2, y2)
    }

    private fun flattenCubic(
        out: ArrayList<SkPoint>,
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
    ) {
        val dx = x3 - x0
        val dy = y3 - y0
        val chordLen = sqrt(dx * dx + dy * dy)
        val dev1 = if (chordLen > 0f) abs(dx * (y0 - y1) - dy * (x0 - x1)) / chordLen else 0f
        val dev2 = if (chordLen > 0f) abs(dx * (y0 - y2) - dy * (x0 - x2)) / chordLen else 0f
        if (max(dev1, dev2) <= kCurveTolerance || chordLen < 1f) {
            appendSanitized(out, x3, y3)
            return
        }
        // De Casteljau midpoint subdivision.
        val mx01 = (x0 + x1) * 0.5f; val my01 = (y0 + y1) * 0.5f
        val mx12 = (x1 + x2) * 0.5f; val my12 = (y1 + y2) * 0.5f
        val mx23 = (x2 + x3) * 0.5f; val my23 = (y2 + y3) * 0.5f
        val mx012 = (mx01 + mx12) * 0.5f; val my012 = (my01 + my12) * 0.5f
        val mx123 = (mx12 + mx23) * 0.5f; val my123 = (my12 + my23) * 0.5f
        val mx = (mx012 + mx123) * 0.5f; val my = (my012 + my123) * 0.5f
        flattenCubic(out, x0, y0, mx01, my01, mx012, my012, mx, my)
        flattenCubic(out, mx, my, mx123, my123, mx23, my23, x3, y3)
    }

    private fun nearlyEqual(p0: SkPoint, p1: SkPoint): Boolean =
        nearlyEqual(p0.fX, p0.fY, p1.fX, p1.fY)

    private fun nearlyEqual(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val dx = x0 - x1
        val dy = y0 - y1
        return dx * dx + dy * dy < kCloseSqd
    }

    /**
     * Compute outward unit normals for each edge of the (convex,
     * positively-oriented) polygon, then take the angle-bisector at
     * each vertex (sum of adjacent edge normals, re-normalised) so the
     * outset stays on the offset line of each adjacent edge.
     *
     * Returns an array `[nx0, ny0, nx1, ny1, …]` of length
     * `2 * poly.size` indexed by *vertex* (not edge). The orientation
     * sign accounts for clockwise vs counter-clockwise winding.
     */
    private fun computeVertexNormals(poly: Array<SkPoint>, areaSign: Float): FloatArray {
        val n = poly.size
        // First, per-edge outward unit normals. Edge i = poly[i] → poly[(i+1)%n].
        val edgeN = FloatArray(2 * n)
        for (i in 0 until n) {
            val a = poly[i]
            val b = poly[(i + 1) % n]
            // Perpendicular pointing outward : rotate (dx, dy) by -90° for
            // CCW polygon (area > 0) ⇒ (dy, -dx) ; for CW use the inverse.
            val dx = b.fX - a.fX
            val dy = b.fY - a.fY
            var nx = dy
            var ny = -dx
            if (areaSign < 0f) { nx = -nx; ny = -ny }
            val len = sqrt(nx * nx + ny * ny)
            if (len > 1e-6f) {
                edgeN[2 * i] = nx / len
                edgeN[2 * i + 1] = ny / len
            }
        }
        // Per-vertex bisector : average of the two edges meeting at the
        // vertex. Re-normalised but compensated by 1/cos(half-angle) so
        // the offset polygon's edges stay at distance `r` from the
        // original edges (the standard "miter" outset formula).
        val out = FloatArray(2 * n)
        for (i in 0 until n) {
            val prev = (i + n - 1) % n
            val nx = edgeN[2 * prev] + edgeN[2 * i]
            val ny = edgeN[2 * prev + 1] + edgeN[2 * i + 1]
            // Miter compensation : the dot product of the two edge
            // normals = cos(turn-angle). The bisector's length-squared
            // = 2 + 2·dot, so dividing by (1 + dot) inflates it to the
            // miter length 1/cos(half-angle).
            val dot = edgeN[2 * prev] * edgeN[2 * i] + edgeN[2 * prev + 1] * edgeN[2 * i + 1]
            val scale = if (dot > -0.95f) 1f / (1f + dot) else 1f
            out[2 * i] = nx * scale
            out[2 * i + 1] = ny * scale
        }
        return out
    }

    /**
     * Build a soft-falloff ring mesh : inner polygon ([poly]) with
     * [kUmbraColor], outer polygon offset by [outset] along the
     * vertex bisectors with [kPenumbraColor], plus a triangle strip
     * stitching the two rings.
     *
     * If [transparent] is `true`, the umbra interior is also covered
     * with a triangle fan rooted at the centroid (otherwise the
     * caller is responsible for clipping the opaque occluder out).
     */
    private fun buildRingMesh(
        poly: Array<SkPoint>,
        areaSign: Float,
        centroid: SkPoint,
        outset: Float,
        transparent: Boolean,
        geometricOnly: Boolean,
    ): SkVertices {
        val n = poly.size
        val normals = computeVertexNormals(poly, areaSign)

        // Vertex layout:
        //   0 .. n-1 : inner ring (umbra colour)
        //   n .. 2n-1 : outer ring (penumbra colour) — skipped if geometricOnly
        //   2n (transparent only) : centroid for the umbra fan
        val outerCount = if (geometricOnly) 0 else n
        val haveCentroid = transparent && !geometricOnly
        val totalVerts = n + outerCount + (if (haveCentroid) 1 else 0)
        val positions = Array(totalVerts) { SkPoint(0f, 0f) }
        val colors = IntArray(totalVerts)

        // Inner ring.
        for (i in 0 until n) {
            positions[i] = SkPoint(poly[i].fX, poly[i].fY)
            colors[i] = kUmbraColor
        }
        // Outer ring.
        if (!geometricOnly) {
            for (i in 0 until n) {
                val nx = normals[2 * i]
                val ny = normals[2 * i + 1]
                positions[n + i] = SkPoint(poly[i].fX + nx * outset, poly[i].fY + ny * outset)
                colors[n + i] = kPenumbraColor
            }
        }
        if (haveCentroid) {
            positions[n + outerCount] = SkPoint(centroid.fX, centroid.fY)
            colors[n + outerCount] = kUmbraColor
        }

        // Triangle index buffer.
        val indices = ArrayList<Short>(6 * n)
        if (!geometricOnly) {
            // Ring strip — two triangles per edge :
            //   (inner_i, outer_i, inner_{i+1})
            //   (outer_i, outer_{i+1}, inner_{i+1})
            for (i in 0 until n) {
                val j = (i + 1) % n
                indices.add(i.toShort())
                indices.add((n + i).toShort())
                indices.add(j.toShort())

                indices.add((n + i).toShort())
                indices.add((n + j).toShort())
                indices.add(j.toShort())
            }
        }
        if (haveCentroid) {
            val c = (n + outerCount).toShort()
            for (i in 0 until n) {
                val j = (i + 1) % n
                indices.add(c)
                indices.add(i.toShort())
                indices.add(j.toShort())
            }
        }
        if (geometricOnly) {
            // Just the umbra fan (no soft falloff).
            // Use a fan rooted at poly[0] to cover the silhouette.
            for (i in 1 until n - 1) {
                indices.add(0.toShort())
                indices.add(i.toShort())
                indices.add((i + 1).toShort())
            }
        }

        val idxArr = ShortArray(indices.size) { indices[it] }
        return SkVertices.MakeCopy(
            mode = SkVertices.VertexMode.kTriangles,
            positions = positions,
            texCoords = null,
            colors = colors,
            indices = idxArr,
        )
    }

    /**
     * Build the **ambient** shadow mesh : a uniform soft halo around
     * [path] outset by the ambient blur radius derived from the
     * `(zPlaneParams · centroid)` height. Returns `null` for empty /
     * non-convex / degenerate paths so the caller can fall back to
     * the legacy blur path.
     */
    internal fun MakeAmbient(
        path: SkPath,
        zPlaneParams: SkPoint3,
        transparent: Boolean,
        geometricOnly: Boolean,
    ): SkVertices? {
        if (!zPlaneParams.isFinite()) return null
        val poly = tessellatePath(path) ?: return null
        if (!poly.convex) return null

        // Use upstream's height-dependent ambient blur radius
        // (`SkDrawShadowMetrics::AmbientBlurRadius`) evaluated at the
        // centroid — matches `SkAmbientShadowTessellator`'s `baseZ`.
        val z = max(
            0.1f,
            zPlaneParams.fX * poly.centroid.fX +
                zPlaneParams.fY * poly.centroid.fY +
                zPlaneParams.fZ,
        )
        val outset = min(z * (1f / 128f) * 64f, 150f) // == AmbientBlurRadius
        if (outset <= 0f) return null

        val areaSign = if (poly.area > 0f) 1f else -1f
        return buildRingMesh(
            poly = poly.points,
            areaSign = areaSign,
            centroid = poly.centroid,
            outset = outset,
            transparent = transparent,
            geometricOnly = geometricOnly,
        )
    }

    /**
     * Build the **spot** shadow mesh : project [path] through
     * [lightPos] / [zPlaneParams], outset by the umbra/penumbra
     * radius, and emit a soft ring mesh. Returns `null` if the
     * geometry is empty / non-convex / degenerate.
     *
     * @param scale       output of [SkShadowUtils]' spot-params (perspective enlargement).
     * @param translateX  output of [SkShadowUtils]' spot-params (light offset).
     * @param translateY  output of [SkShadowUtils]' spot-params (light offset).
     * @param blurRadius  output of [SkShadowUtils]' spot-params (penumbra width).
     */
    internal fun MakeSpot(
        path: SkPath,
        zPlaneParams: SkPoint3,
        lightPos: SkPoint3,
        lightRadius: Float,
        scale: Float,
        translateX: Float,
        translateY: Float,
        blurRadius: Float,
        transparent: Boolean,
        geometricOnly: Boolean,
    ): SkVertices? {
        if (!zPlaneParams.isFinite() || !lightPos.isFinite()) return null
        if (!lightRadius.isFinite() || lightRadius < 1e-3f) return null
        val poly = tessellatePath(path) ?: return null
        if (!poly.convex) return null

        // Transform each silhouette vertex by the spot shadow transform
        // (scale around the path centroid, then translate). This is the
        // shadow projection on the canvas plane.
        val cx = poly.centroid.fX
        val cy = poly.centroid.fY
        val projected = Array(poly.points.size) { i ->
            val p = poly.points[i]
            SkPoint(
                (p.fX - cx) * scale + cx + translateX,
                (p.fY - cy) * scale + cy + translateY,
            )
        }
        // Recompute the projected centroid + area (the linear map can
        // flip winding for negative scale, though our paths use scale ≥ 1).
        var area = 0f
        var pcx = 0f; var pcy = 0f
        for (i in projected.indices) {
            val a = projected[i]
            val b = projected[(i + 1) % projected.size]
            val cr = a.fX * b.fY - b.fX * a.fY
            area += cr
            pcx += (a.fX + b.fX) * cr
            pcy += (a.fY + b.fY) * cr
        }
        if (abs(area) < 1e-6f) return null
        area *= 0.5f
        pcx /= 6f * area
        pcy /= 6f * area
        val areaSign = if (area > 0f) 1f else -1f

        val outset = blurRadius
        if (outset <= 0f && !geometricOnly) return null

        // Suppress unused-parameter warning ; the lightPos / lightRadius
        // values are forwarded by the caller for future extensions but
        // currently only feed into the spot-params upstream.
        @Suppress("UNUSED_VARIABLE") val _lp = lightPos
        @Suppress("UNUSED_VARIABLE") val _lr = lightRadius

        return buildRingMesh(
            poly = projected,
            areaSign = areaSign,
            centroid = SkPoint(pcx, pcy),
            outset = max(outset, 0f),
            transparent = transparent,
            geometricOnly = geometricOnly,
        )
    }
}
