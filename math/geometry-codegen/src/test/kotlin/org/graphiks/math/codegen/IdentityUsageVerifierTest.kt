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

    @Test
    fun `reports direct and aliased imported identity calls`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/ImportedCalls.kt",
            """
                import java.lang.System.identityHashCode
                import java.lang.System.identityHashCode as objectId
                import kotlin.synchronized as withLock

                fun importedCalls(value: Any, lock: Any) {
                    val direct = identityHashCode(value)
                    val aliased = objectId(value)
                    withLock(lock) {}
                }
            """.trimIndent(),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(6, 7, 8), violations.map { it.line })
        assertEquals(
            listOf(
                "val direct = identityHashCode(value)",
                "val aliased = objectId(value)",
                "withLock(lock) {}",
            ),
            violations.map { it.expression },
        )
    }

    @Test
    fun `reports aliases for identity owner and map type`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/ImportedOwners.kt",
            """
                import java.lang.System as JvmSystem
                import java.util.IdentityHashMap as ReferenceMap

                fun importedOwners(value: Any) {
                    val hash = JvmSystem.identityHashCode(value)
                    val map = ReferenceMap<Any, Any>()
                }
            """.trimIndent(),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(5, 6), violations.map { it.line })
        assertEquals(
            listOf(
                "val hash = JvmSystem.identityHashCode(value)",
                "val map = ReferenceMap<Any, Any>()",
            ),
            violations.map { it.expression },
        )
    }

    @Test
    fun `does not treat a backticked import identifier as an import directive`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/BacktickedImport.kt",
            "fun `import`(value: Any): Int = System.identityHashCode(value)",
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(1), violations.map { it.line })
        assertEquals(
            "fun `import`(value: Any): Int = System.identityHashCode(value)",
            violations.single().expression,
        )
    }

    @Test
    fun `reports calls split between symbol and opening parenthesis`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/MultilineCalls.kt",
            """
                fun multilineCalls(value: Any, lock: Any) {
                    val hash = System.identityHashCode
                        (
                            value,
                        )
                    kotlin.synchronized
                        (
                            lock,
                        ) {}
                }
            """.trimIndent(),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(2, 6), violations.map { it.line })
        assertEquals(
            listOf(
                "val hash = System.identityHashCode",
                "kotlin.synchronized",
            ),
            violations.map { it.expression },
        )
    }

    @Test
    fun `reports forbidden operations in regular and raw string templates`() = withRepository { repoRoot ->
        writeKotlin(
            repoRoot,
            "math/sample/src/main/kotlin/Templates.kt",
            listOf(
                "val regular = \"same=${'$'}{run { left === right }}\"",
                "val raw = \"\"\"hash=${'$'}{run { System.identityHashCode(value) }}\"\"\"",
            ).joinToString("\n"),
        )

        val violations = IdentityUsageVerifier.verify(repoRoot)

        assertEquals(listOf(1, 2), violations.map { it.line })
        assertEquals(
            listOf(
                "val regular = \"same=${'$'}{run { left === right }}\"",
                "val raw = \"\"\"hash=${'$'}{run { System.identityHashCode(value) }}\"\"\"",
            ),
            violations.map { it.expression },
        )
    }

    @Test
    fun `does not follow Kotlin source symlinks outside repository`() = withRepository { repoRoot ->
        val externalRoot = createTempDirectory("identity-verifier-external")
        try {
            val externalSource = externalRoot.resolve("External.kt")
            Files.writeString(externalSource, "val escaped = left === right")
            val symlink = repoRoot.resolve("math/sample/src/main/kotlin/Escape.kt")
            Files.createDirectories(symlink.parent)
            try {
                Files.createSymbolicLink(symlink, externalSource)
            } catch (failure: Exception) {
                throw AssertionError("test environment cannot create the required source symlink", failure)
            }
            assertTrue(Files.isSymbolicLink(symlink), "test must exercise an actual symbolic link")

            assertEquals(emptyList(), IdentityUsageVerifier.verify(repoRoot))
        } finally {
            deleteTree(externalRoot)
        }
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
            deleteTree(repoRoot)
        }
    }

    private fun deleteTree(root: Path) {
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
