package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class StrokeGeometry(
    val vertices: List<Float>,
    val contourStarts: List<Int>,
)

internal fun applyDash(
    points: List<Pair<Float, Float>>,
    dashArray: FloatArray,
    phase: Float,
): List<Pair<Float, Float>> {
    if (dashArray.isEmpty()) return emptyList()
    val result = mutableListOf<Pair<Float, Float>>()
    val intervals = dashArray.map { it.coerceAtLeast(0.1f) }
    var dashIdx = 0
    val totalDashLen = intervals.sum().coerceAtLeast(1f)
    var intervalOffset = phase % totalDashLen
    if (intervalOffset < 0f) intervalOffset += totalDashLen
    while (intervalOffset >= intervals[dashIdx % intervals.size]) {
        intervalOffset -= intervals[dashIdx % intervals.size]
        dashIdx++
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[i]; val p1 = points[i + 1]
        val dx = p1.first - p0.first; val dy = p1.second - p0.second
        val segLen = sqrt(dx * dx + dy * dy)
        if (segLen < 1e-6f) continue
        var pos = 0f
        val nx = dx / segLen; val ny = dy / segLen

        while (pos < segLen) {
            val idx = dashIdx % intervals.size
            val dashLen = intervals[idx]
            val effectiveLen = minOf(dashLen - intervalOffset, segLen - pos)
            val endPos = pos + effectiveLen

            if (idx % 2 == 0) {
                result.add(Pair(p0.first + pos * nx, p0.second + pos * ny))
                result.add(Pair(p0.first + endPos * nx, p0.second + endPos * ny))
            }

            pos = endPos
            intervalOffset += effectiveLen
            if (intervalOffset >= dashLen - 1e-6f) {
                dashIdx++
                intervalOffset = 0f
            } else if (pos >= segLen - 1e-6f) {
                break
            }
        }
    }
    return result
}

internal fun generateRoundCap(
    center: Pair<Float, Float>,
    normal: Pair<Float, Float>,
    halfW: Float,
    segments: Int = 6,
): List<Float> {
    val result = mutableListOf<Float>()
    val cx = center.first; val cy = center.second
    for (i in 0 until segments) {
        val angle = (i.toFloat() / (segments - 1).toFloat()) * Math.PI.toFloat()
        val cosA = cos(angle); val sinA = sin(angle)
        val rx = -normal.first * cosA - normal.second * sinA
        val ry = -normal.second * cosA + normal.first * sinA
        result.add(cx + rx * halfW); result.add(cy + ry * halfW)
    }
    return result
}

internal fun generateRoundJoin(
    center: Pair<Float, Float>,
    inNorm: Pair<Float, Float>,
    outNorm: Pair<Float, Float>,
    halfW: Float,
    segments: Int = 6,
): List<Float> {
    val result = mutableListOf<Float>()
    val dot = inNorm.first * outNorm.first + inNorm.second * outNorm.second
    val cross = inNorm.first * outNorm.second - inNorm.second * outNorm.first
    val angle = atan2(cross, dot)
    for (i in 0..segments) {
        val t = i.toFloat() / segments
        val a = angle * t
        val cosA = cos(a); val sinA = sin(a)
        val rx = inNorm.first * cosA - inNorm.second * sinA
        val ry = inNorm.second * cosA + inNorm.first * sinA
        result.add(center.first + rx * halfW)
        result.add(center.second + ry * halfW)
    }
    return result
}

private fun dashStartsOnInterval(intervals: List<Float>, phase: Float): Boolean {
    var dashIdx = 0
    val totalDashLen = intervals.sum().coerceAtLeast(1f)
    var intervalOffset = phase % totalDashLen
    if (intervalOffset < 0f) intervalOffset += totalDashLen
    while (intervalOffset >= intervals[dashIdx % intervals.size]) {
        intervalOffset -= intervals[dashIdx % intervals.size]
        dashIdx++
    }
    return dashIdx % 2 == 0
}

internal fun strokeToFillGeometry(
    contourVertices: List<Float>,
    contourStarts: List<Int>,
    strokeWidth: Float,
    dashArray: FloatArray? = null,
    dashPhase: Float = 0f,
    capStyle: StrokeCap = StrokeCap.BUTT,
    joinStyle: StrokeJoin = StrokeJoin.MITER,
    miterLimit: Float = 4f,
): StrokeGeometry {
    if (
        contourVertices.size < 2 ||
        !strokeWidth.isFinite() ||
        strokeWidth < 0f ||
        !miterLimit.isFinite() ||
        miterLimit <= 0f
    ) {
        return StrokeGeometry(emptyList(), listOf(0))
    }

    val effectiveWidth = if (strokeWidth == 0f) 1f else strokeWidth
    val halfWidth = effectiveWidth / 2f
    val segments = 6
    val result = mutableListOf<Float>()
    val contourResult = mutableListOf(0)

    fun addTriangle(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float,
    ) {
        result.addAll(listOf(ax, ay, bx, by, cx, cy))
        contourResult.add(result.size / 2)
    }

    fun edgeNormal(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) return Pair(0f, 0f)
        return Pair(-dy / len, dx / len)
    }

    fun addRoundDot(center: Pair<Float, Float>) {
        var previousX = center.first + halfWidth
        var previousY = center.second
        for (i in 1..segments * 2) {
            val angle = (i.toFloat() / (segments * 2).toFloat()) * Math.PI.toFloat() * 2f
            val x = center.first + cos(angle) * halfWidth
            val y = center.second + sin(angle) * halfWidth
            addTriangle(center.first, center.second, previousX, previousY, x, y)
            previousX = x
            previousY = y
        }
    }

    fun admittedMiterOffset(
        incomingNormal: Pair<Float, Float>,
        outgoingNormal: Pair<Float, Float>,
    ): Pair<Float, Float>? {
        val summedX = incomingNormal.first + outgoingNormal.first
        val summedY = incomingNormal.second + outgoingNormal.second
        val summedLength = sqrt(summedX * summedX + summedY * summedY)
        if (summedLength < 1e-6f) return null

        val directionX = summedX / summedLength
        val directionY = summedY / summedLength
        val denominator =
            directionX * outgoingNormal.first +
                directionY * outgoingNormal.second
        val length = if (kotlin.math.abs(denominator) < 1e-6f) {
            Float.POSITIVE_INFINITY
        } else {
            kotlin.math.abs(halfWidth / denominator)
        }
        if (!length.isFinite() || length > halfWidth * miterLimit) return null
        return Pair(directionX * length, directionY * length)
    }

    fun addBevelOrMiterJoin(
        center: Pair<Float, Float>,
        incomingNormal: Pair<Float, Float>,
        outgoingNormal: Pair<Float, Float>,
    ) {
        val turn =
            incomingNormal.first * outgoingNormal.second -
                incomingNormal.second * outgoingNormal.first
        if (kotlin.math.abs(turn) < 1e-6f) return
        val side = if (turn > 0f) 1f else -1f
        val incomingOuter = Pair(
            center.first + incomingNormal.first * halfWidth * side,
            center.second + incomingNormal.second * halfWidth * side,
        )
        val outgoingOuter = Pair(
            center.first + outgoingNormal.first * halfWidth * side,
            center.second + outgoingNormal.second * halfWidth * side,
        )
        if (joinStyle == StrokeJoin.BEVEL) {
            addTriangle(
                center.first,
                center.second,
                incomingOuter.first,
                incomingOuter.second,
                outgoingOuter.first,
                outgoingOuter.second,
            )
            return
        }

        val miterOffset = admittedMiterOffset(incomingNormal, outgoingNormal)
        if (miterOffset == null) {
            addTriangle(
                center.first,
                center.second,
                incomingOuter.first,
                incomingOuter.second,
                outgoingOuter.first,
                outgoingOuter.second,
            )
            return
        }
        val miter = Pair(
            center.first + miterOffset.first * side,
            center.second + miterOffset.second * side,
        )
        addTriangle(
            incomingOuter.first,
            incomingOuter.second,
            miter.first,
            miter.second,
            outgoingOuter.first,
            outgoingOuter.second,
        )
    }

    for (ci in contourStarts.indices) {
        val start = contourStarts[ci]
        val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else contourVertices.size / 2
        val n = end - start

        val points = List(n) { idx ->
            val i = (start + idx) * 2
            Pair(contourVertices[i], contourVertices[i + 1])
        }

        if (points.isEmpty()) continue

        val center = points.first()
        val allSamePoint = points.zipWithNext().all { (previous, point) ->
            val dx = point.first - previous.first
            val dy = point.second - previous.second
            dx * dx + dy * dy < 1e-12f
        }
        if (allSamePoint) {
            val shouldDrawRoundCap = dashArray == null ||
                dashArray.isEmpty() ||
                dashStartsOnInterval(dashArray.map { it.coerceAtLeast(0.1f) }, dashPhase)
            if (capStyle == StrokeCap.ROUND && shouldDrawRoundCap) {
                addRoundDot(center)
            }
            continue
        }

        val isClosed = n >= 3 &&
            kotlin.math.abs(points[0].first - points[n - 1].first) < 1e-6f &&
            kotlin.math.abs(points[0].second - points[n - 1].second) < 1e-6f

        val dashSegments = if (dashArray != null && dashArray.isNotEmpty()) {
            applyDash(points, dashArray, dashPhase)
        } else null

        if (dashSegments != null && dashSegments.isNotEmpty()) {
            for (si in 0 until dashSegments.size step 2) {
                val p0 = dashSegments[si]
                val p1 = dashSegments[si + 1]
                val dx = p1.first - p0.first
                val dy = p1.second - p0.second
                val len = sqrt(dx * dx + dy * dy)
                if (len < 1e-6f) continue
                val nux = -dy / len
                val nuy = dx / len
                val nx = nux * halfWidth
                val ny = nuy * halfWidth
                val tangentExtension = if (capStyle == StrokeCap.SQUARE) halfWidth else 0f
                val tx = dx / len * tangentExtension
                val ty = dy / len * tangentExtension
                val startPoint = Pair(p0.first - tx, p0.second - ty)
                val endPoint = Pair(p1.first + tx, p1.second + ty)

                addTriangle(
                    startPoint.first - nx, startPoint.second - ny,
                    startPoint.first + nx, startPoint.second + ny,
                    endPoint.first + nx, endPoint.second + ny,
                )
                addTriangle(
                    startPoint.first - nx, startPoint.second - ny,
                    endPoint.first + nx, endPoint.second + ny,
                    endPoint.first - nx, endPoint.second - ny,
                )

                if (capStyle == StrokeCap.ROUND) {
                    val capStart = generateRoundCap(p0, Pair(nux, nuy), halfWidth, segments)
                    for (vi in 0 until capStart.size - 2 step 2) {
                        addTriangle(
                            p0.first, p0.second,
                            capStart[vi], capStart[vi + 1],
                            capStart[vi + 2], capStart[vi + 3],
                        )
                    }
                    val capEnd = generateRoundCap(p1, Pair(-nux, -nuy), halfWidth, segments)
                    for (vi in 0 until capEnd.size - 2 step 2) {
                        addTriangle(
                            p1.first, p1.second,
                            capEnd[vi], capEnd[vi + 1],
                            capEnd[vi + 2], capEnd[vi + 3],
                        )
                    }
                }
            }
        } else if (!isClosed || n == 2) {
            for (ei in 0 until n - 1) {
                val p0 = points[ei]
                val p1 = points[ei + 1]
                val dx = p1.first - p0.first
                val dy = p1.second - p0.second
                val len = sqrt(dx * dx + dy * dy)
                if (len < 1e-6f) continue
                val nux = -dy / len
                val nuy = dx / len
                val nx = nux * halfWidth
                val ny = nuy * halfWidth

                val lx0 = p0.first - nx; val ly0 = p0.second - ny
                val rx0 = p0.first + nx; val ry0 = p0.second + ny
                val rx1 = p1.first + nx; val ry1 = p1.second + ny
                val lx1 = p1.first - nx; val ly1 = p1.second - ny

                result.addAll(listOf(lx0, ly0, rx0, ry0, rx1, ry1, lx1, ly1))
                contourResult.add(result.size / 2)

                if (ei == 0) {
                    when (capStyle) {
                        StrokeCap.ROUND -> {
                            val capStart = generateRoundCap(p0, Pair(nux, nuy), halfWidth, segments)
                            result.addAll(listOf(p0.first, p0.second))
                            result.addAll(capStart)
                            contourResult.add(result.size / 2)
                        }
                        StrokeCap.SQUARE -> {
                            val ex = dx / len * halfWidth; val ey = dy / len * halfWidth
                            result.addAll(listOf(
                                p0.first - nx - ex, p0.second - ny - ey,
                                p0.first + nx - ex, p0.second + ny - ey,
                                p0.first + nx, p0.second + ny,
                                p0.first - nx, p0.second - ny,
                            ))
                            contourResult.add(result.size / 2)
                        }
                        StrokeCap.BUTT -> { /* no cap needed */ }
                    }
                }
                if (ei == n - 2) {
                    when (capStyle) {
                        StrokeCap.ROUND -> {
                            val capEnd = generateRoundCap(p1, Pair(-nux, -nuy), halfWidth, segments)
                            result.addAll(listOf(p1.first, p1.second))
                            result.addAll(capEnd)
                            contourResult.add(result.size / 2)
                        }
                        StrokeCap.SQUARE -> {
                            val ex = dx / len * halfWidth; val ey = dy / len * halfWidth
                            result.addAll(listOf(
                                p1.first - nx, p1.second - ny,
                                p1.first + nx, p1.second + ny,
                                p1.first + nx + ex, p1.second + ny + ey,
                                p1.first - nx + ex, p1.second - ny + ey,
                            ))
                            contourResult.add(result.size / 2)
                        }
                        StrokeCap.BUTT -> { /* no cap needed */ }
                    }
                }
            }
            if (n > 2) {
                val edgeNormals = List(n - 1) { index ->
                    edgeNormal(
                        points[index].first,
                        points[index].second,
                        points[index + 1].first,
                        points[index + 1].second,
                    )
                }
                for (index in 1 until n - 1) {
                    val centerPoint = points[index]
                    val incomingNormal = edgeNormals[index - 1]
                    val outgoingNormal = edgeNormals[index]
                    if (joinStyle == StrokeJoin.ROUND) {
                        val right = generateRoundJoin(
                            centerPoint,
                            outgoingNormal,
                            incomingNormal,
                            halfWidth,
                            segments,
                        )
                        for (vertexIndex in 0 until right.size - 2 step 2) {
                            addTriangle(
                                centerPoint.first,
                                centerPoint.second,
                                right[vertexIndex],
                                right[vertexIndex + 1],
                                right[vertexIndex + 2],
                                right[vertexIndex + 3],
                            )
                        }
                        val left = generateRoundJoin(
                            centerPoint,
                            Pair(-outgoingNormal.first, -outgoingNormal.second),
                            Pair(-incomingNormal.first, -incomingNormal.second),
                            halfWidth,
                            segments,
                        )
                        for (vertexIndex in 0 until left.size - 2 step 2) {
                            addTriangle(
                                centerPoint.first,
                                centerPoint.second,
                                left[vertexIndex],
                                left[vertexIndex + 1],
                                left[vertexIndex + 2],
                                left[vertexIndex + 3],
                            )
                        }
                    } else {
                        addBevelOrMiterJoin(
                            centerPoint,
                            incomingNormal,
                            outgoingNormal,
                        )
                    }
                }
            }
        } else {
            val effectiveN = if (isClosed) n - 1 else n
            val edgeNormals = List(effectiveN) { i ->
                edgeNormal(
                    points[i].first, points[i].second,
                    points[(i + 1) % effectiveN].first, points[(i + 1) % effectiveN].second,
                )
            }

            if (joinStyle == StrokeJoin.ROUND) {
                for (i in 0 until effectiveN) {
                    val p0 = points[i]; val p1 = points[(i + 1) % effectiveN]
                    val en = edgeNormals[i]
                    val nx = en.first * halfWidth; val ny = en.second * halfWidth

                    addTriangle(
                        p0.first - nx, p0.second - ny,
                        p0.first + nx, p0.second + ny,
                        p1.first + nx, p1.second + ny,
                    )
                    addTriangle(
                        p0.first - nx, p0.second - ny,
                        p1.first + nx, p1.second + ny,
                        p1.first - nx, p1.second - ny,
                    )
                }

                for (i in 0 until effectiveN) {
                    val p = points[i]
                    val inNorm = edgeNormals[(i + effectiveN - 1) % effectiveN]
                    val outNorm = edgeNormals[i]

                    val joinRight = generateRoundJoin(p, outNorm, inNorm, halfWidth, segments)
                    for (vi in 0 until joinRight.size - 2 step 2) {
                        addTriangle(
                            p.first, p.second,
                            joinRight[vi], joinRight[vi + 1],
                            joinRight[vi + 2], joinRight[vi + 3],
                        )
                    }

                    val joinLeft = generateRoundJoin(
                        p, Pair(-outNorm.first, -outNorm.second),
                        Pair(-inNorm.first, -inNorm.second), halfWidth, segments,
                    )
                    for (vi in 0 until joinLeft.size - 2 step 2) {
                        addTriangle(
                            p.first, p.second,
                            joinLeft[vi], joinLeft[vi + 1],
                            joinLeft[vi + 2], joinLeft[vi + 3],
                        )
                    }
                }
            } else {
                val miterCandidates = if (joinStyle == StrokeJoin.MITER) {
                    List(effectiveN) { index ->
                        admittedMiterOffset(
                            incomingNormal = edgeNormals[(index + effectiveN - 1) % effectiveN],
                            outgoingNormal = edgeNormals[index],
                        )
                    }
                } else {
                    emptyList()
                }
                val admittedMiterOffsets = miterCandidates
                    .takeIf { candidates ->
                        candidates.size == effectiveN && candidates.all { it != null }
                    }
                    ?.map { offset -> requireNotNull(offset) }

                if (admittedMiterOffsets != null) {
                    for (i in 0 until effectiveN) {
                        val p0 = points[i]; val p1 = points[(i + 1) % effectiveN]
                        val n0 = admittedMiterOffsets[i]
                        val n1 = admittedMiterOffsets[(i + 1) % effectiveN]

                        addTriangle(
                            p0.first - n0.first,
                            p0.second - n0.second,
                            p0.first + n0.first,
                            p0.second + n0.second,
                            p1.first + n1.first,
                            p1.second + n1.second,
                        )
                        addTriangle(
                            p0.first - n0.first,
                            p0.second - n0.second,
                            p1.first + n1.first,
                            p1.second + n1.second,
                            p1.first - n1.first,
                            p1.second - n1.second,
                        )
                    }
                } else {
                    for (i in 0 until effectiveN) {
                        val p0 = points[i]; val p1 = points[(i + 1) % effectiveN]
                        val normal = edgeNormals[i]
                        val nx = normal.first * halfWidth
                        val ny = normal.second * halfWidth
                        addTriangle(
                            p0.first - nx,
                            p0.second - ny,
                            p0.first + nx,
                            p0.second + ny,
                            p1.first + nx,
                            p1.second + ny,
                        )
                        addTriangle(
                            p0.first - nx,
                            p0.second - ny,
                            p1.first + nx,
                            p1.second + ny,
                            p1.first - nx,
                            p1.second - ny,
                        )
                    }
                    for (index in 0 until effectiveN) {
                        addBevelOrMiterJoin(
                            center = points[index],
                            incomingNormal = edgeNormals[(index + effectiveN - 1) % effectiveN],
                            outgoingNormal = edgeNormals[index],
                        )
                    }
                }
            }
        }
    }

    return StrokeGeometry(vertices = result, contourStarts = contourResult)
}
