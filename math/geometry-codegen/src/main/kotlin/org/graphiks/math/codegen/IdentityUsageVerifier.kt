package org.graphiks.math.codegen

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal data class IdentityViolation(
    val path: String,
    val line: Int,
    val expression: String,
)

internal object IdentityUsageVerifier {
    private val systemIdentityHashCodeCall = Regex("""\bSystem\s*\.\s*identityHashCode\s*\(""")
    private val synchronizedCall = Regex("""\bsynchronized\s*\(""")
    private val identityHashMapReference = Regex("""\bIdentityHashMap\b""")
    private val justifiedAllowance = Regex("""(?:^|\s)identity-ok:\s*(\S.*)$""")

    fun verify(repoRoot: Path): List<IdentityViolation> {
        val normalizedRoot = repoRoot.toAbsolutePath().normalize()
        val mathRoot = normalizedRoot.resolve("math")
        if (!Files.isDirectory(mathRoot)) return emptyList()

        val sourceFiles = Files.walk(mathRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.isMathKotlinSource(mathRoot) }
                .sorted()
                .toList()
        }
        return sourceFiles.flatMap { path -> scan(normalizedRoot, path) }
    }

    private fun scan(repoRoot: Path, sourcePath: Path): List<IdentityViolation> {
        val relativePath = repoRoot.relativize(sourcePath).toString().replace(File.separatorChar, '/')
        return KotlinLexer(Files.readString(sourcePath)).lines()
            .mapIndexedNotNull { index, line ->
                if (!line.code.hasForbiddenIdentityUsage() || line.hasJustifiedAllowance()) {
                    null
                } else {
                    IdentityViolation(
                        path = relativePath,
                        line = index + 1,
                        expression = line.source.trim(),
                    )
                }
            }
    }

    private fun Path.isMathKotlinSource(mathRoot: Path): Boolean {
        val fileName = fileName.toString()
        if (!fileName.endsWith(".kt") && !fileName.endsWith(".kts")) return false
        val relativePath = mathRoot.relativize(this)
        return (0 until relativePath.nameCount - 1).any { index ->
            relativePath.getName(index).toString() == "src"
        }
    }

    private fun String.hasForbiddenIdentityUsage(): Boolean =
        "===" in this ||
            "!==" in this ||
            systemIdentityHashCodeCall.containsMatchIn(this) ||
            synchronizedCall.containsMatchIn(this) ||
            identityHashMapReference.containsMatchIn(this)

    private fun LexedLine.hasJustifiedAllowance(): Boolean =
        lineComment?.let(justifiedAllowance::containsMatchIn) == true

    private data class LexedLine(
        val source: String,
        val code: String,
        val lineComment: String?,
    )

    private class KotlinLexer(source: String) {
        private val sourceLines = source.split('\n')
        private var mode = Mode.CODE
        private var blockCommentDepth = 0

        fun lines(): List<LexedLine> = sourceLines.map(::lexLine)

        private fun lexLine(sourceLine: String): LexedLine {
            val code = CharArray(sourceLine.length) { ' ' }
            var lineComment: String? = null
            var index = 0
            while (index < sourceLine.length) {
                when (mode) {
                    Mode.CODE -> when {
                        sourceLine.startsWith("//", index) -> {
                            lineComment = sourceLine.substring(index + 2)
                            index = sourceLine.length
                        }

                        sourceLine.startsWith("/*", index) -> {
                            mode = Mode.BLOCK_COMMENT
                            blockCommentDepth = 1
                            index += 2
                        }

                        sourceLine.startsWith("\"\"\"", index) -> {
                            mode = Mode.RAW_STRING
                            index += 3
                        }

                        sourceLine[index] == '"' -> {
                            mode = Mode.STRING
                            index++
                        }

                        sourceLine[index] == '\'' -> {
                            mode = Mode.CHAR
                            index++
                        }

                        else -> {
                            code[index] = sourceLine[index]
                            index++
                        }
                    }

                    Mode.BLOCK_COMMENT -> when {
                        sourceLine.startsWith("/*", index) -> {
                            blockCommentDepth++
                            index += 2
                        }

                        sourceLine.startsWith("*/", index) -> {
                            blockCommentDepth--
                            index += 2
                            if (blockCommentDepth == 0) mode = Mode.CODE
                        }

                        else -> index++
                    }

                    Mode.STRING -> when {
                        sourceLine[index] == '\\' -> index = (index + 2).coerceAtMost(sourceLine.length)
                        sourceLine[index] == '"' -> {
                            mode = Mode.CODE
                            index++
                        }

                        else -> index++
                    }

                    Mode.RAW_STRING -> if (sourceLine.startsWith("\"\"\"", index)) {
                        mode = Mode.CODE
                        index += 3
                    } else {
                        index++
                    }

                    Mode.CHAR -> when {
                        sourceLine[index] == '\\' -> index = (index + 2).coerceAtMost(sourceLine.length)
                        sourceLine[index] == '\'' -> {
                            mode = Mode.CODE
                            index++
                        }

                        else -> index++
                    }
                }
            }
            if (mode == Mode.STRING || mode == Mode.CHAR) mode = Mode.CODE
            return LexedLine(source = sourceLine, code = code.concatToString(), lineComment = lineComment)
        }

        private enum class Mode {
            CODE,
            BLOCK_COMMENT,
            STRING,
            RAW_STRING,
            CHAR,
        }
    }
}
