package org.graphiks.kanvas.geometry

import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class PathOp { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

object PathOps {
    fun op(path1: Path, path2: Path, op: PathOp): Path? {
        val r1 = Rect.EMPTY
        val r2 = Rect.EMPTY
        if (path1.isRect(r1) && path2.isRect(r2)) {
            return rectOp(r1, r2, op)
        }

        val actualOp = if (op == PathOp.REVERSE_DIFFERENCE) PathOp.DIFFERENCE else op
        val (p1, p2) = if (op == PathOp.REVERSE_DIFFERENCE) Pair(path2, path1) else Pair(path1, path2)

        val contours1 = flattenPath(p1) ?: return null
        val contours2 = flattenPath(p2) ?: return null
        if (contours1.isEmpty() || contours2.isEmpty()) return null

        if (contours1.size == 1 && contours2.size == 1) {
            val poly1 = ensureCCW(contours1[0])
            val poly2 = ensureCCW(contours2[0])

            if (isConvexPolygon(poly1) && isConvexPolygon(poly2)) {
                val result = convexOps(poly1, poly2, actualOp)
                if (result != null) return result
            }

            val result = generalBooleanOp(poly1, poly2, actualOp)
            if (result != null) {
                if (result.isEmpty()) return Path()
                return polygonsToPath(result, path1.fillType)
            }
        }

        return null
    }

    private fun rectOp(r1: Rect, r2: Rect, op: PathOp): Path {
        val reg1 = Region(r1); val reg2 = Region(r2)
        val regionOp = when (op) {
            PathOp.DIFFERENCE -> RegionOp.DIFFERENCE
            PathOp.INTERSECT -> RegionOp.INTERSECT
            PathOp.UNION -> RegionOp.UNION
            PathOp.XOR -> RegionOp.XOR
            PathOp.REVERSE_DIFFERENCE -> RegionOp.REVERSE_DIFFERENCE
        }
        reg1.op(reg2, regionOp)
        if (reg1.isEmpty) return Path()
        if (reg1.isRect) {
            val b = reg1.bounds
            return Path().addRect(Rect.fromLTRB(b.left, b.top, b.right, b.bottom))
        }
        val result = Path()
        for (rect in reg1.rects) result.addRect(rect)
        return result
    }

    fun simplify(path: Path): Path? {
        val result = Path()
        result.fillType = path.fillType
        result.apply {
            val v = path.verbs()
            val p = path.points()
            var pi = 0
            for (verb in v) {
                when (verb) {
                    PathVerb.MOVE -> { moveTo(p[pi].x, p[pi].y); pi++ }
                    PathVerb.LINE -> { lineTo(p[pi].x, p[pi].y); pi++ }
                    PathVerb.QUAD -> { quadTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y); pi += 2 }
                    PathVerb.CUBIC -> { cubicTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y, p[pi + 2].x, p[pi + 2].y); pi += 3 }
                    PathVerb.ARC_TO -> {
                        arcTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y > 0f, p[pi + 2].x > 0f, p[pi + 3].x, p[pi + 3].y)
                        pi += 4
                    }
                    PathVerb.CLOSE -> close()
                }
            }
        }
        return result
    }

    fun asWinding(path: Path): Path? {
        val result = Path()
        result.fillType = FillType.WINDING
        val v = path.verbs()
        val p = path.points()
        var pi = 0
        for (verb in v) {
            when (verb) {
                PathVerb.MOVE -> { result.moveTo(p[pi].x, p[pi].y); pi++ }
                PathVerb.LINE -> { result.lineTo(p[pi].x, p[pi].y); pi++ }
                PathVerb.QUAD -> { result.quadTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y); pi += 2 }
                PathVerb.CUBIC -> { result.cubicTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y, p[pi + 2].x, p[pi + 2].y); pi += 3 }
                PathVerb.ARC_TO -> {
                    result.arcTo(p[pi].x, p[pi].y, p[pi + 1].x, p[pi + 1].y > 0f, p[pi + 2].x > 0f, p[pi + 3].x, p[pi + 3].y)
                    pi += 4
                }
                PathVerb.CLOSE -> result.close()
            }
        }
        return result
    }

    // --- Flatten path to polygon ---

    private fun flattenPath(path: Path): List<List<Point>>? {
        val v = path.verbs(); val p = path.points()
        if (v.isEmpty()) return null

        val contours = mutableListOf<MutableList<Point>>()
        var currentContour = mutableListOf<Point>()
        var pi = 0
        var prevX = 0f; var prevY = 0f
        var firstX = 0f; var firstY = 0f
        var hasPrev = false

        for (verb in v) {
            when (verb) {
                PathVerb.MOVE -> {
                    if (currentContour.size >= 3) {
                        contours.add(currentContour.toMutableList())
                    }
                    currentContour.clear()
                    firstX = p[pi].x; firstY = p[pi].y
                    currentContour.add(Point(firstX, firstY))
                    prevX = firstX; prevY = firstY
                    hasPrev = true; pi++
                }
                PathVerb.LINE -> {
                    val x = p[pi].x; val y = p[pi].y
                    currentContour.add(Point(x, y))
                    prevX = x; prevY = y; pi++
                }
                PathVerb.QUAD -> {
                    val cx = p[pi].x; val cy = p[pi].y
                    val x = p[pi + 1].x; val y = p[pi + 1].y
                    linearizeQuad(prevX, prevY, cx, cy, x, y, currentContour)
                    prevX = x; prevY = y; pi += 2
                }
                PathVerb.CUBIC -> {
                    val cx1 = p[pi].x; val cy1 = p[pi].y
                    val cx2 = p[pi + 1].x; val cy2 = p[pi + 1].y
                    val x = p[pi + 2].x; val y = p[pi + 2].y
                    linearizeCubic(prevX, prevY, cx1, cy1, cx2, cy2, x, y, currentContour)
                    prevX = x; prevY = y; pi += 3
                }
                PathVerb.ARC_TO -> {
                    val x = p[pi + 3].x; val y = p[pi + 3].y
                    currentContour.add(Point(x, y))
                    prevX = x; prevY = y; pi += 4
                }
                PathVerb.CLOSE -> {
                    if (hasPrev && (prevX != firstX || prevY != firstY)) {
                        currentContour.add(Point(firstX, firstY))
                        prevX = firstX; prevY = firstY
                    }
                }
            }
        }

        if (currentContour.size >= 3) {
            contours.add(currentContour.toMutableList())
        }

        return if (contours.isEmpty()) null else contours
    }

    private fun linearizeQuad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, out: MutableList<Point>) {
        val steps = 8
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val u = 1f - t
            val x = u * u * x0 + 2f * u * t * cx + t * t * x1
            val y = u * u * y0 + 2f * u * t * cy + t * t * y1
            out.add(Point(x, y))
        }
    }

    private fun linearizeCubic(x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x1: Float, y1: Float, out: MutableList<Point>) {
        val steps = 16
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val u = 1f - t
            val x = u * u * u * x0 + 3f * u * u * t * cx1 + 3f * u * t * t * cx2 + t * t * t * x1
            val y = u * u * u * y0 + 3f * u * u * t * cy1 + 3f * u * t * t * cy2 + t * t * t * y1
            out.add(Point(x, y))
        }
    }

    // --- Polygon utilities ---

    private fun signedArea(polygon: List<Point>): Float {
        var area = 0f
        val n = polygon.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += polygon[i].x * polygon[j].y
            area -= polygon[j].x * polygon[i].y
        }
        return area / 2f
    }

    private fun ensureCCW(polygon: List<Point>): List<Point> {
        return if (signedArea(polygon) < 0f) polygon else polygon.reversed()
    }

    private fun ensureCW(polygon: List<Point>): List<Point> {
        return if (signedArea(polygon) > 0f) polygon else polygon.reversed()
    }

    private fun isConvexPolygon(polygon: List<Point>): Boolean {
        val n = polygon.size
        if (n < 3) return true
        var sign = 0f
        for (i in 0 until n) {
            val dx1 = polygon[(i + 1) % n].x - polygon[i].x
            val dy1 = polygon[(i + 1) % n].y - polygon[i].y
            val dx2 = polygon[(i + 2) % n].x - polygon[(i + 1) % n].x
            val dy2 = polygon[(i + 2) % n].y - polygon[(i + 1) % n].y
            val cross = dx1 * dy2 - dy1 * dx2
            if (cross != 0f) {
                if (sign == 0f) sign = cross
                else if (cross * sign < 0f) return false
            }
        }
        return true
    }

    private fun pointInPolygon(point: Point, polygon: List<Point>): Boolean {
        val px = point.x; val py = point.y
        var winding = 0
        val n = polygon.size
        for (i in 0 until n) {
            val x1 = polygon[i].x; val y1 = polygon[i].y
            val x2 = polygon[(i + 1) % n].x; val y2 = polygon[(i + 1) % n].y
            if (y1 == y2) continue
            if (py < minOf(y1, y2) || py >= maxOf(y1, y2)) continue
            val xIntersect = x1 + (py - y1) * (x2 - x1) / (y2 - y1)
            if (xIntersect > px) {
                if (y2 > y1) winding++ else winding--
            }
        }
        return winding != 0
    }

    // --- Line segment intersection ---

    private data class Edge(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    private fun segmentIntersection(e1: Edge, e2: Edge): Point? {
        val denom = (e1.x1 - e1.x2) * (e2.y1 - e2.y2) - (e1.y1 - e1.y2) * (e2.x1 - e2.x2)
        if (abs(denom) < 1e-10f) return null

        val t = ((e1.x1 - e2.x1) * (e2.y1 - e2.y2) - (e1.y1 - e2.y1) * (e2.x1 - e2.x2)) / denom
        if (t < -1e-10f || t > 1f + 1e-10f) return null

        val u = -((e1.x1 - e1.x2) * (e1.y1 - e2.y1) - (e1.y1 - e1.y2) * (e1.x1 - e2.x1)) / denom
        if (u < -1e-10f || u > 1f + 1e-10f) return null

        val x = e1.x1 + t * (e1.x2 - e1.x1)
        val y = e1.y1 + t * (e1.y2 - e1.y1)
        return Point(x, y)
    }

    // --- Sutherland-Hodgman convex polygon clipping ---

    private fun sutherlandHodgmanClip(subject: List<Point>, clip: List<Point>): List<Point>? {
        var output = subject.toMutableList()
        val n = clip.size
        for (i in 0 until n) {
            if (output.isEmpty()) return null
            val e1 = clip[i]
            val e2 = clip[(i + 1) % n]
            val input = output
            output = mutableListOf()
            for (j in input.indices) {
                val curr = input[j]
                val prev = input[(j - 1 + input.size) % input.size]
                val currInside = isInsideHalfPlane(curr, e1, e2)
                val prevInside = isInsideHalfPlane(prev, e1, e2)
                if (currInside) {
                    if (!prevInside) {
                        val inter = lineEdgeIntersection(prev, curr, e1, e2)
                        if (inter != null) output.add(inter)
                    }
                    output.add(curr)
                } else if (prevInside) {
                    val inter = lineEdgeIntersection(prev, curr, e1, e2)
                    if (inter != null) output.add(inter)
                }
            }
        }
        return if (output.size >= 3) output else null
    }

    private fun isInsideHalfPlane(point: Point, edgeStart: Point, edgeEnd: Point): Boolean {
        return (edgeEnd.x - edgeStart.x) * (point.y - edgeStart.y) -
            (edgeEnd.y - edgeStart.y) * (point.x - edgeStart.x) >= -1e-8f
    }

    private fun lineEdgeIntersection(p1: Point, p2: Point, e1: Point, e2: Point): Point? {
        val denom = (p1.x - p2.x) * (e1.y - e2.y) - (p1.y - p2.y) * (e1.x - e2.x)
        if (abs(denom) < 1e-10f) return null
        val t = ((p1.x - e1.x) * (e1.y - e2.y) - (p1.y - e1.y) * (e1.x - e2.x)) / denom
        val x = p1.x + t * (p2.x - p1.x)
        val y = p1.y + t * (p2.y - p1.y)
        return Point(x, y)
    }

    private fun convexOps(poly1: List<Point>, poly2: List<Point>, op: PathOp): Path? {
        return when (op) {
            PathOp.INTERSECT -> {
                val result = sutherlandHodgmanClip(poly1, poly2)
                if (result != null) polygonsToPath(listOf(result), FillType.WINDING) else null
            }
            PathOp.UNION -> {
                val clipResult = sutherlandHodgmanClip(poly1, poly2)
                if (clipResult == null) {
                    polygonsToPath(listOf(poly1, poly2), FillType.WINDING)
                } else if (clipResult.size >= 3) {
                    polygonUnionToPath(poly1, poly2, clipResult)
                } else null
            }
            PathOp.DIFFERENCE -> null
            PathOp.XOR -> null
            else -> null
        }
    }

    private fun polygonUnionToPath(poly1: List<Point>, poly2: List<Point>, intersection: List<Point>): Path? {
        val allPoints = poly1 + poly2
        val hull = convexHull(allPoints)
        if (hull != null) return polygonsToPath(listOf(hull), FillType.WINDING)
        return polygonsToPath(listOf(poly1, poly2), FillType.WINDING)
    }

    private fun convexHull(points: List<Point>): List<Point>? {
        if (points.size < 3) return null
        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))
        val n = sorted.size
        val hull = Array<Point?>(2 * n) { null }
        var k = 0

        for (i in 0 until n) {
            while (k >= 2 && cross(hull[k - 1]!!, hull[k - 2]!!, sorted[i]) <= 0f) k--
            hull[k++] = sorted[i]
        }

        val lowerEnd = k + 1
        for (i in n - 2 downTo 0) {
            while (k >= lowerEnd && cross(hull[k - 1]!!, hull[k - 2]!!, sorted[i]) <= 0f) k--
            hull[k++] = sorted[i]
        }

        if (k <= 3) return null
        return hull.take(k - 1).filterNotNull()
    }

    private fun cross(o: Point, a: Point, b: Point): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }

    // --- Greiner-Hormann general boolean operation ---

    private class PolyState {
        val pts = mutableListOf<Point>()
        val isInter = mutableListOf<Boolean>()
        val neighbor = mutableListOf<Int>()
        val isEntry = mutableListOf<Boolean>()
        val visited = mutableListOf<Boolean>()

        val size get() = pts.size
        fun next(i: Int) = (i + 1) % size
        fun prev(i: Int) = (i - 1 + size) % size

        fun nextIntersection(i: Int): Int {
            var j = next(i)
            while (j != i && !isInter[j]) j = next(j)
            return if (j == i) -1 else j
        }

        fun prevIntersection(i: Int): Int {
            var j = prev(i)
            while (j != i && !isInter[j]) j = prev(j)
            return if (j == i) -1 else j
        }
    }

    private fun generalBooleanOp(poly1: List<Point>, poly2: List<Point>, op: PathOp): List<List<Point>>? {
        if (op == PathOp.XOR) return xorOp(poly1, poly2)

        val state1 = buildPolyState(poly1)
        val state2 = buildPolyState(poly2)

        findAndInsertIntersections(state1, state2)

        if (state1.pts.size == poly1.size && state2.pts.size == poly2.size) {
            return handleNoIntersections(poly1, poly2, op)
        }

        classifyEntries(state1, state2)

        val (startPhase, dir1, dir2) = when (op) {
            PathOp.INTERSECT -> Triple(true, 1, 1)
            PathOp.UNION -> Triple(false, 1, 1)
            PathOp.DIFFERENCE -> Triple(false, 1, -1)
            else -> return null
        }

        return ghTraverse(state1, state2, startPhase, dir1, dir2)
    }

    private fun xorOp(poly1: List<Point>, poly2: List<Point>): List<List<Point>>? {
        val diff12 = generalBooleanOp(poly1, poly2, PathOp.DIFFERENCE)
        val diff21 = generalBooleanOp(poly2, poly1, PathOp.DIFFERENCE)
        val result = mutableListOf<List<Point>>()
        if (diff12 != null) result.addAll(diff12)
        if (diff21 != null) result.addAll(diff21)
        return if (result.isNotEmpty()) result else null
    }

    private fun buildPolyState(polygon: List<Point>): PolyState {
        val state = PolyState()
        for (pt in polygon) {
            state.pts.add(Point(pt.x, pt.y))
            state.isInter.add(false)
            state.neighbor.add(-1)
            state.isEntry.add(false)
            state.visited.add(false)
        }
        return state
    }

    private data class InterRec(
        val pt: Point, val s1Edge: Int, val t1: Float, val s2Edge: Int, val t2: Float,
    )

    private fun findAndInsertIntersections(s1: PolyState, s2: PolyState) {
        val records = mutableListOf<InterRec>()
        val n = s1.size; val m = s2.size

        for (i in 0 until n) {
            val iNext = s1.next(i)
            val e1 = Edge(s1.pts[i].x, s1.pts[i].y, s1.pts[iNext].x, s1.pts[iNext].y)
            for (j in 0 until m) {
                val jNext = s2.next(j)
                val e2 = Edge(s2.pts[j].x, s2.pts[j].y, s2.pts[jNext].x, s2.pts[jNext].y)
                val inter = segmentIntersection(e1, e2)
                if (inter != null) {
                    val t1 = distance(e1, inter)
                    val t2 = distance(e2, inter)
                    if (t1 > 1e-8f && t1 < 1f - 1e-8f && t2 > 1e-8f && t2 < 1f - 1e-8f) {
                        records.add(InterRec(inter, i, t1, j, t2))
                    }
                }
            }
        }

        if (records.isEmpty()) return

        val s1Positions = mutableMapOf<InterRec, Int>()
        var cumulativeOffset = 0
        for ((edgeIdx, recs) in records.groupBy { it.s1Edge }.entries.sortedBy { it.key }) {
            val sorted = recs.sortedBy { it.t1 }
            var offset = 1
            for (rec in sorted) {
                val insertIdx = edgeIdx + cumulativeOffset + offset
                s1.pts.add(insertIdx, Point(rec.pt.x, rec.pt.y))
                s1.isInter.add(insertIdx, true)
                s1.neighbor.add(insertIdx, -1)
                s1.isEntry.add(insertIdx, false)
                s1.visited.add(insertIdx, false)
                s1Positions[rec] = insertIdx
                offset++
            }
            cumulativeOffset += recs.size
        }

        val s2Positions = mutableMapOf<InterRec, Int>()
        cumulativeOffset = 0
        for ((edgeIdx, recs) in records.groupBy { it.s2Edge }.entries.sortedBy { it.key }) {
            val sorted = recs.sortedBy { it.t2 }
            var offset = 1
            for (rec in sorted) {
                val insertIdx = edgeIdx + cumulativeOffset + offset
                s2.pts.add(insertIdx, Point(rec.pt.x, rec.pt.y))
                s2.isInter.add(insertIdx, true)
                s2.neighbor.add(insertIdx, -1)
                s2.isEntry.add(insertIdx, false)
                s2.visited.add(insertIdx, false)
                s2Positions[rec] = insertIdx
                offset++
            }
            cumulativeOffset += recs.size
        }

        for (rec in records) {
            val s1Idx = s1Positions[rec] ?: continue
            val s2Idx = s2Positions[rec] ?: continue
            s1.neighbor[s1Idx] = s2Idx
            s2.neighbor[s2Idx] = s1Idx
        }
    }

    private fun distance(e: Edge, p: Point): Float {
        val dx = e.x2 - e.x1; val dy = e.y2 - e.y1
        if (abs(dx) > abs(dy)) return (p.x - e.x1) / dx
        if (abs(dy) > 1e-10f) return (p.y - e.y1) / dy
        return 0f
    }

    private fun classifyEntries(s1: PolyState, s2: PolyState) {
        classifyEntriesForPoly(s1, s2)
        classifyEntriesForPoly(s2, s1)
    }

    private fun classifyEntriesForPoly(self: PolyState, other: PolyState) {
        val n = self.size
        var inside = pointInPolygon(self.pts[0], other.pts.map { it })

        for (i in 0 until n) {
            if (self.isInter[i]) {
                self.isEntry[i] = !inside
                inside = !inside
            }
        }
    }

    private fun ghTraverse(
        s1: PolyState, s2: PolyState,
        startPhase: Boolean, dir1: Int, dir2: Int
    ): List<List<Point>>? {
        val result = mutableListOf<List<Point>>()

        for (startIdx in s1.pts.indices) {
            if (!s1.isInter[startIdx] || s1.visited[startIdx]) continue
            if (s1.isEntry[startIdx] != startPhase) continue

            val contour = mutableListOf<Point>()
            var onP = true
            var currIdx = startIdx

            contour.add(s1.pts[currIdx])
            s1.visited[currIdx] = true

            val startNeighbor = s1.neighbor[startIdx]
            currIdx = startNeighbor
            onP = false

            while (true) {
                val self = if (onP) s1 else s2
                val dir = if (onP) dir1 else dir2

                currIdx = if (dir > 0) self.next(currIdx) else self.prev(currIdx)

                while (currIdx >= 0 && !self.isInter[currIdx]) {
                    contour.add(self.pts[currIdx])
                    currIdx = if (dir > 0) self.next(currIdx) else self.prev(currIdx)
                }

                if (currIdx < 0) break

                contour.add(self.pts[currIdx])
                self.visited[currIdx] = true

                val isBackToStart = if (onP) {
                    currIdx == startIdx
                } else {
                    currIdx == startNeighbor
                }
                if (isBackToStart) break

                val neighborIdx = self.neighbor[currIdx]
                if (neighborIdx < 0) break

                currIdx = neighborIdx
                onP = !onP
            }

            if (contour.size >= 3) {
                result.add(contour)
            }
        }

        return if (result.isNotEmpty()) result else null
    }

    private fun handleNoIntersections(poly1: List<Point>, poly2: List<Point>, op: PathOp): List<List<Point>>? {
        val p1inP2 = poly1.all { pointInPolygon(it, poly2) }
        val p2inP1 = poly2.all { pointInPolygon(it, poly1) }

        return when (op) {
            PathOp.INTERSECT -> {
                if (p1inP2) listOf(poly1)
                else if (p2inP1) listOf(poly2)
                else emptyList()
            }
            PathOp.UNION -> {
                if (p1inP2) listOf(poly2)
                else if (p2inP1) listOf(poly1)
                else listOf(poly1, poly2)
            }
            PathOp.DIFFERENCE -> {
                if (p1inP2) emptyList()
                else if (p2inP1) listOf(poly1, ensureCW(poly2))
                else listOf(poly1)
            }
            PathOp.XOR -> {
                if (p1inP2) listOf(poly2, ensureCW(poly1))
                else if (p2inP1) listOf(poly1, ensureCW(poly2))
                else listOf(poly1, poly2)
            }
            else -> null
        }
    }

    // --- Convert polygons back to Path ---

    private fun polygonsToPath(polygons: List<List<Point>>, fillType: FillType): Path {
        val path = Path()
        path.fillType = fillType
        for (poly in polygons) {
            if (poly.size < 3) continue
            path.moveTo(poly[0].x, poly[0].y)
            for (k in 1 until poly.size) {
                path.lineTo(poly[k].x, poly[k].y)
            }
            path.close()
        }
        return path
    }
}
