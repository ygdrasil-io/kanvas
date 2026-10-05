package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val equalSelfClosedCarrierClipF32: PathF32 = PathBuilder()
    // The long public clipping rectangle keeps this reproduction small enough for Kotlin/JS
    // while its left edge still creates the third-party cuts through every repeated carrier.
    .addRect(RectF32.ofLTRB(1.2f, .75f, 1_025f, 2f))
    .build()

private val equalSelfClosedCarrierProbesF32 = listOf(
    Point2F32(1.1f, 1.1f),
    Point2F32(1.35f, 1.35f),
    Point2F32(2f, 1f),
    Point2F32(1_024f, 1f),
)

private fun repeatedEqualSelfClosedCarrierPathF32(countI32: Int, separateContours: Boolean): PathF32 =
    PathBuilder().also { builderF32 ->
        if (separateContours) {
            repeat(countI32) {
                builderF32
                    .moveTo(1f, 1f)
                    .cubicTo(2f, 1f, 1f, 2f, 1f, 1f)
                    .close()
            }
        } else {
            builderF32.moveTo(1f, 1f)
            repeat(countI32) {
                builderF32.cubicTo(2f, 1f, 1f, 2f, 1f, 1f)
            }
            builderF32.close()
        }
    }.build()

private fun expectedEqualSelfClosedCarrierClipContainsF32(operation: PathBooleanOp): List<Boolean> = when (operation) {
    PathBooleanOp.DIFFERENCE -> listOf(true, false, false, false)
    PathBooleanOp.INTERSECT -> listOf(false, true, false, false)
    PathBooleanOp.UNION -> listOf(true, true, true, true)
    PathBooleanOp.XOR -> listOf(true, false, true, true)
    PathBooleanOp.REVERSE_DIFFERENCE -> listOf(false, false, true, true)
}

private fun assertSupportedSingleSelfClosedCarrierClipResultF32(
    countI32: Int,
    separateContours: Boolean,
    operation: PathBooleanOp,
    limitsI32: PathOpsLimitsI32 = PathOpsLimitsI32(),
) {
    val sourceF32 = repeatedEqualSelfClosedCarrierPathF32(countI32, separateContours)
    val resultF32 = PathOpsF32.op(sourceF32, equalSelfClosedCarrierClipF32, operation, limitsI32)
    val label = "count=$countI32 separate=$separateContours operation=$operation"
    assertEquals(
        expectedEqualSelfClosedCarrierClipContainsF32(operation),
        equalSelfClosedCarrierProbesF32.map { probeF32 -> PathAnalysisF32.contains(resultF32, probeF32) },
        label,
    )
}

private fun assertDuplicateSelfClosedCarrierRejectedF32(
    separateContours: Boolean,
    operation: PathBooleanOp,
    swapOperands: Boolean,
    limitsI32: PathOpsLimitsI32 = PathOpsLimitsI32(),
) {
    val carriersF32 = repeatedEqualSelfClosedCarrierPathF32(countI32 = 2, separateContours = separateContours)
    val firstF32 = if (swapOperands) equalSelfClosedCarrierClipF32 else carriersF32
    val secondF32 = if (swapOperands) carriersF32 else equalSelfClosedCarrierClipF32
    val firstBeforeF32 = firstF32.toList()
    val secondBeforeF32 = secondF32.toList()

    val error = assertFailsWith<IllegalStateException> {
        PathOpsF32.op(firstF32, secondF32, operation, limitsI32)
    }

    assertEquals("path-f32-projection-collapse", error.message)
    assertEquals(firstBeforeF32, firstF32.toList())
    assertEquals(secondBeforeF32, secondF32.toList())
}

private fun assertPathF32ProjectionCollapseF32(
    vararg inputsF32: PathF32,
    operation: () -> Unit,
) {
    assertPathF32FailureF32(
        expectedMessage = "path-f32-projection-collapse",
        inputsF32 = inputsF32,
        operation = operation,
    )
}

private fun assertPathF32FailureF32(
    expectedMessage: String,
    vararg inputsF32: PathF32,
    operation: () -> Unit,
) {
    val inputsBeforeF32 = inputsF32.map(PathF32::toList)
    val error = assertFailsWith<IllegalStateException> { operation() }

    assertEquals(expectedMessage, error.message)
    inputsF32.zip(inputsBeforeF32).forEach { (inputF32, beforeF32) ->
        assertEquals(beforeF32, inputF32.toList())
    }
}

private fun thinLensWithDistantSelfClosedPrimitiveF32(): PathF32 {
    val eF32 = 2.0.pow(-23).toFloat()
    return PathBuilder()
        .moveTo(0f, 1f - eF32)
        .quadTo(.5f, 4f, 1f, 1f - eF32)
        .lineTo(2f, -1f)
        .lineTo(0f, -1f)
        .close()
        .moveTo(0f, 1f + eF32)
        .quadTo(.5f, 4f, 1f, 1f + eF32)
        .lineTo(2f, 3f)
        .lineTo(0f, 3f)
        .close()
        .moveTo(10f, 10f)
        .cubicTo(11f, 10f, 10f, 11f, 10f, 10f)
        .close()
        .build()
}

class PathOpsHybridTopologyF32Test {
    @Test
    fun `strict source parameter ULP boundary rejects the sixteenth step including signed zero`() {
        val oneF64 = 1.0
        val fifteenUlpsF64 = Double.fromBits(oneF64.toRawBits() + 15L)
        val sixteenUlpsF64 = Double.fromBits(oneF64.toRawBits() + 16L)

        assertTrue(PathPredicatesF64.almostEqualUlps(oneF64, fifteenUlpsF64, maxUlps = 16, nearZeroMaxUlps = 0))
        assertFalse(PathPredicatesF64.almostEqualUlps(oneF64, sixteenUlpsF64, maxUlps = 16, nearZeroMaxUlps = 0))
        assertTrue(PathPredicatesF64.almostEqualUlps(-0.0, 0.0, maxUlps = 16, nearZeroMaxUlps = 0))
        assertFalse(
            PathPredicatesF64.almostEqualUlps(
                0.0,
                Double.fromBits(16L),
                maxUlps = 16,
                nearZeroMaxUlps = 0,
            ),
        )
    }

    @Test
    fun `hybrid simplify preserves a flattened quadratic carrier instead of its endpoint chord`() {
        val curved = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 2f, 2f, 0f)
            .lineTo(2f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()

        val result = PathOpsF32.simplify(curved)

        // The quadratic reaches y = 1 at x = 1, so this literal probe is inside the source
        // region but would be outside the forbidden endpoint chord from (0, 0) to (2, 0).
        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, .5f)))
        assertEquals(false, PathAnalysisF32.contains(result, Point2F32(1f, 1.25f)))
    }

    @Test
    fun `public quadratic point witness keeps its local projected coincidence without remote authority`() {
        // The ±2^-25 difference sits on the near-zero *end* abscissa, so it survives F32 input
        // construction. At interior t the two F64 rails lie around x=.5 and quantize onto the
        // same F32 lattice; their only source contact is the shared start point.
        val eF32 = 2.0.pow(-25).toFloat()
        val lower = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 0f, -eF32, 4f)
            .lineTo(3f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()
        val upper = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 0f, eF32, 4f)
            .lineTo(3f, 5f)
            .lineTo(0f, 5f)
            .close()
            .build()

        val lowerBeforeF32 = lower.toList()
        val upperBeforeF32 = upper.toList()
        val forwardResultF32 = PathOpsF32.op(lower, upper, PathBooleanOp.UNION)
        val swappedResultF32 = PathOpsF32.op(upper, lower, PathBooleanOp.UNION)

        assertTrue(PathAnalysisF32.contains(forwardResultF32, Point2F32(1f, 0f)))
        assertTrue(PathAnalysisF32.contains(forwardResultF32, Point2F32(1f, 2f)))
        assertEquals(forwardResultF32, swappedResultF32)
        assertEquals(lowerBeforeF32, lower.toList())
        assertEquals(upperBeforeF32, upper.toList())
    }

    @Test
    fun `public local projected coincidence checks its canonical intersection limit before carrier mutation`() {
        // Keep the ±2^-25 offset at zero, where both literal F32 inputs have distinct raw bits.
        // An offset around y=1 would round away before PathOps sees it and make the fixture
        // backend-dependent rather than a public geometric limit boundary.
        // Its nine source events include the endpoint-only relation.  It admits without a
        // physical projected cut, so no tenth public event is created after admission.
        val eF32 = 2.0.pow(-25).toFloat()
        assertTrue(eF32.toRawBits() != 0f.toRawBits())
        assertTrue((-eF32).toRawBits() != eF32.toRawBits())
        val lower = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 0f, -eF32, 4f)
            .lineTo(3f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()
        val upper = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(1f, 0f, eF32, 4f)
            .lineTo(3f, 5f)
            .lineTo(0f, 3f)
            .close()
            .build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                lower,
                upper,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxIntersections = 8),
            )
        }
        val atBoundary = PathOpsF32.op(
            lower,
            upper,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxIntersections = 9),
        )

        assertEquals("path-intersection-limit", belowError.message)
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(1f, 0f)))
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(1f, 2f)))
    }

    @Test
    fun `public quadratic coincidence without the adjacent source witness rejects`() {
        // These curves can acquire the same projected micro-section, but their source starts,
        // ends and parameters are disjoint.  Matching F32 coordinates must not substitute for
        // the adjacent provenance chain used by the preceding successful fixture.
        val eF32 = 2.0.pow(-23).toFloat()
        val lower = PathBuilder()
            .moveTo(0f, 1f - eF32)
            .quadTo(.5f, 4f, 1f, 1f - eF32)
            .lineTo(2f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()
        val upper = PathBuilder()
            .moveTo(0f, 1f + eF32)
            .quadTo(.5f, 4f, 1f, 1f + eF32)
            .lineTo(2f, 3f)
            .lineTo(0f, 3f)
            .close()
            .build()

        assertPathF32ProjectionCollapseF32(lower, upper) {
            PathOpsF32.op(lower, upper, PathBooleanOp.UNION)
        }
        assertPathF32ProjectionCollapseF32(upper, lower) {
            PathOpsF32.op(upper, lower, PathBooleanOp.UNION)
        }
    }

    @Test
    fun `staggered n way exact overlaps stay atomic across operands and contour relabeling`() {
        val leftF32 = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val middleF32 = RectF32.ofLTRB(1f, 0f, 5f, 1f)
        val rightF32 = RectF32.ofLTRB(2f, 0f, 6f, 1f)

        fun path(vararg rectanglesF32: RectF32): PathF32 {
            val builder = PathBuilder()
            rectanglesF32.forEach(builder::addRect)
            return builder.build()
        }

        val variants = listOf(
            path(leftF32, rightF32) to path(middleF32),
            path(rightF32, leftF32) to path(middleF32),
            path(middleF32) to path(leftF32, rightF32),
        )

        variants.forEach { (firstF32, secondF32) ->
            val result = PathOpsF32.op(firstF32, secondF32, PathBooleanOp.UNION)

            listOf(.5f, 1.5f, 3f, 4.5f, 5.5f).forEach { xF32 ->
                assertTrue(PathAnalysisF32.contains(result, Point2F32(xF32, .5f)))
            }
            assertEquals(false, PathAnalysisF32.contains(result, Point2F32(-.25f, .5f)))
            assertEquals(false, PathAnalysisF32.contains(result, Point2F32(6.25f, .5f)))
            assertEquals(RectF32.ofLTRB(0f, 0f, 6f, 1f), PathAnalysisF32.bounds(result))
        }
    }

    @Test
    fun `atomic staggered overlap endpoint groups enforce max intersections before hybrid allocation`() {
        // Three rails [0,4], [1,5], [2,6] have six exact lower events x={0,1,2,4,5,6} and five
        // upper events x={1,2,4,5,6}; the upper-left corner is the one known adjacent contour
        // endpoint, so it is not registered as an intersection. Thus this exact fixture has
        // 6 + 5 = 11 source event groups. Every active carrier gets its direct source cut at an
        // already-counted group; no hybrid allocation may add another one.
        val leftF32 = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val middleF32 = RectF32.ofLTRB(1f, 0f, 5f, 1f)
        val rightF32 = RectF32.ofLTRB(2f, 0f, 6f, 1f)
        val firstF32 = PathBuilder().addRect(leftF32).addRect(rightF32).build()
        val secondF32 = PathBuilder().addRect(middleF32).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                firstF32,
                secondF32,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxIntersections = 10),
            )
        }
        val atBoundaryF32 = PathOpsF32.op(
            firstF32,
            secondF32,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxIntersections = 11),
        )

        assertEquals("path-intersection-limit", belowError.message)
        assertTrue(PathAnalysisF32.contains(atBoundaryF32, Point2F32(3f, .5f)))
        assertFalse(PathAnalysisF32.contains(atBoundaryF32, Point2F32(6.25f, .5f)))
        assertEquals(RectF32.ofLTRB(0f, 0f, 6f, 1f), PathAnalysisF32.bounds(atBoundaryF32))
    }

    @Test
    fun `max half edges counts the final canonical DCEL for identical rectangles`() {
        val firstF32 = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)).build()
        val secondF32 = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                firstF32,
                secondF32,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxHalfEdges = 7),
            )
        }
        val atBoundaryF32 = PathOpsF32.op(
            firstF32,
            secondF32,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxHalfEdges = 8),
        )
        val permutedF32 = PathOpsF32.op(
            secondF32,
            firstF32,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxHalfEdges = 8),
        )

        assertEquals("path-half-edge-limit", belowError.message)
        assertEquals(RectF32.ofLTRB(0f, 0f, 2f, 1f), PathAnalysisF32.bounds(atBoundaryF32))
        assertTrue(PathAnalysisF32.contains(atBoundaryF32, Point2F32(1f, .5f)))
        assertFalse(PathAnalysisF32.contains(atBoundaryF32, Point2F32(2.25f, .5f)))
        assertEquals(atBoundaryF32, permutedF32)
    }

    @Test
    fun `max vertices counts the alias collapsed canonical vertices before DCEL allocation`() {
        val firstF32 = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)).build()
        val secondF32 = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 1f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                firstF32,
                secondF32,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxVertices = 3),
            )
        }
        val atBoundaryF32 = PathOpsF32.op(
            firstF32,
            secondF32,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxVertices = 4),
        )

        assertEquals("path-vertex-limit", belowError.message)
        assertEquals(RectF32.ofLTRB(0f, 0f, 2f, 1f), PathAnalysisF32.bounds(atBoundaryF32))
        assertTrue(PathAnalysisF32.contains(atBoundaryF32, Point2F32(1f, .5f)))
    }

    @Test
    fun `long staggered exact overlaps preserve the public union under operand and contour permutations`() {
        val rectanglesF32 = List(12) { indexI32 ->
            val leftF32 = indexI32.toFloat() * .5f
            RectF32.ofLTRB(leftF32, 0f, leftF32 + 2f, 1f)
        }

        fun path(indicesI32: List<Int>): PathF32 = PathBuilder().also { builderF32 ->
            indicesI32.forEach { indexI32 -> builderF32.addRect(rectanglesF32[indexI32]) }
        }.build()

        val variants = listOf(
            path(listOf(0, 2, 4, 6, 8, 10)) to path(listOf(1, 3, 5, 7, 9, 11)),
            path(listOf(10, 6, 2, 8, 0, 4)) to path(listOf(11, 7, 3, 9, 1, 5)),
            path(listOf(11, 9, 7, 5, 3, 1)) to path(listOf(10, 8, 6, 4, 2, 0)),
        )

        val resultsF32 = variants.map { (firstF32, secondF32) ->
            PathOpsF32.op(firstF32, secondF32, PathBooleanOp.UNION)
        }
        resultsF32.forEach { resultF32 ->
            listOf(.25f, 1.25f, 2.75f, 4.25f, 5.75f).forEach { xF32 ->
                assertTrue(PathAnalysisF32.contains(resultF32, Point2F32(xF32, .5f)))
            }
            assertFalse(PathAnalysisF32.contains(resultF32, Point2F32(-.25f, .5f)))
            assertFalse(PathAnalysisF32.contains(resultF32, Point2F32(7.75f, .5f)))
            assertEquals(RectF32.ofLTRB(0f, 0f, 7.5f, 1f), PathAnalysisF32.bounds(resultF32))
        }
        assertEquals(resultsF32.first(), resultsF32[1])
        assertEquals(resultsF32.first(), resultsF32[2])
    }

    @Test
    fun `high valence exact junction preserves public union across contour and operand permutations`() {
        // Six separated sectors touch at exactly one literal F32 vertex.  The probes are well
        // inside each sector, so their expectations are geometric facts rather than an oracle
        // derived from the arrangement.  This gives the hybrid outgoing-ray sweep a public
        // high-valence junction under both contour and operand permutation.
        val sectorsF32 = listOf(
            listOf(Point2F32(6f, -1f), Point2F32(6f, 1f)),
            listOf(Point2F32(5f, 3f), Point2F32(3f, 5f)),
            listOf(Point2F32(-3f, 5f), Point2F32(-5f, 3f)),
            listOf(Point2F32(-6f, 1f), Point2F32(-6f, -1f)),
            listOf(Point2F32(-5f, -3f), Point2F32(-3f, -5f)),
            listOf(Point2F32(3f, -5f), Point2F32(5f, -3f)),
        )
        fun path(indicesI32: List<Int>): PathF32 = PathBuilder().also { builderF32 ->
            indicesI32.forEach { indexI32 ->
                val (firstPointF32, secondPointF32) = sectorsF32[indexI32]
                builderF32.moveTo(0f, 0f)
                    .lineTo(firstPointF32.x, firstPointF32.y)
                    .lineTo(secondPointF32.x, secondPointF32.y)
                    .close()
            }
        }.build()

        val variantsF32 = listOf(
            path(listOf(0, 2, 4)) to path(listOf(1, 3, 5)),
            path(listOf(4, 0, 2)) to path(listOf(5, 1, 3)),
            path(listOf(5, 3, 1)) to path(listOf(4, 2, 0)),
        )
        val resultsF32 = variantsF32.map { (firstF32, secondF32) ->
            PathOpsF32.op(firstF32, secondF32, PathBooleanOp.UNION)
        }

        resultsF32.forEach { resultF32 ->
            listOf(
                Point2F32(4f, 0f),
                Point2F32(3.75f, 3.75f),
                Point2F32(-3.75f, 3.75f),
                Point2F32(-4f, 0f),
                Point2F32(-3.75f, -3.75f),
                Point2F32(3.75f, -3.75f),
            ).forEach { probeF32 ->
                assertTrue(PathAnalysisF32.contains(resultF32, probeF32), "missing $probeF32")
            }
            assertFalse(PathAnalysisF32.contains(resultF32, Point2F32(0f, 2f)))
        }
        assertEquals(resultsF32.first(), resultsF32[1])
        assertEquals(resultsF32.first(), resultsF32[2])
    }

    @Test
    fun `hybrid representative preserves the semantic signed zero original bits`() {
        val source = PathBuilder()
            .moveTo(-0.0f, -0.0f)
            .lineTo(2.0f, -0.0f)
            .lineTo(0.0f, 2.0f)
            .close()
            .build()

        val result = PathOpsF32.simplify(source)
        val verticesF32 = pathVerticesF32(result)

        assertTrue(verticesF32.any { pointF32 -> pointF32.x.toRawBits() == (-0.0f).toRawBits() })
        assertTrue(verticesF32.any { pointF32 -> pointF32.y.toRawBits() == (-0.0f).toRawBits() })
    }

    @Test
    fun `collinear subdivision crossing remains observable through the public operation`() {
        val first = PathBuilder()
            .moveTo(0f, 0f)
            .quadTo(2f, 0f, 1f, 0f)
            .lineTo(1f, -1f)
            .lineTo(0f, -1f)
            .close()
            .build()
        val second = PathBuilder()
            .addRect(RectF32.ofLTRB(1.24f, -0.5f, 1.26f, 0.5f))
            .build()

        val result = PathOpsF32.op(first, second, PathBooleanOp.UNION)

        listOf(
            Point2F32(0.1f, -0.9f),
            Point2F32(0.5f, -0.5f),
            Point2F32(0.9f, -0.5f),
            Point2F32(1.25f, -0.25f),
            Point2F32(1.25f, 0.25f),
        ).forEach { probe ->
            assertEquals(
                PathAnalysisF32.contains(first, probe) || PathAnalysisF32.contains(second, probe),
                PathAnalysisF32.contains(result, probe),
            )
        }
    }

    @Test
    fun `public zero source area contour drops atomically instead of emitting a partial boundary`() {
        val collinear = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(2f, 0f)
            .lineTo(1f, 0f)
            .close()
            .build()

        val result = PathOpsF32.simplify(collinear)

        assertEquals(null, PathAnalysisF32.bounds(result))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(1f, 0f)))
    }

    @Test
    fun `representable self closed cubic keeps a normal public boundary`() {
        val loopF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(2f, 1f, 1f, 2f, 1f, 1f)
            .close()
            .build()
        val distantF32 = PathBuilder().addRect(RectF32.ofLTRB(10f, 10f, 20f, 20f)).build()

        val simplifiedF32 = PathOpsF32.simplify(loopF32)
        val intersectedF32 = PathOpsF32.op(loopF32, distantF32, PathBooleanOp.INTERSECT)
        val differedF32 = PathOpsF32.op(loopF32, distantF32, PathBooleanOp.DIFFERENCE)

        assertTrue(PathAnalysisF32.contains(simplifiedF32, Point2F32(1.2f, 1.2f)))
        assertEquals(null, PathAnalysisF32.bounds(intersectedF32))
        assertTrue(PathAnalysisF32.contains(differedF32, Point2F32(1.2f, 1.2f)))
    }

    @Test
    fun `significant collapsed sibling cannot be hidden by smaller opposite lobes`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 1)
        val dF32 = Float.fromBits(1f.toRawBits() - 1)
        fun sourceF32(opposite: Boolean): PathF32 = PathBuilder().apply {
            moveTo(1f, 1f)
            repeat(10) {
                cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            }
            close()
            if (opposite) {
                moveTo(1f, 1f)
                repeat(10) {
                    cubicTo(1f, dF32, dF32, 1f, 1f, 1f)
                }
                close()
            }
        }.build()

        val aloneF32 = sourceF32(opposite = false)
        val siblingF32 = sourceF32(opposite = true)
        assertPathF32ProjectionCollapseF32(aloneF32) {
            PathOpsF32.simplify(aloneF32)
        }
        assertPathF32ProjectionCollapseF32(siblingF32) {
            PathOpsF32.simplify(siblingF32)
        }
    }

    @Test
    fun `even odd repeated tiny lobes reject instead of simplifying to an empty path`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 1)
        val sourceF32 = PathBuilder(FillRule.EVEN_ODD).apply {
            moveTo(1f, 1f)
            repeat(10) {
                cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            }
            close()
        }.build()

        assertPathF32ProjectionCollapseF32(sourceF32) {
            PathOpsF32.simplify(sourceF32)
        }
    }

    @Test
    fun `identical collapsed loops reject XOR and difference instead of returning an empty path`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 2)
        val loopF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            .close()
            .build()
        val reversedLoopF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(1f, uF32, uF32, 1f, 1f, 1f)
            .close()
            .build()

        listOf(PathBooleanOp.XOR, PathBooleanOp.DIFFERENCE).forEach { operation ->
            assertPathF32ProjectionCollapseF32(loopF32, loopF32) {
                PathOpsF32.op(loopF32, loopF32, operation)
            }
            assertPathF32ProjectionCollapseF32(loopF32, reversedLoopF32) {
                PathOpsF32.op(loopF32, reversedLoopF32, operation)
            }
        }
    }

    @Test
    fun `significant equal collapsed loops do not cancel outside XOR`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 2)
        val loopF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            .close()
            .build()
        val reversedLoopF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(1f, uF32, uF32, 1f, 1f, 1f)
            .close()
            .build()

        listOf(loopF32, reversedLoopF32).forEach { secondF32 ->
            listOf(PathBooleanOp.UNION, PathBooleanOp.INTERSECT).forEach { operation ->
                assertPathF32ProjectionCollapseF32(loopF32, secondF32) {
                    PathOpsF32.op(loopF32, secondF32, operation)
                }
            }
        }
    }

    // Each case executes one public operation. Kotlin/JS enforces a two-second per-test timeout.
    @Test fun `equal self closed n1 compact difference retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, false, PathBooleanOp.DIFFERENCE)
    @Test fun `equal self closed n1 compact intersect retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, false, PathBooleanOp.INTERSECT)
    @Test fun `equal self closed n1 compact union retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, false, PathBooleanOp.UNION)
    @Test fun `equal self closed n1 compact xor retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, false, PathBooleanOp.XOR)
    @Test fun `equal self closed n1 compact reverse difference retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, false, PathBooleanOp.REVERSE_DIFFERENCE)

    @Test fun `equal self closed n1 separate difference retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, true, PathBooleanOp.DIFFERENCE)
    @Test fun `equal self closed n1 separate intersect retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, true, PathBooleanOp.INTERSECT)
    @Test fun `equal self closed n1 separate union retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, true, PathBooleanOp.UNION)
    @Test fun `equal self closed n1 separate xor retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, true, PathBooleanOp.XOR)
    @Test fun `equal self closed n1 separate reverse difference retains clip cuts`() =
        assertSupportedSingleSelfClosedCarrierClipResultF32(1, true, PathBooleanOp.REVERSE_DIFFERENCE)

    @Test fun `duplicate self closed compact difference rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.DIFFERENCE, false)
    @Test fun `duplicate self closed compact intersect rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.INTERSECT, false)
    @Test fun `duplicate self closed compact union rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.UNION, false)
    @Test fun `duplicate self closed compact xor rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.XOR, false)
    @Test fun `duplicate self closed compact reverse difference rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.REVERSE_DIFFERENCE, false)
    @Test fun `duplicate self closed separate difference rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.DIFFERENCE, false)
    @Test fun `duplicate self closed separate intersect rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.INTERSECT, false)
    @Test fun `duplicate self closed separate union rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.UNION, false)
    @Test fun `duplicate self closed separate xor rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.XOR, false)
    @Test fun `duplicate self closed separate reverse difference rejects`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.REVERSE_DIFFERENCE, false)
    @Test fun `duplicate self closed compact difference rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.DIFFERENCE, true)
    @Test fun `duplicate self closed compact intersect rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.INTERSECT, true)
    @Test fun `duplicate self closed compact union rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.UNION, true)
    @Test fun `duplicate self closed compact xor rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.XOR, true)
    @Test fun `duplicate self closed compact reverse difference rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(false, PathBooleanOp.REVERSE_DIFFERENCE, true)
    @Test fun `duplicate self closed separate difference rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.DIFFERENCE, true)
    @Test fun `duplicate self closed separate intersect rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.INTERSECT, true)
    @Test fun `duplicate self closed separate union rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.UNION, true)
    @Test fun `duplicate self closed separate xor rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.XOR, true)
    @Test fun `duplicate self closed separate reverse difference rejects after operand swap`() =
        assertDuplicateSelfClosedCarrierRejectedF32(true, PathBooleanOp.REVERSE_DIFFERENCE, true)

    @Test
    fun `duplicate self closed budget takes priority over projection rejection`() {
        val carriersF32 = repeatedEqualSelfClosedCarrierPathF32(countI32 = 2, separateContours = false)
        val firstBeforeF32 = carriersF32.toList()
        val secondBeforeF32 = equalSelfClosedCarrierClipF32.toList()

        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                carriersF32,
                equalSelfClosedCarrierClipF32,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = 1),
            )
        }

        assertEquals("path-candidate-limit", error.message)
        assertEquals(firstBeforeF32, carriersF32.toList())
        assertEquals(secondBeforeF32, equalSelfClosedCarrierClipF32.toList())
    }

    @Test
    fun `under threshold collapsed loop rejects in every public fill context`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 1)
        fun tinyLoopF32(builderF32: PathBuilder) {
            builderF32
                .moveTo(1f, 1f)
                .cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
                .close()
        }
        val aloneF32 = PathBuilder().also(::tinyLoopF32).build()
        val nestedF32 = PathBuilder()
            .addRect(RectF32.ofLTRB(0f, 0f, 3f, 3f))
            .also(::tinyLoopF32)
            .build()
        val holedF32 = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 3f, 3f))
            .addRect(RectF32.ofLTRB(.75f, .75f, 1.25f, 1.25f))
            .also(::tinyLoopF32)
            .build()
        val reversedOuterF32 = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(0f, 3f)
            .lineTo(3f, 3f)
            .lineTo(3f, 0f)
            .close()
            .also(::tinyLoopF32)
            .build()
        val inverseF32 = PathBuilder(FillRule.INVERSE_WINDING)
            .addRect(RectF32.ofLTRB(0f, 0f, 3f, 3f))
            .also(::tinyLoopF32)
            .build()
        val boundaryAmbiguityF32 = PathBuilder()
            .addRect(RectF32.ofLTRB(1f, 0f, 3f, 3f))
            .also(::tinyLoopF32)
            .build()

        listOf(aloneF32, nestedF32, holedF32, reversedOuterF32, inverseF32, boundaryAmbiguityF32).forEach { sourceF32 ->
            assertPathF32ProjectionCollapseF32(sourceF32) {
                PathOpsF32.simplify(sourceF32)
            }
        }

        // Translation and scale cannot manufacture support for a collapsed source primitive.
        fun assertTransformedNestedRejectionF32(scaleF32: Float, translationF32: Float) {
            fun transformF32(valueF32: Float): Float = valueF32 * scaleF32 + translationF32
            val transformedAloneF32 = PathBuilder()
                .moveTo(transformF32(1f), transformF32(1f))
                .cubicTo(
                    transformF32(uF32),
                    transformF32(1f),
                    transformF32(1f),
                    transformF32(uF32),
                    transformF32(1f),
                    transformF32(1f),
                )
                .close()
                .build()
            val transformedNestedF32 = PathBuilder()
                .addRect(
                    RectF32.ofLTRB(
                        transformF32(0f),
                        transformF32(0f),
                        transformF32(3f),
                        transformF32(3f),
                    ),
                )
                .also { builderF32 ->
                    builderF32
                        .moveTo(transformF32(1f), transformF32(1f))
                        .cubicTo(
                            transformF32(uF32),
                            transformF32(1f),
                            transformF32(1f),
                            transformF32(uF32),
                            transformF32(1f),
                            transformF32(1f),
                        )
                        .close()
                }
                .build()

            assertPathF32ProjectionCollapseF32(transformedAloneF32) {
                PathOpsF32.simplify(transformedAloneF32)
            }
            assertPathF32ProjectionCollapseF32(transformedNestedF32) {
                PathOpsF32.simplify(transformedNestedF32)
            }
        }
        assertTransformedNestedRejectionF32(scaleF32 = 1f, translationF32 = .125f)
        assertTransformedNestedRejectionF32(scaleF32 = .5f, translationF32 = .125f)
    }

    @Test
    fun `geometrically identical signed zero rotations XOR consistently through the budget`() {
        val canonicalF32 = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(2f, 0f)
            .lineTo(0f, 2f)
            .close()
            .build()
        val rotatedSignedZeroF32 = PathBuilder()
            .moveTo(2f, 0f)
            .lineTo(-0.0f, 2f)
            .lineTo(-0.0f, -0.0f)
            .close()
            .build()

        val resultF32 = PathOpsF32.op(canonicalF32, rotatedSignedZeroF32, PathBooleanOp.XOR)
        val exactBudgetError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                canonicalF32,
                canonicalF32,
                PathBooleanOp.XOR,
                PathOpsLimitsI32(maxCandidateProbes = 1),
            )
        }
        val rotatedBudgetError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                canonicalF32,
                rotatedSignedZeroF32,
                PathBooleanOp.XOR,
                PathOpsLimitsI32(maxCandidateProbes = 1),
            )
        }

        assertEquals(null, PathAnalysisF32.bounds(resultF32))
        assertEquals("path-candidate-limit", exactBudgetError.message)
        assertEquals(exactBudgetError.message, rotatedBudgetError.message)
    }

    @Test
    fun `unselected distant signed zero provenance cannot rewrite selected output`() {
        val selectedPositiveZeroF32 = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(2f, 0f)
            .lineTo(0f, 2f)
            .close()
            .build()
        val distantNegativeZeroF32 = PathBuilder()
            .moveTo(10f, -0.0f)
            .lineTo(12f, -0.0f)
            .lineTo(10f, 2f)
            .close()
            .build()

        val resultF32 = PathOpsF32.op(
            selectedPositiveZeroF32,
            distantNegativeZeroF32,
            PathBooleanOp.DIFFERENCE,
        )
        val zeroYVerticesF32 = pathVerticesF32(resultF32).filter { pointF32 -> pointF32.y == 0f }

        assertTrue(zeroYVerticesF32.isNotEmpty())
        assertTrue(zeroYVerticesF32.all { pointF32 -> pointF32.y.toRawBits() == 0f.toRawBits() })
    }

    @Test
    fun `union of signed zero triangles is invariant under operand order`() {
        val negativeZeroF32 = PathBuilder()
            .moveTo(-0.0f, -0.0f)
            .lineTo(2f, -0.0f)
            .lineTo(-0.0f, 2f)
            .close()
            .build()
        val positiveZeroF32 = PathBuilder()
            .moveTo(0.0f, 0.0f)
            .lineTo(2f, 0.0f)
            .lineTo(0.0f, 2f)
            .close()
            .build()

        val forwardF32 = PathOpsF32.op(negativeZeroF32, positiveZeroF32, PathBooleanOp.UNION)
        val reverseF32 = PathOpsF32.op(positiveZeroF32, negativeZeroF32, PathBooleanOp.UNION)

        assertEquals(forwardF32, reverseF32)
    }

    @Test
    fun `public intersect rejects jointly selected collapsed sibling instead of returning empty`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 2)
        fun loop(extra: Boolean, reverseLobe: Boolean = false): PathF32 {
            val builderF32 = PathBuilder()
            if (extra) builderF32.addRect(RectF32.ofLTRB(10f, 10f, 20f, 20f))
            builderF32.moveTo(1f, 1f)
            if (reverseLobe) {
                builderF32.cubicTo(1f, uF32, uF32, 1f, 1f, 1f)
            } else {
                builderF32.cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            }
            builderF32.close()
            return builderF32.build()
        }

        val collapsedF32 = loop(extra = false)
        val retainedF32 = loop(extra = true)
        val reversedRetainedF32 = loop(extra = true, reverseLobe = true)
        assertPathF32ProjectionCollapseF32(collapsedF32) {
            PathOpsF32.simplify(collapsedF32)
        }
        assertPathF32ProjectionCollapseF32(retainedF32, collapsedF32) {
            PathOpsF32.op(retainedF32, collapsedF32, PathBooleanOp.INTERSECT)
        }
        assertPathF32ProjectionCollapseF32(collapsedF32, retainedF32) {
            PathOpsF32.op(collapsedF32, retainedF32, PathBooleanOp.INTERSECT)
        }
        assertPathF32ProjectionCollapseF32(reversedRetainedF32, collapsedF32) {
            PathOpsF32.op(reversedRetainedF32, collapsedF32, PathBooleanOp.INTERSECT)
        }
        val distantF32 = PathBuilder()
            .addRect(RectF32.ofLTRB(30f, 30f, 40f, 40f))
            .build()
        assertPathF32ProjectionCollapseF32(collapsedF32, distantF32) {
            PathOpsF32.op(collapsedF32, distantF32, PathBooleanOp.INTERSECT)
        }

    }

    @Test
    fun `as winding admits polygons and rejects collapsed self closed curves`() {
        val polygonF32 = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val uF32 = Float.fromBits(1f.toRawBits() + 1)
        val collapsedF32 = PathBuilder()
            .moveTo(1f, 1f)
            .cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
            .close()
            .build()
        val duplicateF32 = repeatedEqualSelfClosedCarrierPathF32(countI32 = 2, separateContours = false)
        val polygonBeforeF32 = polygonF32.toList()

        val windingF32 = PathOpsF32.asWinding(polygonF32)

        assertTrue(PathAnalysisF32.contains(windingF32, Point2F32(1f, 1f)))
        assertEquals(polygonBeforeF32, polygonF32.toList())
        assertPathF32ProjectionCollapseF32(collapsedF32) { PathOpsF32.asWinding(collapsedF32) }
        assertPathF32ProjectionCollapseF32(duplicateF32) { PathOpsF32.asWinding(duplicateF32) }
    }

    @Test
    fun `thin lens overlapping projected claims reject atomically`() {
        val lensF32 = thinLensWithDistantSelfClosedPrimitiveF32()
        val rectangleF32 = PathBuilder().addRect(RectF32.ofLTRB(20f, 20f, 21f, 21f)).build()

        assertPathF32ProjectionCollapseF32(lensF32) { PathOpsF32.simplify(lensF32) }
        assertPathF32ProjectionCollapseF32(lensF32, lensF32) {
            PathOpsF32.op(lensF32, lensF32, PathBooleanOp.UNION)
        }
        assertPathF32ProjectionCollapseF32(lensF32, lensF32) {
            PathOpsF32.op(lensF32, lensF32, PathBooleanOp.INTERSECT)
        }
        assertPathF32ProjectionCollapseF32(lensF32, rectangleF32) {
            PathOpsF32.op(lensF32, rectangleF32, PathBooleanOp.UNION)
        }
        assertPathF32ProjectionCollapseF32(rectangleF32, lensF32) {
            PathOpsF32.op(rectangleF32, lensF32, PathBooleanOp.UNION)
        }
    }

    @Test
    fun `thin lens candidate limit takes priority while projection precedes intersection limit`() {
        val lensF32 = thinLensWithDistantSelfClosedPrimitiveF32()
        assertPathF32FailureF32("path-candidate-limit", lensF32) {
            PathOpsF32.simplify(lensF32, PathOpsLimitsI32(maxCandidateProbes = 1))
        }

        assertPathF32ProjectionCollapseF32(lensF32) {
            PathOpsF32.simplify(lensF32, PathOpsLimitsI32(maxIntersections = 1))
        }
    }

    @Test
    fun `one public n way contact commits all pair relations atomically`() {
        // One contour meets two independently closed contours at the same exact public vertex.
        // The three probes are strictly inside their sectors, so they expose a lost pair relation
        // without inspecting aliases, claims, vertices, or the DCEL.
        val firstF32 = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(-2f, 1f)
            .lineTo(-2f, -1f)
            .close()
            .build()
        val secondF32 = PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(2f, 2f)
            .lineTo(2f, 1f)
            .close()
            .moveTo(0f, 0f)
            .lineTo(2f, -1f)
            .lineTo(2f, -2f)
            .close()
            .build()
        val firstBefore = firstF32.toList()
        val secondBefore = secondF32.toList()

        val resultF32 = PathOpsF32.op(firstF32, secondF32, PathBooleanOp.UNION)

        listOf(
            Point2F32(-1.5f, 0f),
            Point2F32(1.5f, 1.4f),
            Point2F32(1.5f, -1.4f),
        ).forEach { probeF32 ->
            assertTrue(PathAnalysisF32.contains(resultF32, probeF32), "missing $probeF32")
        }
        assertEquals(firstBefore, firstF32.toList())
        assertEquals(secondBefore, secondF32.toList())
    }

    @Test
    fun `two public disjoint contacts on one segment stay independent under input permutations`() {
        val mainF32 = PathBuilder()
            .addRect(RectF32.ofLTRB(0f, 0f, 4f, 2f))
            .build()
        fun touchesF32(reverseContours: Boolean): PathF32 = PathBuilder().also { builderF32 ->
            val anchorsF32 = if (reverseContours) listOf(3f, 1f) else listOf(1f, 3f)
            anchorsF32.forEach { anchorF32 ->
                builderF32
                    .moveTo(anchorF32, 0f)
                    .lineTo(anchorF32 - .5f, -1f)
                    .lineTo(anchorF32 + .5f, -1f)
                    .close()
            }
        }.build()

        val firstTouchesF32 = touchesF32(reverseContours = false)
        val reversedTouchesF32 = touchesF32(reverseContours = true)
        val mainBefore = mainF32.toList()
        val touchesBefore = firstTouchesF32.toList()
        val variantsF32 = listOf(
            PathOpsF32.op(mainF32, firstTouchesF32, PathBooleanOp.UNION),
            PathOpsF32.op(reversedTouchesF32, mainF32, PathBooleanOp.UNION),
        )

        variantsF32.forEach { resultF32 ->
            listOf(
                Point2F32(2f, 1f),
                Point2F32(1f, -.5f),
                Point2F32(3f, -.5f),
            ).forEach { probeF32 ->
                assertTrue(PathAnalysisF32.contains(resultF32, probeF32), "missing $probeF32")
            }
            assertFalse(PathAnalysisF32.contains(resultF32, Point2F32(2f, -.5f)))
        }
        assertEquals(variantsF32.first(), variantsF32.last())
        assertEquals(mainBefore, mainF32.toList())
        assertEquals(touchesBefore, firstTouchesF32.toList())
    }

    @Test
    fun `public finite extreme and subnormal translation keep a finite deterministic result`() {
        val extremeF32 = PathBuilder()
            .addRect(RectF32.ofLTRB(0f, 0f, Float.MAX_VALUE, 1f))
            .build()
        val extremeResultF32 = PathOpsF32.simplify(extremeF32)
        val extremeBoundsF32 = requireNotNull(PathAnalysisF32.bounds(extremeResultF32))
        assertTrue(extremeBoundsF32.isFinite())
        assertEquals(Float.MAX_VALUE, extremeBoundsF32.right)

        val translationF32 = Float.fromBits(0x00000100)
        val spanF32 = Float.fromBits(0x00001000)
        val tinyF32 = PathBuilder()
            .addRect(
                RectF32.ofLTRB(
                    translationF32,
                    translationF32,
                    translationF32 + spanF32,
                    translationF32 + spanF32,
                ),
            )
            .build()
        val tinyResultF32 = PathOpsF32.simplify(tinyF32)
        val tinyBoundsF32 = requireNotNull(PathAnalysisF32.bounds(tinyResultF32))
        assertTrue(tinyBoundsF32.isFinite())
        assertTrue(PathAnalysisF32.contains(tinyResultF32, Point2F32(translationF32 + spanF32 * .5f, translationF32 + spanF32 * .5f)))
    }

}

private fun pathVerticesF32(path: PathF32): List<Point2F32> = buildList {
    path.forEach { segmentF32 ->
        when (segmentF32) {
            is PathSegmentF32.MoveTo -> add(segmentF32.point)
            is PathSegmentF32.LineTo -> add(segmentF32.point)
            is PathSegmentF32.QuadTo -> add(segmentF32.point)
            is PathSegmentF32.CubicTo -> add(segmentF32.point)
            is PathSegmentF32.ArcTo -> add(segmentF32.point)
            PathSegmentF32.Close -> Unit
        }
    }
}
