package org.graphiks.math.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.jetbrains.kotlin.cli.common.ExitCode

class SemanticCompilationTest {
    @Test
    fun `allowed point vector and matrix operations compile`() {
        val source = fixture("positive/AllowedOperations.kt")

        val result = compile(source)

        assertEquals(ExitCode.OK, result.exitCode, result.diagnostics)
    }

    @Test
    fun `point plus point is rejected at plus`() {
        assertRejected("PointPlusPoint.kt", "plus", "point1 + point2", "argument type mismatch")
    }

    @Test
    fun `point scaling is rejected at times`() {
        assertRejected("PointScale.kt", "times", "point * 2f", "unresolved reference 'times'")
    }

    @Test
    fun `point normalization is rejected at normalized`() {
        assertRejected(
            "PointNormalize.kt",
            "normalized",
            "point.normalized()",
            "unresolved reference 'normalized'",
        )
    }

    @Test
    fun `point transform cannot be assigned as vector`() {
        assertRejected(
            "PointAssignedAsVector.kt",
            "transform",
            "matrix.transform(point)",
            "initializer type mismatch",
        )
    }

    @Test
    fun `polygon correspondence rejects vector arrays`() {
        assertRejected(
            "PolyToPolyVectors.kt",
            "polyToPoly",
            "Matrix3x3F32.polyToPoly(source, destination)",
            "argument type mismatch",
        )
    }

    @Test
    fun `vector component product is rejected at times`() {
        assertRejected(
            "VectorTimesVector.kt",
            "times",
            "left * right",
            "argument type mismatch",
        )
    }

    private fun assertRejected(
        fileName: String,
        symbol: String,
        offendingExpression: String,
        expectedFailure: String,
    ) {
        val source = fixture("negative/$fileName")

        val result = compile(source)

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.diagnostics)
        assertContains(result.diagnostics, fileName)
        assertContains(result.diagnostics, symbol, ignoreCase = true)
        assertContains(result.diagnostics, offendingExpression)
        assertContains(result.diagnostics, expectedFailure)
    }

    private fun compile(source: Path): CompilationResult {
        val destination = createTempDirectory("semantic-fixture-classes")
        return try {
            compileFixture(
                source = source,
                classpath = System.getProperty("java.class.path"),
                destination = destination,
            )
        } finally {
            Files.walk(destination).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun fixture(relativePath: String): Path = Path.of(
        requireNotNull(javaClass.getResource("/semantic-fixtures/$relativePath")) {
            "missing semantic fixture: $relativePath"
        }.toURI(),
    )
}
