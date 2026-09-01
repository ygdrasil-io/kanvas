package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        val result = PathOpsF32.op(lower, upper, PathBooleanOp.UNION)

        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 0f)))
        assertTrue(PathAnalysisF32.contains(result, Point2F32(1f, 2f)))
    }

    @Test
    fun `public local projected coincidence checks its canonical intersection limit before carrier mutation`() {
        // Keep the ±2^-25 offset at zero, where both literal F32 inputs have distinct raw bits.
        // An offset around y=1 would round away before PathOps sees it and make the fixture
        // backend-dependent rather than a public geometric limit boundary.
        // Its nine source events and one distinct canonical projected endpoint relation require
        // ten events in total.  The projected rail is flattened into many degree-two occurrences,
        // but those are one structural event rather than one event per carrier section.  The
        // existing endpoint-only relation remains part of that canonical event even though it
        // needs no physical carrier cut.
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
                PathOpsLimitsI32(maxIntersections = 9),
            )
        }
        val atBoundary = PathOpsF32.op(
            lower,
            upper,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxIntersections = 10),
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

        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(lower, upper, PathBooleanOp.UNION)
        }

        assertEquals("path-f32-projection-collapse", error.message)
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

        assertEquals("path-half-edge-limit", belowError.message)
        assertEquals(RectF32.ofLTRB(0f, 0f, 2f, 1f), PathAnalysisF32.bounds(atBoundaryF32))
        assertTrue(PathAnalysisF32.contains(atBoundaryF32, Point2F32(1f, .5f)))
        assertFalse(PathAnalysisF32.contains(atBoundaryF32, Point2F32(2.25f, .5f)))
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
    fun `hybrid local candidate budget remains deterministic at its checked boundary`() {
        val first = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val second = PathBuilder().addRect(RectF32.ofLTRB(1f, 0f, 3f, 2f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                first,
                second,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32 - 1),
            )
        }
        val atBoundary = PathOpsF32.op(
            first,
            second,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )

        assertEquals("path-candidate-limit", belowError.message)
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(.5f, 1f)))
        assertTrue(PathAnalysisF32.contains(atBoundary, Point2F32(2.5f, 1f)))
    }

    @Test
    fun `round four local debit rejects the formerly sufficient rectangle budget`() {
        val first = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val second = PathBuilder().addRect(RectF32.ofLTRB(1f, 0f, 3f, 2f)).build()

        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                first,
                second,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = roundThreeOverlappingRectanglesHybridBudgetI32 + 1),
            )
        }

        assertEquals("path-candidate-limit", error.message)
    }

    @Test
    fun `hybrid budget frontier is invariant when canonical inputs are permuted`() {
        val left = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 2f, 2f)).build()
        val right = PathBuilder().addRect(RectF32.ofLTRB(1f, 0f, 3f, 2f)).build()

        val belowError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(
                right,
                left,
                PathBooleanOp.UNION,
                PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32 - 1),
            )
        }
        val forward = PathOpsF32.op(
            left,
            right,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )
        val reverse = PathOpsF32.op(
            right,
            left,
            PathBooleanOp.UNION,
            PathOpsLimitsI32(maxCandidateProbes = overlappingRectanglesHybridBudgetI32),
        )

        assertEquals("path-candidate-limit", belowError.message)
        assertEquals(forward, reverse)
        listOf(Point2F32(.5f, 1f), Point2F32(1.5f, 1f), Point2F32(2.5f, 1f)).forEach { probeF32 ->
            assertEquals(PathAnalysisF32.contains(forward, probeF32), PathAnalysisF32.contains(reverse, probeF32))
        }
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
    fun `public intersect rejects jointly selected collapsed sibling instead of returning empty`() {
        val uF32 = Float.fromBits(1f.toRawBits() + 2)
        fun loop(extra: Boolean): PathF32 {
            val builderF32 = PathBuilder()
            if (extra) builderF32.addRect(RectF32.ofLTRB(10f, 10f, 20f, 20f))
            builderF32
                .moveTo(1f, 1f)
                .cubicTo(uF32, 1f, 1f, uF32, 1f, 1f)
                .close()
            return builderF32.build()
        }

        val collapsedF32 = loop(extra = false)
        val simplifyError = assertFailsWith<IllegalStateException> {
            PathOpsF32.simplify(collapsedF32)
        }
        val intersectError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(loop(extra = true), collapsedF32, PathBooleanOp.INTERSECT)
        }
        val noFaceXorError = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(collapsedF32, loop(extra = false), PathBooleanOp.XOR)
        }

        assertEquals("path-f32-projection-collapse", simplifyError.message)
        assertEquals("path-f32-projection-collapse", intersectError.message)
        assertEquals("path-f32-projection-collapse", noFaceXorError.message)
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

// The round-4 regression keeps the former public success boundary separate, so removal of these
// local debits makes 4_988 observable again.  The current paired limit-1/limit boundary is a
// deterministic public non-regression only; an independent global cost oracle remains Task 5.
private const val roundThreeOverlappingRectanglesHybridBudgetI32 = 4_987
/** Local hybrid-ledger frontier: 6_121 rejects; 6_122 succeeds.  This is not Task 5's oracle. */
private const val overlappingRectanglesHybridBudgetI32 = 6_122
