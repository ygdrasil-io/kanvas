package org.graphiks.math.geometry

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathOpsF32Test {
    private val smallScaleTransformF32 = AffineTransformF32(scale = 1e-5f, translateX = 0f, translateY = 0f)
    private val translationTransformF32 = AffineTransformF32(scale = 1f, translateX = 3_000f, translateY = 3_000f)
    private val largeScaleTranslationTransformF32 =
        AffineTransformF32(scale = 1_000f, translateX = -1_000_000f, translateY = -1_000_000f)

    @Test
    fun `all boolean operations follow membership truth tables for complex finite paths`() {
        complexPathPairsF32().forEach { (name, first, second) ->
            // The offset between axes deliberately avoids the self-crossing diagonals used by
            // the bow-tie fixture. Boolean membership is only compared away from boundaries.
            val probes = probeGridF32(RectF32.ofLTRB(-1.75f, -2.25f, 22.25f, 21.75f), steps = 25)
            PathBooleanOp.entries.forEach { operation ->
                val result = PathOpsF32.op(first, second, operation)
                probes.forEach { point ->
                    val inFirst = PathAnalysisF32.contains(first, point)
                    val inSecond = PathAnalysisF32.contains(second, point)
                    val expected = expectedMembership(
                        operation,
                        inFirst,
                        inSecond,
                    )
                    assertEquals(
                        expected,
                        PathAnalysisF32.contains(result, point),
                        "$name $operation at $point (first=$inFirst, second=$inSecond)",
                    )
                }
            }
        }
    }

    @Test
    fun `boolean operations obey algebraic membership identities`() {
        val first = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
            .addRect(RectF32.ofLTRB(6f, 6f, 14f, 14f))
            .build()
        val second = PathBuilder()
            .moveTo(2f, 2f).lineTo(18f, 5f).lineTo(9f, 19f).close()
            .build()
        val probes = probeGridF32(RectF32.ofLTRB(-2f, -2f, 22f, 22f), steps = 25)

        assertMembershipEquivalentF32(first, PathOpsF32.op(first, first, PathBooleanOp.UNION), probes)
        assertMembershipEquivalentF32(first, PathOpsF32.op(first, first, PathBooleanOp.INTERSECT), probes)
        assertMembershipEquivalentF32(
            PathBuilder(FillRule.WINDING).build(),
            PathOpsF32.op(first, first, PathBooleanOp.XOR),
            probes,
        )
        assertMembershipEquivalentF32(
            PathOpsF32.op(first, second, PathBooleanOp.DIFFERENCE),
            PathOpsF32.op(second, first, PathBooleanOp.REVERSE_DIFFERENCE),
            probes,
        )
    }

    @Test
    fun `metamorphic tangent ovals preserve DIFFERENCE at small scale`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.DIFFERENCE, smallScaleTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve DIFFERENCE at translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.DIFFERENCE, translationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve DIFFERENCE at large scale translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.DIFFERENCE, largeScaleTranslationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve INTERSECT at small scale`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.INTERSECT, smallScaleTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve INTERSECT at translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.INTERSECT, translationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve INTERSECT at large scale translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.INTERSECT, largeScaleTranslationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve UNION at small scale`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.UNION, smallScaleTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve UNION at translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.UNION, translationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve UNION at large scale translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.UNION, largeScaleTranslationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve XOR at small scale`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.XOR, smallScaleTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve XOR at translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.XOR, translationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve XOR at large scale translation`() =
        assertMetamorphicOperationAtTransformF32("tangent ovals", PathBooleanOp.XOR, largeScaleTranslationTransformF32)

    @Test
    fun `metamorphic tangent ovals preserve REVERSE_DIFFERENCE at small scale`() =
        assertMetamorphicOperationAtTransformF32(
            "tangent ovals",
            PathBooleanOp.REVERSE_DIFFERENCE,
            smallScaleTransformF32,
        )

    @Test
    fun `metamorphic tangent ovals preserve REVERSE_DIFFERENCE at translation`() =
        assertMetamorphicOperationAtTransformF32(
            "tangent ovals",
            PathBooleanOp.REVERSE_DIFFERENCE,
            translationTransformF32,
        )

    @Test
    fun `metamorphic tangent ovals preserve REVERSE_DIFFERENCE at large scale translation`() =
        assertMetamorphicOperationAtTransformF32(
            "tangent ovals",
            PathBooleanOp.REVERSE_DIFFERENCE,
            largeScaleTranslationTransformF32,
        )

    @Test
    fun `metamorphic collinear rectangles preserve DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("collinear rectangles", PathBooleanOp.DIFFERENCE)

    @Test
    fun `metamorphic collinear rectangles preserve INTERSECT across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("collinear rectangles", PathBooleanOp.INTERSECT)

    @Test
    fun `metamorphic collinear rectangles preserve UNION across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("collinear rectangles", PathBooleanOp.UNION)

    @Test
    fun `metamorphic collinear rectangles preserve XOR across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("collinear rectangles", PathBooleanOp.XOR)

    @Test
    fun `metamorphic collinear rectangles preserve REVERSE_DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("collinear rectangles", PathBooleanOp.REVERSE_DIFFERENCE)

    @Test
    fun `metamorphic oblique triangles preserve DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("overlapping oblique triangles", PathBooleanOp.DIFFERENCE)

    @Test
    fun `metamorphic oblique triangles preserve INTERSECT across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("overlapping oblique triangles", PathBooleanOp.INTERSECT)

    @Test
    fun `metamorphic oblique triangles preserve UNION across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("overlapping oblique triangles", PathBooleanOp.UNION)

    @Test
    fun `metamorphic oblique triangles preserve XOR across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("overlapping oblique triangles", PathBooleanOp.XOR)

    @Test
    fun `metamorphic oblique triangles preserve REVERSE_DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("overlapping oblique triangles", PathBooleanOp.REVERSE_DIFFERENCE)

    @Test
    fun `metamorphic nested donut preserves DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("nested donut", PathBooleanOp.DIFFERENCE)

    @Test
    fun `metamorphic nested donut preserves INTERSECT across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("nested donut", PathBooleanOp.INTERSECT)

    @Test
    fun `metamorphic nested donut preserves UNION across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("nested donut", PathBooleanOp.UNION)

    @Test
    fun `metamorphic nested donut preserves XOR across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("nested donut", PathBooleanOp.XOR)

    @Test
    fun `metamorphic nested donut preserves REVERSE_DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("nested donut", PathBooleanOp.REVERSE_DIFFERENCE)

    @Test
    fun `metamorphic self intersecting bow ties preserve DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("self intersecting bow ties", PathBooleanOp.DIFFERENCE)

    @Test
    fun `metamorphic self intersecting bow ties preserve INTERSECT across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("self intersecting bow ties", PathBooleanOp.INTERSECT)

    @Test
    fun `metamorphic self intersecting bow ties preserve UNION across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("self intersecting bow ties", PathBooleanOp.UNION)

    @Test
    fun `metamorphic self intersecting bow ties preserve XOR across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("self intersecting bow ties", PathBooleanOp.XOR)

    @Test
    fun `metamorphic self intersecting bow ties preserve REVERSE_DIFFERENCE across transforms`() =
        assertMetamorphicOperationAcrossTransformsF32("self intersecting bow ties", PathBooleanOp.REVERSE_DIFFERENCE)

    @Test
    fun `boolean operations remain finite near one e30 with representable separations`() {
        val anchor = 1e30f
        val anchorBits = anchor.toRawBits()
        fun coordinate(offsetUlps: Int): Float = Float.fromBits(anchorBits + offsetUlps)

        val first = PathBuilder().addRect(
            RectF32.ofLTRB(coordinate(0), coordinate(0), coordinate(16), coordinate(16)),
        ).build()
        val second = PathBuilder().addRect(
            RectF32.ofLTRB(coordinate(8), coordinate(8), coordinate(24), coordinate(24)),
        ).build()
        val probes = listOf(
            Point2F32(coordinate(-4), coordinate(-4)),
            Point2F32(coordinate(4), coordinate(4)),
            Point2F32(coordinate(12), coordinate(12)),
            Point2F32(coordinate(20), coordinate(20)),
            Point2F32(coordinate(28), coordinate(28)),
        )

        PathBooleanOp.entries.forEach { operation ->
            val result = PathOpsF32.op(first, second, operation)
            assertFinitePathF32(result)
            probes.forEach { point ->
                assertEquals(
                    expectedMembership(
                        operation,
                        PathAnalysisF32.contains(first, point),
                        PathAnalysisF32.contains(second, point),
                    ),
                    PathAnalysisF32.contains(result, point),
                    "$operation at $point",
                )
            }
        }
    }

    @Test
    fun `public intersection reports projection collapse for a significant e30 triangle`() {
        val anchor = 1e30f
        val anchorBits = anchor.toRawBits()
        fun coordinate(offsetUlps: Int): Float = Float.fromBits(anchorBits + offsetUlps)
        fun polygon(offsets: List<Pair<Int, Int>>): PathF32 {
            val builder = PathBuilder()
            offsets.forEachIndexed { index, (xOffset, yOffset) ->
                if (index == 0) {
                    builder.moveTo(coordinate(xOffset), coordinate(yOffset))
                } else {
                    builder.lineTo(coordinate(xOffset), coordinate(yOffset))
                }
            }
            return builder.close().build()
        }

        val first = polygon(listOf(0 to 0, 10 to 0, 0 to 10))
        val second = polygon(listOf(-10 to -10, 10 to -10, 7 to -5, -9 to 7, -10 to 10))

        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(first, second, PathBooleanOp.INTERSECT)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects significant outer and hole boundaries that become one F32 cycle`() {
        val normalization = projectionCollapseNormalizationF64()
        val outer = contourF64(
            listOf(
                Point2F64(-0.5, -0.5),
                Point2F64(0.5, -0.5),
                Point2F64(0.5, 0.5),
                Point2F64(-0.5, 0.5),
            ),
        )
        val hole = contourF64(
            listOf(
                Point2F64(-0.4375, -0.4375),
                Point2F64(-0.4375, 0.4375),
                Point2F64(0.4375, 0.4375),
                Point2F64(0.4375, -0.4375),
                Point2F64(0.0, -0.45),
            ),
        )
        val ringDoubleArea = ExpansionF64.expansionSum(
            signedDoubleAreaExpansionF64(outer.vertices.map { it.point } + outer.vertices.first().point),
            signedDoubleAreaExpansionF64(hole.vertices.map { it.point } + hole.vertices.first().point),
        )

        assertTrue(ExpansionF64.sign(ringDoubleArea) > 0)
        assertTrue(
            ExpansionF64.sign(
                ExpansionF64.expansionDiff(ringDoubleArea, doubleArrayOf(2.0.pow(-45))),
            ) > 0,
        )
        val projectedOuter = projectContoursF64ToPathF32(listOf(outer), normalization, FillRule.WINDING)
        val projectedHole = projectContoursF64ToPathF32(listOf(hole), normalization, FillRule.WINDING)
        assertTrue(PathAnalysisF32.bounds(projectedOuter) != null)
        assertTrue(PathAnalysisF32.bounds(projectedHole) != null)
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(outer, hole), normalization, FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a new F32 endpoint contact between source disjoint contours`() {
        val epsilon = 2.0.pow(-25)
        val normalization = affineProjectionNormalizationF64()
        val first = affineProjectionContourF64(
            0.0 to 0.0,
            1.0 to 0.0,
            1.0 to 1.0,
            0.0 to 1.0,
        )
        val second = affineProjectionContourF64(
            1.0 + epsilon to 1.0 + epsilon,
            2.0 + epsilon to 1.0 + epsilon,
            2.0 + epsilon to 2.0 + epsilon,
            1.0 + epsilon to 2.0 + epsilon,
        )

        assertTrue(PathAnalysisF32.bounds(projectContoursF64ToPathF32(listOf(first), normalization, FillRule.WINDING)) != null)
        assertTrue(PathAnalysisF32.bounds(projectContoursF64ToPathF32(listOf(second), normalization, FillRule.WINDING)) != null)
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(first, second), normalization, FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a new F32 partial edge overlap between source disjoint contours`() {
        val epsilon = 2.0.pow(-25)
        val normalization = affineProjectionNormalizationF64()
        val first = affineProjectionContourF64(
            0.0 to 0.5,
            2.0 to 0.5,
            2.0 to 1.5,
            0.0 to 1.5,
        )
        val second = affineProjectionContourF64(
            2.0 + epsilon to 0.5,
            3.0 + epsilon to 0.5,
            3.0 + epsilon to 1.5,
            2.0 + epsilon to 1.5,
        )

        assertTrue(PathAnalysisF32.bounds(projectContoursF64ToPathF32(listOf(first), normalization, FillRule.WINDING)) != null)
        assertTrue(PathAnalysisF32.bounds(projectContoursF64ToPathF32(listOf(second), normalization, FillRule.WINDING)) != null)
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(first, second), normalization, FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a significant narrow bridge whose nonadjacent edges merge`() {
        val epsilon = 2.0.pow(-25)
        val contour = affineProjectionContourF64(
            0.0 to 0.0,
            3.0 to 0.0,
            3.0 to 1.0,
            1.0 + epsilon to 1.0,
            1.0 + epsilon to 2.0,
            3.0 to 2.0,
            3.0 to 3.0,
            0.0 to 3.0,
            0.0 to 2.0,
            1.0 to 2.0,
            1.0 to 1.0,
            0.0 to 1.0,
        )

        assertTrue(epsilon / 9.0 > 2.0.pow(-46))
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(contour), affineProjectionNormalizationF64(), FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a remote F32 endpoint despite a real contact on the contour pair`() {
        val epsilon = 2.0.pow(-25)
        val first = affineProjectionContourF64(
            0.0 to 0.0,
            3.0 to 0.0,
            3.0 to 1.0,
            0.0 to 1.0,
        )
        val second = affineProjectionContourF64(
            0.0 to 1.0,
            0.75 to 1.5,
            2.25 to 1.5,
            3.0 to 1.0 + epsilon,
            3.0 to 3.0 + epsilon / 4.0,
            0.0 to 3.0,
        )

        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(first, second), affineProjectionNormalizationF64(), FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a remote F32 partial overlap despite a real contact on the contour pair`() {
        val epsilon = 2.0.pow(-25)
        val first = affineProjectionContourF64(
            0.0 to 0.0,
            3.0 to 0.0,
            3.0 to 1.0,
            0.0 to 1.0,
        )
        val second = affineProjectionContourF64(
            0.0 to 1.0,
            0.75 to 1.5,
            2.25 to 1.5,
            3.0 + epsilon to 0.5,
            3.0 + epsilon to 1.0 + epsilon,
            3.0 to 3.0 + epsilon / 4.0,
            0.0 to 3.0,
        )

        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(first, second), affineProjectionNormalizationF64(), FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection rejects a compensated significant narrow bridge`() {
        val epsilon = 2.0.pow(-25)
        val contour = affineProjectionContourF64(
            0.0 to 0.0,
            3.0 to 0.0,
            3.0 to 1.0,
            1.0 + epsilon to 1.0,
            1.0 + epsilon to 2.0,
            3.0 to 2.0,
            3.0 to 3.0 - 2.0 * epsilon / 3.0,
            0.0 to 3.0,
            0.0 to 2.0,
            1.0 to 2.0,
            1.0 to 1.0,
            0.0 to 1.0,
        )

        assertTrue(epsilon / 9.0 > 2.0.pow(-46))
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(contour), affineProjectionNormalizationF64(), FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection gives repeated minimum cycles the same budgeted result after rotation and reversal`() {
        val normalization = PathNormalizationF64(origin = Point2F64(0.0, 0.0), scale = 1.0)
        val first = repeatedMinimumCycleF64(rotation = 0, reverse = false)
        val rotated = repeatedMinimumCycleF64(rotation = 1, reverse = false)
        val reversedFirst = repeatedMinimumCycleF64(rotation = 0, reverse = true)
        val reversed = repeatedMinimumCycleF64(rotation = 1, reverse = true)
        val budget = 1_060

        val baselineFailure = projectionFailureMessageF32(listOf(first, first), normalization, budget)
        assertEquals("path-candidate-limit", baselineFailure)
        assertEquals(
            baselineFailure,
            projectionFailureMessageF32(listOf(first, rotated), normalization, budget),
        )
        assertEquals(
            baselineFailure,
            projectionFailureMessageF32(listOf(reversedFirst, reversed), normalization, budget),
        )

        val baseline = projectContoursF64ToPathF32(listOf(first, first), normalization, FillRule.WINDING)
        val rotatedResult = projectContoursF64ToPathF32(listOf(first, rotated), normalization, FillRule.WINDING)
        val reversedResult = projectContoursF64ToPathF32(listOf(reversedFirst, reversed), normalization, FillRule.WINDING)
        val probes = listOf(Point2F32(0.5f, 0.25f), Point2F32(1.5f, 0.25f), Point2F32(3f, 3f))

        assertMembershipEquivalentF32(baseline, rotatedResult, probes)
        assertMembershipEquivalentF32(baseline, reversedResult, probes)
    }

    @Test
    fun `projection group permits exact threshold and rejects above threshold across rotations`() {
        val normalization = PathNormalizationF64(origin = Point2F64(1.5, 1.5), scale = 1.0)
        val outer = projectionInsetRectangleF64(bottomInset = 0.0, reverse = false, rotation = 0)
        val exact = projectionInsetRectangleF64(bottomInset = 2.0.pow(-46), reverse = true, rotation = 1)
        val rotatedExact = projectionInsetRectangleF64(bottomInset = 2.0.pow(-46), reverse = true, rotation = 2)
        val reversedOuter = projectionInsetRectangleF64(bottomInset = 0.0, reverse = true, rotation = 3)
        val reversedExact = projectionInsetRectangleF64(bottomInset = 2.0.pow(-46), reverse = false, rotation = 2)
        val above = projectionInsetRectangleF64(bottomInset = 2.0.pow(-45), reverse = true, rotation = 3)
        val threshold = doubleArrayOf(2.0.pow(-45))
        val exactDifference = ExpansionF64.expansionSum(
            signedDoubleAreaExpansionF64(outer.vertices.map { it.point } + outer.vertices.first().point),
            signedDoubleAreaExpansionF64(exact.vertices.map { it.point } + exact.vertices.first().point),
        )

        assertEquals(0, ExpansionF64.sign(ExpansionF64.expansionDiff(exactDifference, threshold)))
        val exactResult = projectContoursF64ToPathF32(listOf(outer, exact), normalization, FillRule.WINDING)
        val rotatedResult = projectContoursF64ToPathF32(listOf(outer, rotatedExact), normalization, FillRule.WINDING)
        val reversedResult = projectContoursF64ToPathF32(listOf(reversedOuter, reversedExact), normalization, FillRule.WINDING)
        assertMembershipEquivalentF32(
            exactResult,
            rotatedResult,
            listOf(Point2F32(1.5f, 1.5f), Point2F32(0f, 0f)),
        )
        assertMembershipEquivalentF32(
            exactResult,
            reversedResult,
            listOf(Point2F32(1.5f, 1.5f), Point2F32(0f, 0f)),
        )

        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(outer, above), normalization, FillRule.WINDING)
        }
        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection group rejects cumulative all same orientation loss`() {
        val normalization = PathNormalizationF64(origin = Point2F64(1.5, 1.5), scale = 1.0)
        val d = 2.0.pow(-47)
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(
                listOf(
                    projectionInsetRectangleF64(bottomInset = 0.0, reverse = false, rotation = 0),
                    projectionInsetRectangleF64(bottomInset = d, reverse = false, rotation = 1),
                    projectionInsetRectangleF64(bottomInset = 2.0 * d, reverse = false, rotation = 2),
                ),
                normalization,
                FillRule.WINDING,
            )
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection aggregates three coincident F32 cycles beyond the area tolerance`() {
        val d = 3.0 * 2.0.pow(-50)
        val normalization = PathNormalizationF64(origin = Point2F64(1.5, 1.5), scale = 1.0)
        val outer = nestedProjectionSquareF64(0.0, reverse = false)
        val hole = nestedProjectionSquareF64(d, reverse = true)
        val island = nestedProjectionSquareF64(2.0 * d, reverse = false)
        val threshold = 2.0.pow(-45)

        assertTrue(8.0 * d - 8.0 * d * d < threshold)
        assertTrue(16.0 * d - 32.0 * d * d > threshold)
        assertTrue(
            PathAnalysisF32.bounds(
                projectContoursF64ToPathF32(listOf(outer, hole), normalization, FillRule.WINDING),
            ) != null,
        )
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(listOf(outer, hole, island), normalization, FillRule.WINDING)
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `projection drops only a collapsed contour at or below the real area tolerance`() {
        val result = projectContoursF64ToPathF32(
            contours = listOf(collapsedTriangleContourF64(height = 3.0 * 2.0.pow(-24))),
            normalization = projectionCollapseNormalizationF64(),
            fillRule = FillRule.WINDING,
        )

        assertEquals(FillRule.WINDING, result.fillRule)
        assertEquals(null, PathAnalysisF32.bounds(result))
        assertFalse(PathAnalysisF32.contains(result, Point2F32(0f, 0f)))
    }

    @Test
    fun `projection drops a collapsed contour at the exact real area tolerance`() {
        val result = projectContoursF64ToPathF32(
            contours = listOf(collapsedTriangleContourF64(height = 2.0.pow(-22))),
            normalization = projectionCollapseNormalizationF64(),
            fillRule = FillRule.WINDING,
        )

        assertEquals(FillRule.WINDING, result.fillRule)
        assertEquals(null, PathAnalysisF32.bounds(result))
    }

    @Test
    fun `projection rejects a collapsed contour above the real area tolerance`() {
        val error = assertFailsWith<IllegalStateException> {
            projectContoursF64ToPathF32(
                contours = listOf(collapsedTriangleContourF64(height = 5.0 * 2.0.pow(-24))),
                normalization = projectionCollapseNormalizationF64(),
                fillRule = FillRule.WINDING,
            )
        }

        assertEquals("path-f32-projection-collapse", error.message)
    }

    @Test
    fun `asWinding preserves even odd holes and duplicate cancellation`() {
        val holeSource = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
            .addRect(RectF32.ofLTRB(5f, 5f, 15f, 15f))
            .build()
        val holeSourceBefore = holeSource.toList()
        val winding = PathOpsF32.asWinding(holeSource)

        assertEquals(holeSourceBefore, holeSource.toList())
        assertEquals(FillRule.WINDING, winding.fillRule)
        assertTrue(PathAnalysisF32.contains(winding, Point2F32(2f, 2f)))
        assertFalse(PathAnalysisF32.contains(winding, Point2F32(10f, 10f)))
        assertMembershipEquivalentF32(
            holeSource,
            winding,
            probeGridF32(RectF32.ofLTRB(-2f, -2f, 22f, 22f), steps = 25),
        )

        val duplicates = PathBuilder(FillRule.EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .build()
        val duplicatesBefore = duplicates.toList()
        val cancelled = PathOpsF32.asWinding(duplicates)
        assertEquals(duplicatesBefore, duplicates.toList())
        assertEquals(FillRule.WINDING, cancelled.fillRule)
        assertFalse(PathAnalysisF32.contains(cancelled, Point2F32(5f, 5f)))
        assertMembershipEquivalentF32(
            duplicates,
            cancelled,
            probeGridF32(RectF32.ofLTRB(-2f, -2f, 12f, 12f), steps = 17),
        )
    }

    @Test
    fun `asWinding preserves inverse even odd membership`() {
        val source = PathBuilder(FillRule.INVERSE_EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
            .addRect(RectF32.ofLTRB(5f, 5f, 15f, 15f))
            .build()
        val sourceBefore = source.toList()
        val winding = PathOpsF32.asWinding(source)

        assertEquals(sourceBefore, source.toList())
        assertEquals(FillRule.INVERSE_WINDING, winding.fillRule)
        assertMembershipEquivalentF32(
            source,
            winding,
            probeGridF32(RectF32.ofLTRB(-2f, -2f, 22f, 22f), steps = 25),
        )
        assertTrue(PathAnalysisF32.contains(winding, Point2F32(-1f, -1f)))
        assertFalse(PathAnalysisF32.contains(winding, Point2F32(2f, 2f)))
        assertTrue(PathAnalysisF32.contains(winding, Point2F32(10f, 10f)))
    }

    @Test
    fun `simplify resolves self intersections and preserves inverse semantics`() {
        val selfIntersecting = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(0f, 0f).lineTo(12f, 12f).lineTo(0f, 12f).lineTo(12f, 0f).close()
            .build()
        val inverseDuplicates = PathBuilder(FillRule.INVERSE_EVEN_ODD)
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f))
            .build()

        listOf(selfIntersecting, inverseDuplicates).forEach { source ->
            val sourceBefore = source.toList()
            val simplified = PathOpsF32.simplify(source)
            assertEquals(sourceBefore, source.toList())
            assertEquals(source.fillRule, simplified.fillRule)
            assertMembershipEquivalentF32(
                source,
                simplified,
                probeGridF32(RectF32.ofLTRB(-2f, -2f, 14f, 14f), steps = 21),
            )
        }
    }

    @Test
    fun `binary operations reject inverse fills and nonfinite coordinates before returning geometry`() {
        val finite = PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build()
        val inverse = PathBuilder(FillRule.INVERSE_WINDING).addRect(RectF32.ofLTRB(2f, 2f, 8f, 8f)).build()
        val nonfinitePoint = PathBuilder()
            .moveTo(0f, 0f).lineTo(Float.NaN, 1f).lineTo(1f, 0f).close()
            .build()
        val nonfiniteRadius = PathBuilder()
            .moveTo(0f, 0f)
            .arcTo(Float.POSITIVE_INFINITY, 1f, 0f, largeArc = false, sweep = true, 4f, 4f)
            .close()
            .build()
        val nonfiniteRotation = PathBuilder()
            .moveTo(0f, 0f)
            .arcTo(1f, 1f, Float.NaN, largeArc = false, sweep = true, 4f, 4f)
            .close()
            .build()

        assertBinaryRejectsWithoutMutationF32(finite, inverse)
        assertBinaryRejectsWithoutMutationF32(nonfinitePoint, finite)
        assertBinaryRejectsWithoutMutationF32(nonfiniteRadius, finite)
        assertBinaryRejectsWithoutMutationF32(nonfiniteRotation, finite)
    }

    @Test
    fun `internal operation reports the flattening limit without changing sources`() {
        val curved = curvedPathF32()
        assertLimitFailureWithoutMutationF32(
            first = curved,
            second = emptyPathF32(),
            limits = PathOpsLimitsI32(maxFlattenedEdgesPerOperand = 1),
            message = "path-flattening-limit",
        )
    }

    @Test
    fun `unary internal overloads thread flattening limits without changing sources`() {
        val source = curvedPathF32()
        val sourceBefore = source.toList()
        val limits = PathOpsLimitsI32(maxFlattenedEdgesPerOperand = 1)

        val simplifyError = assertFailsWith<IllegalStateException> {
            PathOpsF32.simplify(source, limits)
        }
        assertEquals("path-flattening-limit", simplifyError.message)
        assertEquals(sourceBefore, source.toList())

        val windingError = assertFailsWith<IllegalStateException> {
            PathOpsF32.asWinding(source, limits)
        }
        assertEquals("path-flattening-limit", windingError.message)
        assertEquals(sourceBefore, source.toList())
    }

    @Test
    fun `internal operation reports flattening convergence without changing sources`() {
        val curved = curvedPathF32()
        assertLimitFailureWithoutMutationF32(
            first = curved,
            second = emptyPathF32(),
            limits = PathOpsLimitsI32(maxSubdivisionDepth = 1),
            message = "path-flattening-convergence",
        )
    }

    @Test
    fun `internal operation reports the intersection limit without changing sources`() {
        val (first, second) = overlappingRectanglesF32()
        assertLimitFailureWithoutMutationF32(
            first = first,
            second = second,
            limits = PathOpsLimitsI32(maxIntersections = 1),
            message = "path-intersection-limit",
        )
    }

    @Test
    fun `internal operation reports the candidate limit without changing sources`() {
        val (first, second) = overlappingRectanglesF32()
        assertLimitFailureWithoutMutationF32(
            first = first,
            second = second,
            limits = PathOpsLimitsI32(maxCandidateProbes = 1),
            message = "path-candidate-limit",
        )
    }

    @Test
    fun `internal operation reports the vertex limit without changing sources`() {
        val (first, second) = overlappingRectanglesF32()
        assertLimitFailureWithoutMutationF32(
            first = first,
            second = second,
            limits = PathOpsLimitsI32(maxVertices = 2),
            message = "path-vertex-limit",
        )
    }

    @Test
    fun `internal operation reports the half edge limit without changing sources`() {
        val (first, second) = overlappingRectanglesF32()
        assertLimitFailureWithoutMutationF32(
            first = first,
            second = second,
            limits = PathOpsLimitsI32(maxHalfEdges = 2),
            message = "path-half-edge-limit",
        )
    }

    @Test
    fun `projection keeps canonical screen orientation and untouched signed zero provenance`() {
        val outer = PathBuilder()
            .moveTo(-0f, -0f).lineTo(20f, -0f).lineTo(20f, 20f).lineTo(-0f, 20f).close()
            .build()
        val hole = PathBuilder().addRect(RectF32.ofLTRB(5f, 5f, 15f, 15f)).build()

        val result = PathOpsF32.op(outer, hole, PathBooleanOp.DIFFERENCE)
        val contours = linearClosedContoursF32(result)

        assertEquals(FillRule.WINDING, result.fillRule)
        assertEquals(2, contours.size)
        assertTrue(signedDoubleAreaF32(contours[0]) > 0.0)
        assertTrue(signedDoubleAreaF32(contours[1]) < 0.0)
        val firstMove = result.first() as PathSegmentF32.MoveTo
        assertEquals((-0.0f).toRawBits(), firstMove.point.x.toRawBits())
        assertEquals((-0.0f).toRawBits(), firstMove.point.y.toRawBits())
    }

    @Test
    fun `behavior transform reemits every verb including arcs`() {
        val source = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(1f, 2f)
            .lineTo(3f, 4f)
            .quadTo(5f, 6f, 7f, 8f)
            .cubicTo(9f, 10f, 11f, 12f, 13f, 14f)
            .arcTo(2f, 3f, 33f, largeArc = true, sweep = false, 15f, 16f)
            .close()
            .build()

        val transformed = transformPathF32(source, AffineTransformF32(-2f, 100f, 200f))
        val expected = PathBuilder(FillRule.EVEN_ODD)
            .moveTo(98f, 196f)
            .lineTo(94f, 192f)
            .quadTo(90f, 188f, 86f, 184f)
            .cubicTo(82f, 180f, 78f, 176f, 74f, 172f)
            .arcTo(4f, 6f, 33f, largeArc = true, sweep = false, 70f, 168f)
            .close()
            .build()

        assertEquals(expected, transformed)
    }

    private fun complexPathPairsF32(): List<ComplexPathPairF32> = listOf(
        ComplexPathPairF32(
            name = "overlapping triangles",
            first = PathBuilder().moveTo(0f, 0f).lineTo(14f, 2f).lineTo(4f, 16f).close().build(),
            second = PathBuilder().moveTo(4f, -2f).lineTo(18f, 8f).lineTo(7f, 18f).close().build(),
        ),
        ComplexPathPairF32(
            name = "concave polygon",
            first = PathBuilder()
                .moveTo(0f, 0f).lineTo(16f, 0f).lineTo(16f, 5f)
                .lineTo(7f, 5f).lineTo(7f, 16f).lineTo(0f, 16f).close()
                .build(),
            second = PathBuilder().addRect(RectF32.ofLTRB(3f, 3f, 13f, 13f)).build(),
        ),
        ComplexPathPairF32(
            name = "donut",
            first = PathBuilder(FillRule.EVEN_ODD)
                .addRect(RectF32.ofLTRB(0f, 0f, 20f, 20f))
                .addRect(RectF32.ofLTRB(6f, 6f, 14f, 14f))
                .build(),
            second = PathBuilder().addRect(RectF32.ofLTRB(8f, 8f, 18f, 18f)).build(),
        ),
        ComplexPathPairF32(
            name = "duplicate contours",
            first = PathBuilder(FillRule.EVEN_ODD)
                .addRect(RectF32.ofLTRB(0f, 0f, 12f, 12f))
                .addRect(RectF32.ofLTRB(0f, 0f, 12f, 12f))
                .build(),
            second = PathBuilder().addRect(RectF32.ofLTRB(4f, 4f, 16f, 16f)).build(),
        ),
        ComplexPathPairF32(
            name = "self intersecting bow ties",
            first = PathBuilder(FillRule.EVEN_ODD)
                .moveTo(0f, 0f).lineTo(14f, 14f).lineTo(0f, 14f).lineTo(14f, 0f).close()
                .build(),
            second = PathBuilder(FillRule.EVEN_ODD)
                .moveTo(5f, -2f).lineTo(19f, 12f).lineTo(5f, 12f).lineTo(19f, -2f).close()
                .build(),
        ),
    )

    private data class ComplexPathPairF32(
        val name: String,
        val first: PathF32,
        val second: PathF32,
    )

    private fun assertBinaryRejectsWithoutMutationF32(first: PathF32, second: PathF32) {
        val firstBefore = first.toList()
        val secondBefore = second.toList()
        assertFailsWith<IllegalArgumentException> {
            PathOpsF32.op(first, second, PathBooleanOp.UNION)
        }
        assertEquals(firstBefore, first.toList())
        assertEquals(secondBefore, second.toList())
    }

    private fun assertFinitePathF32(path: PathF32) {
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> assertTrue(segment.point.isFinite())
                is PathSegmentF32.LineTo -> assertTrue(segment.point.isFinite())
                is PathSegmentF32.QuadTo -> {
                    assertTrue(segment.control.isFinite())
                    assertTrue(segment.point.isFinite())
                }
                is PathSegmentF32.CubicTo -> {
                    assertTrue(segment.control1.isFinite())
                    assertTrue(segment.control2.isFinite())
                    assertTrue(segment.point.isFinite())
                }
                is PathSegmentF32.ArcTo -> {
                    assertTrue(segment.radius.isFinite())
                    assertTrue(segment.xAxisRotation.isFinite())
                    assertTrue(segment.point.isFinite())
                }
                PathSegmentF32.Close -> Unit
            }
        }
    }

    private fun assertMetamorphicOperationAcrossTransformsF32(name: String, operation: PathBooleanOp) {
        val case = pathOpCasesF32().single { it.name == name }
        assertMetamorphicMembershipF32(
            case,
            operation,
            listOf(smallScaleTransformF32, translationTransformF32, largeScaleTranslationTransformF32),
        )
    }

    private fun assertMetamorphicOperationAtTransformF32(
        name: String,
        operation: PathBooleanOp,
        transform: AffineTransformF32,
    ) {
        val case = pathOpCasesF32().single { it.name == name }
        assertMetamorphicMembershipF32(case, operation, listOf(transform))
    }

    private fun assertLimitFailureWithoutMutationF32(
        first: PathF32,
        second: PathF32,
        limits: PathOpsLimitsI32,
        message: String,
    ) {
        val firstBefore = first.toList()
        val secondBefore = second.toList()
        val error = assertFailsWith<IllegalStateException> {
            PathOpsF32.op(first, second, PathBooleanOp.UNION, limits)
        }
        assertEquals(message, error.message)
        assertEquals(firstBefore, first.toList())
        assertEquals(secondBefore, second.toList())
    }

    private fun curvedPathF32(): PathF32 = PathBuilder()
        .moveTo(0f, 0f)
        .cubicTo(0f, 100f, 100f, 100f, 100f, 0f)
        .close()
        .build()

    private fun emptyPathF32(): PathF32 = PathBuilder().build()

    private fun contourF64(points: List<Point2F64>): PathContourF64 = PathContourF64(
        points.map { point -> PathContourVertexF64(point, originalPointF32 = null) },
    )

    private fun collapsedTriangleContourF64(height: Double): PathContourF64 = contourF64(
        listOf(
            Point2F64(0.0, 0.0),
            Point2F64(2.0.pow(-23), 0.0),
            Point2F64(0.0, height),
        ),
    )

    private fun affineProjectionContourF64(vararg points: Pair<Double, Double>): PathContourF64 = contourF64(
        points.map { (x, y) -> Point2F64((x - 1.5) / 3.0, (y - 1.5) / 3.0) },
    )

    private fun affineProjectionNormalizationF64(): PathNormalizationF64 = PathNormalizationF64(
        origin = Point2F64(1.5, 1.5),
        scale = 1.0 / 3.0,
    )

    private fun nestedProjectionSquareF64(inset: Double, reverse: Boolean): PathContourF64 {
        val points = listOf(
            Point2F64(-0.5 + inset, -0.5 + inset),
            Point2F64(0.5 - inset, -0.5 + inset),
            Point2F64(0.5 - inset, 0.5 - inset),
            Point2F64(-0.5 + inset, 0.5 - inset),
        )
        return contourF64(if (reverse) points.asReversed() else points)
    }

    private fun repeatedMinimumCycleF64(rotation: Int, reverse: Boolean): PathContourF64 {
        val points = listOf(
            Point2F64(0.0, 0.0),
            Point2F64(1.0, 0.0),
            Point2F64(1.0, 1.0),
            Point2F64(0.0, 0.0),
            Point2F64(2.0, 0.0),
            Point2F64(2.0, 1.0),
        )
        val oriented = if (reverse) points.asReversed() else points
        val normalizedRotation = rotation.mod(oriented.size)
        return contourF64(oriented.drop(normalizedRotation) + oriented.take(normalizedRotation))
    }

    private fun projectionInsetRectangleF64(
        bottomInset: Double,
        reverse: Boolean,
        rotation: Int,
    ): PathContourF64 {
        val points = listOf(
            Point2F64(-0.5, -0.5 + bottomInset),
            Point2F64(0.5, -0.5 + bottomInset),
            Point2F64(0.5, 0.5),
            Point2F64(-0.5, 0.5),
        )
        val oriented = if (reverse) points.asReversed() else points
        val normalizedRotation = rotation.mod(oriented.size)
        return contourF64(oriented.drop(normalizedRotation) + oriented.take(normalizedRotation))
    }

    private fun projectionFailureMessageF32(
        contours: List<PathContourF64>,
        normalization: PathNormalizationF64,
        maxCandidateProbes: Int,
    ): String? = try {
        projectContoursF64ToPathF32(
            contours = contours,
            normalization = normalization,
            fillRule = FillRule.WINDING,
            candidateWorkBudget = PathCandidateWorkBudgetI32(maxCandidateProbes),
        )
        null
    } catch (error: IllegalStateException) {
        error.message
    }

    private fun projectionCollapseNormalizationF64(): PathNormalizationF64 {
        // A power-of-two F32 spacing keeps this internal normalized fixture bit-identical on
        // JVM and JS. The public large-coordinate regression above separately covers 1e30f.
        val ulp = 2.0.pow(-23)
        return PathNormalizationF64(
            origin = Point2F64(1.0 + 2.0 * ulp, 1.0 + 2.0 * ulp),
            scale = 1.0 / (4.0 * ulp),
        )
    }

    private fun overlappingRectanglesF32(): Pair<PathF32, PathF32> =
        PathBuilder().addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)).build() to
            PathBuilder().addRect(RectF32.ofLTRB(5f, -5f, 15f, 5f)).build()

    private fun linearClosedContoursF32(path: PathF32): List<List<Point2F32>> {
        val contours = mutableListOf<List<Point2F32>>()
        var current = mutableListOf<Point2F32>()
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    check(current.isEmpty())
                    current += segment.point
                }
                is PathSegmentF32.LineTo -> current += segment.point
                PathSegmentF32.Close -> {
                    check(current.size >= 3)
                    contours += current
                    current = mutableListOf()
                }
                else -> error("PathOpsF32 output must be linear")
            }
        }
        check(current.isEmpty())
        return contours
    }

    private fun signedDoubleAreaF32(points: List<Point2F32>): Double {
        var area = 0.0
        points.indices.forEach { index ->
            val first = points[index]
            val second = points[(index + 1) % points.size]
            area += first.x.toDouble() * second.y.toDouble() - first.y.toDouble() * second.x.toDouble()
        }
        return area
    }
}
