package org.graphiks.math.codegen

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinEmitterTest {
    @Test
    fun `same schema emits byte-identical sorted files`() {
        val first = KotlinEmitter.emit(MathPrimitiveManifest.schema)
        val second = KotlinEmitter.emit(MathPrimitiveManifest.schema)

        assertEquals(first.files.map { it.relativePath }.sorted(), first.files.map { it.relativePath })
        assertContentEquals(
            first.files.flatMap { it.utf8.asIterable() },
            second.files.flatMap { it.utf8.asIterable() },
        )
    }

    @Test
    fun `manifest emits only the selected primitive inventory`() {
        assertEquals(
            listOf(
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/MutablePoint2F32.kt",
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/MutablePoint2F64.kt",
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2F32.kt",
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2F64.kt",
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point2I32.kt",
                "math/geometry/src/generated/kotlin/org/graphiks/math/geometry/Point3F32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/MutableVector2F32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/MutableVector2F64.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/MutableVector3F32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector2F32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector2F64.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector2I32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector3F32.kt",
                "math/vector/src/generated/kotlin/org/graphiks/math/vector/Vector4F32.kt",
            ),
            KotlinEmitter.emit(MathPrimitiveManifest.schema).files.map { it.relativePath },
        )
    }

    @Test
    fun `selected F32 and F64 points emit named scalar conversions only`() {
        val tree = KotlinEmitter.emit(MathPrimitiveManifest.schema)
        val f32 = emitted(tree, "Point2F32.kt")
        val f64 = emitted(tree, "Point2F64.kt")

        assertContains(
            f32,
            "public fun Point2F32.toPoint2F64(): Point2F64 = Point2F64(x.toDouble(), y.toDouble())",
        )
        assertContains(
            f64,
            "public fun Point2F64.toPoint2F32(): Point2F32 = Point2F32(x.toFloat(), y.toFloat())",
        )
        assertFalse("toVector" in f32)
        assertFalse("toVector" in f64)
    }

    @Test
    fun `disabled immutable F64 point is not a conversion target`() {
        val schema = MathPrimitiveManifest.schema.copy(
            primitives = MathPrimitiveManifest.schema.primitives.map { primitive ->
                if (primitive.semantic == Semantic.POINT &&
                    primitive.dimension == 2 &&
                    primitive.scalar == ScalarId.F64
                ) {
                    primitive.copy(generateImmutable = false, generateMutable = false)
                } else {
                    primitive
                }
            },
        )
        val tree = KotlinEmitter.emit(schema)
        val paths = tree.files.map { it.relativePath }
        val f32 = emitted(tree, "Point2F32.kt")

        assertTrue(paths.any { it.endsWith("/Point2F32.kt") })
        assertFalse(paths.any { it.endsWith("/Point2F64.kt") })
        assertFalse(paths.any { it.endsWith("/MutablePoint2F64.kt") })
        assertFalse("toPoint2F64" in f32)
    }

    @Test
    fun `point emission selects point vector operations only`() {
        val source = emitted("Point2F32.kt")

        assertContains(source, "operator fun plus(delta: Vector2F32): Point2F32")
        assertContains(source, "operator fun minus(delta: Vector2F32): Point2F32")
        assertContains(source, "operator fun minus(other: Point2F32): Vector2F32")
        assertFalse("fun unaryMinus" in source)
        assertFalse("operator fun times" in source)
        assertFalse("fun dot" in source)
        assertFalse("fun cross" in source)
        assertFalse("fun normalized" in source)
    }

    @Test
    fun `F64 point emission includes an overflow resistant midpoint`() {
        val source = emitted("Point2F64.kt")

        assertContains(source, "public fun midpointTo(other: Point2F64): Point2F64")
        assertContains(source, "if ((x < 0.0) == (other.x < 0.0))")
        assertContains(source, "x + (other.x - x) * 0.5")
        assertContains(source, "x * 0.5 + other.x * 0.5")
    }

    @Test
    fun `I32 point emission uses saturating scalar operations`() {
        val source = emitted("Point2I32.kt")

        assertContains(source, "import org.graphiks.math.scalar.saturatingAddI32")
        assertContains(source, "import org.graphiks.math.scalar.saturatingSubtractI32")
        assertContains(source, "operator fun plus(delta: Vector2I32): Point2I32 = Point2I32(saturatingAddI32(x, delta.x), saturatingAddI32(y, delta.y))")
        assertContains(source, "operator fun minus(other: Point2I32): Vector2I32 = Vector2I32(saturatingSubtractI32(x, other.x), saturatingSubtractI32(y, other.y))")
        assertFalse("fun distanceTo" in source)
        assertFalse("fun midpointTo" in source)
        assertFalse("fun isFinite" in source)
    }

    @Test
    fun `fallback surface excludes data class conveniences`() {
        val source = emitted("Vector2F32.kt")

        assertTrue(
            source.startsWith(
                "// Generated by :math:geometry-codegen.\n" +
                    "// Edit MathPrimitiveManifest.kt and run generateMathPrimitives.\n",
            ),
        )
        assertContains(source, "package org.graphiks.math.vector")
        assertContains(source, "public class Vector2F32(")
        assertContains(source, "public val x: Float")
        assertContains(source, "public val y: Float")
        assertFalse("data class" in source)
        assertFalse("fun copy(" in source)
        assertFalse("component1" in source)
        assertFalse("fun of(" in source)
        assertFalse("this === other" in source)
        assertContains(
            source,
            "override fun toString(): String = \"Vector2F32(x=\$x, y=\$y)\"",
        )
        assertFalse("Generated on" in source)
        assertFalse("/Users/" in source)
    }

    @Test
    fun `all emitted sources use LF and one final newline`() {
        KotlinEmitter.emit(MathPrimitiveManifest.schema).files.forEach { file ->
            val source = file.utf8.decodeToString()
            assertFalse('\r' in source, file.relativePath)
            assertTrue(source.endsWith("\n"), file.relativePath)
            assertFalse(source.endsWith("\n\n"), file.relativePath)
        }
    }

    private fun emitted(fileName: String): String =
        emitted(KotlinEmitter.emit(MathPrimitiveManifest.schema), fileName)

    private fun emitted(tree: GeneratedTree, fileName: String): String =
        tree.files
            .single { it.relativePath.endsWith("/$fileName") }
            .utf8
            .decodeToString()
}
