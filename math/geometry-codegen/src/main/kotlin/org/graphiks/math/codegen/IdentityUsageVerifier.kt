package org.graphiks.math.codegen

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque

internal data class IdentityViolation(
    val path: String,
    val line: Int,
    val expression: String,
)

internal object IdentityUsageVerifier {
    private val justifiedAllowance = Regex("""(?:^|\s)identity-ok:\s*(\S.*)$""")

    fun verify(repoRoot: Path): List<IdentityViolation> {
        val normalizedRoot = repoRoot.toAbsolutePath().normalize()
        val mathRoot = normalizedRoot.resolve("math")
        if (!Files.isDirectory(mathRoot, LinkOption.NOFOLLOW_LINKS)) return emptyList()

        val sourceFiles = mutableListOf<Path>()
        val pendingDirectories = ArrayDeque<Path>().apply { add(mathRoot) }
        while (pendingDirectories.isNotEmpty()) {
            val directory = pendingDirectories.removeFirst()
            val beneathSourceRoot = directory.isBeneathSourceRoot(mathRoot)
            Files.newDirectoryStream(directory).use { entries ->
                entries.forEach { entry ->
                    if (!beneathSourceRoot && entry.fileName.toString() == "build") return@forEach
                    val attributes = Files.readAttributes(
                        entry,
                        BasicFileAttributes::class.java,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    when {
                        attributes.isDirectory -> pendingDirectories.add(entry)
                        attributes.isRegularFile &&
                            entry.isMathKotlinSource(mathRoot) -> sourceFiles.add(entry)
                    }
                }
            }
        }
        return sourceFiles.sorted().flatMap { path -> scan(normalizedRoot, path) }
    }

    private fun scan(repoRoot: Path, sourcePath: Path): List<IdentityViolation> {
        val relativePath = repoRoot.relativize(sourcePath).toString().replace(File.separatorChar, '/')
        val source = KotlinLexer(Files.readString(sourcePath)).lex()
        return IdentityDetector(source.tokens).forbiddenLines()
            .mapNotNull { lineNumber ->
                val line = source.lines[lineNumber - 1]
                if (line.hasJustifiedAllowance()) {
                    null
                } else {
                    IdentityViolation(relativePath, lineNumber, line.source.trim())
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

    private fun Path.isBeneathSourceRoot(mathRoot: Path): Boolean {
        val relativePath = mathRoot.relativize(this)
        return (0 until relativePath.nameCount).any { index ->
            relativePath.getName(index).toString() == "src"
        }
    }

    private fun LexedLine.hasJustifiedAllowance(): Boolean =
        lineComment?.let(justifiedAllowance::containsMatchIn) == true

    private class IdentityDetector(private val tokens: List<Token>) {
        private val importedIdentityCalls = mutableSetOf<String>()
        private val synchronizedCalls = mutableSetOf("synchronized")
        private val identityMapNames = mutableSetOf("IdentityHashMap")
        private val systemNames = mutableSetOf("System")
        private val importTokenIndices = mutableSetOf<Int>()

        fun forbiddenLines(): Set<Int> {
            collectImports()
            val codeTokens = tokens.withIndex().filter { (index, token) ->
                index !in importTokenIndices && token.kind != TokenKind.NEWLINE
            }
            return buildSet {
                codeTokens.forEachIndexed { index, indexedToken ->
                    val token = indexedToken.value
                    when {
                        token.text == "===" || token.text == "!==" -> add(token.line)
                        token.kind != TokenKind.IDENTIFIER -> Unit
                        token.text in identityMapNames -> add(token.line)
                        token.text in importedIdentityCalls &&
                            (codeTokens.isCallAt(index) || codeTokens.isCallableReferenceAt(index)) ->
                            add(token.line)
                        token.text in synchronizedCalls &&
                            (codeTokens.isCallAt(index) || codeTokens.isCallableReferenceAt(index)) ->
                            add(token.line)
                        token.text == "identityHashCode" &&
                            (
                                codeTokens.isCallAt(index) &&
                                    codeTokens.isQualifiedBySystemAt(index, systemNames) ||
                                    codeTokens.isCallableReferenceAt(index) &&
                                    codeTokens.isCallableReferenceQualifiedBySystemAt(index, systemNames)
                            ) -> add(token.line)
                    }
                }
            }.toSortedSet()
        }

        private fun collectImports() {
            var index = 0
            while (index < tokens.size) {
                if (!tokens[index].isKeyword("import")) {
                    index++
                    continue
                }

                val start = index
                var end = start + 1
                while (end < tokens.size &&
                    tokens[end].kind != TokenKind.NEWLINE &&
                    tokens[end].text != ";"
                ) {
                    end++
                }
                (start until end).forEach(importTokenIndices::add)
                if (end < tokens.size && tokens[end].text == ";") importTokenIndices += end
                collectImportAlias(tokens.subList(start + 1, end))
                index = end + 1
            }
        }

        private fun collectImportAlias(importTokens: List<Token>) {
            val asIndex = importTokens.indexOfFirst { it.isKeyword("as") }
            val targetTokens = if (asIndex < 0) importTokens else importTokens.subList(0, asIndex)
            val target = targetTokens.joinToString(separator = "") { it.text }
            val alias = if (asIndex >= 0) {
                importTokens.getOrNull(asIndex + 1)?.takeIf { it.kind == TokenKind.IDENTIFIER }?.text
            } else {
                targetTokens.lastOrNull { it.kind == TokenKind.IDENTIFIER }?.text
            } ?: return

            when (target) {
                "java.lang.System.identityHashCode" -> importedIdentityCalls += alias
                "kotlin.synchronized" -> synchronizedCalls += alias
                "java.lang.System" -> systemNames += alias
                "java.util.IdentityHashMap" -> identityMapNames += alias
            }
        }

        private fun List<IndexedValue<Token>>.isCallAt(index: Int): Boolean {
            var nextIndex = index + 1
            if (getOrNull(nextIndex)?.value?.text == "<") {
                var depth = 0
                while (nextIndex < size) {
                    when (get(nextIndex).value.text) {
                        "<" -> depth++
                        ">" -> {
                            depth--
                            if (depth == 0) {
                                nextIndex++
                                break
                            }
                        }
                    }
                    nextIndex++
                }
                if (depth != 0) return false
            }
            return getOrNull(nextIndex)?.value?.text == "("
        }

        private fun List<IndexedValue<Token>>.isCallableReferenceAt(index: Int): Boolean =
            getOrNull(index - 1)?.value?.text == ":" &&
                getOrNull(index - 2)?.value?.text == ":"

        private fun List<IndexedValue<Token>>.isQualifiedBySystemAt(
            index: Int,
            systemNames: Set<String>,
        ): Boolean =
            getOrNull(index - 1)?.value?.text == "." &&
                getOrNull(index - 2)?.value?.text?.let(systemNames::contains) == true

        private fun List<IndexedValue<Token>>.isCallableReferenceQualifiedBySystemAt(
            index: Int,
            systemNames: Set<String>,
        ): Boolean =
            getOrNull(index - 1)?.value?.text == ":" &&
                getOrNull(index - 2)?.value?.text == ":" &&
                getOrNull(index - 3)?.value?.text?.let(systemNames::contains) == true
    }

    private data class LexedSource(
        val lines: List<LexedLine>,
        val tokens: List<Token>,
    )

    private data class LexedLine(
        val source: String,
        val lineComment: String?,
    )

    private class KotlinLexer(source: String) {
        private val sourceLines = source.split('\n')
        private val tokens = mutableListOf<Token>()
        private val templateFrames = ArrayDeque<TemplateFrame>()
        private var mode = Mode.CODE
        private var blockCommentDepth = 0

        fun lex(): LexedSource {
            val lines = sourceLines.mapIndexed { index, sourceLine ->
                val lineNumber = index + 1
                val line = lexLine(sourceLine, lineNumber)
                tokens += Token("\n", lineNumber, TokenKind.NEWLINE)
                line
            }
            return LexedSource(lines, tokens)
        }

        private fun lexLine(sourceLine: String, lineNumber: Int): LexedLine {
            var lineComment: String? = null
            var index = 0
            while (index < sourceLine.length) {
                when (mode) {
                    Mode.CODE -> when {
                        templateFrames.isNotEmpty() && sourceLine[index] == '}' -> {
                            val frame = templateFrames.peekLast()
                            frame.braceDepth--
                            index++
                            if (frame.braceDepth == 0) {
                                templateFrames.removeLast()
                                mode = frame.returnMode
                            } else {
                                tokens += Token("}", lineNumber, TokenKind.SYMBOL)
                            }
                        }

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

                        sourceLine.startsWith("===", index) || sourceLine.startsWith("!==", index) -> {
                            tokens += Token(sourceLine.substring(index, index + 3), lineNumber, TokenKind.SYMBOL)
                            index += 3
                        }

                        sourceLine[index] == '{' -> {
                            if (templateFrames.isNotEmpty()) templateFrames.peekLast().braceDepth++
                            tokens += Token("{", lineNumber, TokenKind.SYMBOL)
                            index++
                        }

                        sourceLine[index] == '`' -> index = lexBacktickedIdentifier(sourceLine, index, lineNumber)
                        sourceLine[index].isIdentifierStart() ->
                            index = lexIdentifier(sourceLine, index, lineNumber)

                        sourceLine[index].isWhitespace() -> index++
                        else -> {
                            tokens += Token(sourceLine[index].toString(), lineNumber, TokenKind.SYMBOL)
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

                        sourceLine.isTemplateStart(index) -> {
                            templateFrames.addLast(TemplateFrame(Mode.STRING))
                            mode = Mode.CODE
                            index += 2
                        }

                        else -> index++
                    }

                    Mode.RAW_STRING -> when {
                        sourceLine.startsWith("\"\"\"", index) -> {
                            mode = Mode.CODE
                            index += 3
                        }

                        sourceLine.isTemplateStart(index) -> {
                            templateFrames.addLast(TemplateFrame(Mode.RAW_STRING))
                            mode = Mode.CODE
                            index += 2
                        }

                        else -> index++
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
            return LexedLine(source = sourceLine, lineComment = lineComment)
        }

        private fun lexIdentifier(sourceLine: String, start: Int, lineNumber: Int): Int {
            var end = start + 1
            while (end < sourceLine.length && sourceLine[end].isIdentifierPart()) end++
            tokens += Token(sourceLine.substring(start, end), lineNumber, TokenKind.IDENTIFIER)
            return end
        }

        private fun lexBacktickedIdentifier(sourceLine: String, start: Int, lineNumber: Int): Int {
            val closing = sourceLine.indexOf('`', start + 1)
            if (closing < 0) return sourceLine.length
            tokens += Token(
                sourceLine.substring(start + 1, closing),
                lineNumber,
                TokenKind.IDENTIFIER,
                isBackticked = true,
            )
            return closing + 1
        }

        private enum class Mode {
            CODE,
            BLOCK_COMMENT,
            STRING,
            RAW_STRING,
            CHAR,
        }

        private data class TemplateFrame(
            val returnMode: Mode,
            var braceDepth: Int = 1,
        )
    }

    private data class Token(
        val text: String,
        val line: Int,
        val kind: TokenKind,
        val isBackticked: Boolean = false,
    ) {
        fun isKeyword(value: String): Boolean =
            kind == TokenKind.IDENTIFIER && !isBackticked && text == value
    }

    private enum class TokenKind {
        IDENTIFIER,
        SYMBOL,
        NEWLINE,
    }

    private fun String.isTemplateStart(index: Int): Boolean =
        this[index] == '$' && getOrNull(index + 1) == '{'

    private fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()

    private fun Char.isIdentifierPart(): Boolean = this == '_' || isLetterOrDigit()
}
