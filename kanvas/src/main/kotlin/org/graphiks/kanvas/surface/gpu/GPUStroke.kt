package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class StrokeGeometry(
    val vertices: List<Float>,
    val contourStarts: List<Int>,
    val coordinateSpace: StrokeGeometryCoordinateSpace =
        StrokeGeometryCoordinateSpace.DEVICE,
)

enum class StrokeGeometryCoordinateSpace {
    DEVICE,
}

internal data class DashRun(
    val points: List<Pair<Float, Float>>,
    val closed: Boolean,
)

internal fun applyDashRuns(
    points: List<Pair<Float, Float>>,
    dashArray: FloatArray,
    phase: Float,
    closed: Boolean,
): List<DashRun> {
    if (points.isEmpty() || dashArray.isEmpty()) return emptyList()
    val intervals = dashArray.map { it.coerceAtLeast(0.1f) }
    var dashIdx = 0
    val totalDashLen = intervals.sum().coerceAtLeast(1f)
    var intervalOffset = phase % totalDashLen
    if (intervalOffset < 0f) intervalOffset += totalDashLen
    while (intervalOffset >= intervals[dashIdx % intervals.size]) {
        intervalOffset -= intervals[dashIdx % intervals.size]
        dashIdx++
    }
    val startsOn = dashIdx % 2 == 0
    val traversalPoints = if (
        closed &&
        points.size > 1 &&
        !pointsClose(points.first(), points.last())
    ) {
        points + points.first()
    } else {
        points
    }
    val hasLength = traversalPoints.zipWithNext().any { (start, end) ->
        !pointsClose(start, end)
    }
    if (!hasLength) {
        return if (startsOn) {
            listOf(DashRun(points = listOf(points.first()), closed = false))
        } else {
            emptyList()
        }
    }

    val finishedRuns = mutableListOf<DashRun>()
    var activePoints: MutableList<Pair<Float, Float>>? = null

    fun appendPoint(runPoints: MutableList<Pair<Float, Float>>, point: Pair<Float, Float>) {
        if (runPoints.isEmpty() || !pointsClose(runPoints.last(), point)) {
            runPoints.add(point)
        }
    }

    fun finishActiveRun() {
        val runPoints = activePoints ?: return
        if (runPoints.size >= 2) {
            finishedRuns.add(DashRun(points = runPoints.toList(), closed = false))
        }
        activePoints = null
    }

    for (i in 0 until traversalPoints.size - 1) {
        val p0 = traversalPoints[i]
        val p1 = traversalPoints[i + 1]
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
                val runPoints = activePoints ?: mutableListOf<Pair<Float, Float>>().also {
                    activePoints = it
                }
                appendPoint(runPoints, Pair(p0.first + pos * nx, p0.second + pos * ny))
                appendPoint(runPoints, Pair(p0.first + endPos * nx, p0.second + endPos * ny))
            }

            pos = endPos
            intervalOffset += effectiveLen
            if (intervalOffset >= dashLen - 1e-6f) {
                if (idx % 2 == 0) {
                    finishActiveRun()
                }
                dashIdx++
                intervalOffset = 0f
            } else if (pos >= segLen - 1e-6f) {
                break
            }
        }
    }

    if (closed && startsOn && activePoints != null) {
        if (finishedRuns.isEmpty()) {
            val closedPoints = activePoints!!.toMutableList()
            appendPoint(closedPoints, closedPoints.first())
            return listOf(DashRun(points = closedPoints, closed = true))
        }
        val merged = activePoints!!.toMutableList()
        val firstRun = finishedRuns.removeAt(0)
        firstRun.points.forEach { point -> appendPoint(merged, point) }
        finishedRuns.add(0, DashRun(points = merged, closed = false))
        activePoints = null
    }
    finishActiveRun()
    return finishedRuns
}

private fun pointsClose(
    first: Pair<Float, Float>,
    second: Pair<Float, Float>,
): Boolean =
    kotlin.math.abs(first.first - second.first) < 1e-6f &&
        kotlin.math.abs(first.second - second.second) < 1e-6f

internal fun applyDash(
    points: List<Pair<Float, Float>>,
    dashArray: FloatArray,
    phase: Float,
): List<Pair<Float, Float>> = applyDashRuns(
    points = points,
    dashArray = dashArray,
    phase = phase,
    closed = false,
).flatMap { run -> run.points.zipWithNext().flatMap { (start, end) -> listOf(start, end) } }

/** Fixed tessellation used by the native non-AA round-cap outline generator. */
internal const val ROUND_CAP_TESSELLATION_SEGMENTS = 6

internal fun generateRoundCap(
    center: Pair<Float, Float>,
    normal: Pair<Float, Float>,
    halfW: Float,
    segments: Int = ROUND_CAP_TESSELLATION_SEGMENTS,
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
    transform: GPUTransformFacts = GPUTransformFacts.identity(),
    closedContours: Set<Int> = emptySet(),
): StrokeGeometry {
    val transformValues = listOf(
        transform.translateX,
        transform.translateY,
        transform.scaleX,
        transform.scaleY,
        transform.skewX,
        transform.skewY,
    )
    if (
        contourVertices.size < 2 ||
        !strokeWidth.isFinite() ||
        strokeWidth < 0f ||
        !miterLimit.isFinite() ||
        miterLimit <= 0f ||
        transform.type == GPUTransformType.Perspective ||
        transform.type == GPUTransformType.Singular ||
        transformValues.any { value -> !value.isFinite() }
    ) {
        return StrokeGeometry(emptyList(), listOf(0))
    }

    fun mapToDevice(x: Float, y: Float): Pair<Float, Float> = Pair(
        transform.scaleX * x + transform.skewX * y + transform.translateX,
        transform.skewY * x + transform.scaleY * y + transform.translateY,
    )

    if (dashArray != null && dashArray.isNotEmpty()) {
        val dashedVertices = mutableListOf<Float>()
        val dashedContourStarts = mutableListOf(0)
        for (contourIndex in contourStarts.indices) {
            val start = contourStarts[contourIndex]
            val end = if (contourIndex + 1 < contourStarts.size) {
                contourStarts[contourIndex + 1]
            } else {
                contourVertices.size / 2
            }
            val points = List(end - start) { pointIndex ->
                val vertexIndex = (start + pointIndex) * 2
                Pair(contourVertices[vertexIndex], contourVertices[vertexIndex + 1])
            }
            if (points.isEmpty()) continue
            val isExplicitlyClosed =
                points.size >= 3 && pointsClose(points.first(), points.last())
            val dashRuns = applyDashRuns(
                points = points,
                dashArray = dashArray,
                phase = dashPhase,
                closed = isExplicitlyClosed || contourIndex in closedContours,
            )
            dashRuns.forEach { run ->
                val runGeometry = strokeToFillGeometry(
                    contourVertices = run.points.flatMap { point ->
                        listOf(point.first, point.second)
                    },
                    contourStarts = listOf(0),
                    strokeWidth = strokeWidth,
                    capStyle = capStyle,
                    joinStyle = joinStyle,
                    miterLimit = miterLimit,
                    transform = transform,
                    closedContours = if (run.closed) setOf(0) else emptySet(),
                )
                val vertexOffset = dashedVertices.size / 2
                dashedVertices.addAll(runGeometry.vertices)
                runGeometry.contourStarts.drop(1).forEach { runContourEnd ->
                    dashedContourStarts.add(vertexOffset + runContourEnd)
                }
            }
        }
        return StrokeGeometry(
            vertices = dashedVertices,
            contourStarts = dashedContourStarts,
            coordinateSpace = StrokeGeometryCoordinateSpace.DEVICE,
        )
    }

    val geometryInput = if (strokeWidth == 0f) {
        contourVertices.chunked(2).flatMap { (x, y) ->
            val mapped = mapToDevice(x, y)
            listOf(mapped.first, mapped.second)
        }
    } else {
        contourVertices
    }
    val effectiveWidth = if (strokeWidth == 0f) 1f else strokeWidth
    val halfWidth = effectiveWidth / 2f
    // A positive uniform scale magnifies the local round-cap polygon. Use a denser
    // device-space approximation for the first promoted scaled lane while retaining
    // the six-segment pixel-exact contract for the integral radius-two routes.
    val segments = if (
        transform.scaleX > 1f && transform.scaleX == transform.scaleY &&
        transform.skewX == 0f && transform.skewY == 0f
    ) {
        ROUND_CAP_TESSELLATION_SEGMENTS * 2
    } else {
        ROUND_CAP_TESSELLATION_SEGMENTS
    }
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
        val side = if (turn > 0f) -1f else 1f
        val incomingOuter = Pair(
            center.first + incomingNormal.first * halfWidth * side,
            center.second + incomingNormal.second * halfWidth * side,
        )
        val outgoingOuter = Pair(
            center.first + outgoingNormal.first * halfWidth * side,
            center.second + outgoingNormal.second * halfWidth * side,
        )
        if (joinStyle == StrokeJoin.BEVEL) {
            if (turn > 0f) {
                addTriangle(
                    center.first,
                    center.second,
                    outgoingOuter.first,
                    outgoingOuter.second,
                    incomingOuter.first,
                    incomingOuter.second,
                )
            } else {
                addTriangle(
                    center.first,
                    center.second,
                    incomingOuter.first,
                    incomingOuter.second,
                    outgoingOuter.first,
                    outgoingOuter.second,
                )
            }
            return
        }

        val miterOffset = admittedMiterOffset(incomingNormal, outgoingNormal)
        if (miterOffset == null) {
            if (turn > 0f) {
                addTriangle(
                    center.first,
                    center.second,
                    outgoingOuter.first,
                    outgoingOuter.second,
                    incomingOuter.first,
                    incomingOuter.second,
                )
            } else {
                addTriangle(
                    center.first,
                    center.second,
                    incomingOuter.first,
                    incomingOuter.second,
                    outgoingOuter.first,
                    outgoingOuter.second,
                )
            }
            return
        }
        val miter = Pair(
            center.first + miterOffset.first * side,
            center.second + miterOffset.second * side,
        )
        if (turn > 0f) {
            addTriangle(
                incomingOuter.first,
                incomingOuter.second,
                outgoingOuter.first,
                outgoingOuter.second,
                miter.first,
                miter.second,
            )
        } else {
            addTriangle(
                incomingOuter.first,
                incomingOuter.second,
                miter.first,
                miter.second,
                outgoingOuter.first,
                outgoingOuter.second,
            )
        }
    }

    for (ci in contourStarts.indices) {
        val start = contourStarts[ci]
        val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else geometryInput.size / 2
        val n = end - start

        val points = List(n) { idx ->
            val i = (start + idx) * 2
            Pair(geometryInput[i], geometryInput[i + 1])
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

        val isExplicitlyClosed = n >= 3 && pointsClose(points.first(), points.last())
        val isClosed = isExplicitlyClosed || ci in closedContours

        if (!isClosed || n == 2) {
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
            val effectiveN = if (isExplicitlyClosed) n - 1 else n
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

    val deviceVertices = if (strokeWidth == 0f) {
        result
    } else {
        result.chunked(2).flatMap { (x, y) ->
            val mapped = mapToDevice(x, y)
            listOf(mapped.first, mapped.second)
        }
    }
    return StrokeGeometry(
        vertices = deviceVertices,
        contourStarts = contourResult,
        coordinateSpace = StrokeGeometryCoordinateSpace.DEVICE,
    )
}
