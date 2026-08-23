package org.graphiks.math.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityUsageVerifierTest {
    @Test
    fun `reports every forbidden identity operation`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/Forbidden.kt",
            """
                fun forbidden(a: Any, b: Any) {
                    val same = a === b
                    val different = a !== b
                    val hash = System.identityHashCode(a)
                    synchronized(a) {}
                    kotlin.synchronized(a) {}
                    val map = IdentityHashMap<Any, Any>()
                }
            """.trimIndent(),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(2, 3, 4, 5, 6, 7), violations.map { it.line })
        assertEquals(
            listOf(
                "val same = a === b",
                "val different = a !== b",
                "val hash = System.identityHashCode(a)",
                "synchronized(a) {}",
                "kotlin.synchronized(a) {}",
                "val map = IdentityHashMap<Any, Any>()",
            ),
            violations.map { it.expression },
        )
        assertTrue(violations.all { it.path == "math/sample/src/main/kotlin/Forbidden.kt" })
    }

    @Test
    fun `ignores forbidden tokens in comments strings raw strings and chars`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/Literals.kt",
            listOf(
                "/* a === b",
                "   /* System.identityHashCode(a) */",
                "   synchronized(a) */",
                "val text = \"a !== b; kotlin.synchronized(a); IdentityHashMap()\"",
                "val escaped = \"quote: \\\" then a === b\"",
                "val raw = \"\"\"a !== b",
                "System.identityHashCode(a)",
                "\"\"\"",
                "val quote = '\\''",
                "// synchronized(a) and IdentityHashMap",
            ).joinToString("\n"),
        )

        assertEquals(emptyList(), IdentityUsageVerifier.verify(repoRoot))
    }

    @Test
    fun `same-line identity allowance requires a non-empty reason`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/test/kotlin/Aliasing.kt",
            """
                val allowed = destination === source // identity-ok: mutable array aliasing
                val forbidden = destination !== source // identity-ok:
            """.trimIndent(),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(1, violations.size)
        assertEquals(2, violations.single().line)
        assertEquals("val forbidden = destination !== source // identity-ok:", violations.single().expression)
    }

    @Test
    fun `scans only Kotlin sources beneath math src directories`() = withRepository { repoRoot ->
        writeKotlin(repoRoot, "math/one/src/main/kotlin/Source.kt", "val bad = a === b")
        writeKotlin(repoRoot, "math/two/src/check.kts", "val bad = a !== b")
        writeKotlin(repoRoot, "math/one/tools/OutsideSrc.kt", "val ignored = a === b")
        writeKotlin(repoRoot, "other/src/main/kotlin/OutsideMath.kt", "val ignored = a === b")
        writeKotlin(repoRoot, "math/one/src/main/kotlin/NotKotlin.java", "val ignored = a === b")

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(
            listOf(
                "math/one/src/main/kotlin/Source.kt",
                "math/two/src/check.kts",
            ),
            violations.map { it.path },
        )
    }

    private fun writeKotlin(repoRoot: Path, relativePath: String, source: String) {
        val path = repoRoot.resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, source)
    }

    private fun withRepository(block: (Path) -> Unit) {
        val repoRoot = createTempDirectory("identity-verifier-test")
        try {
            block(repoRoot)
        } finally {
            Files.walk(repoRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
