package org.graphiks.kanvas.text.shaping

import java.security.MessageDigest

public const val TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE: String =
    "text.shaping.paragraph-bidi-required"
public const val TEXT_UNICODE_BIDI_CONTROL_UNBALANCED_DIAGNOSTIC_CODE: String =
    "text.unicode.bidi-control-unbalanced"
public const val TEXT_SHAPING_BIDI_CONTEXT_REQUIRED_DIAGNOSTIC_CODE: String =
    "text.shaping.bidi-context-required"
public const val TEXT_SHAPING_BIDI_DATA_MALFORMED_DIAGNOSTIC_CODE: String =
    "text.shaping.bidi-data-malformed"

public data class ResolvedBidiRun(
    public val logicalUtf16Range: IntRange,
    public val clusterRange: IntRange,
    public val embeddingLevel: Int,
    public val direction: String,
    public val paragraphDirection: String,
    public val resolvedBidiClasses: List<String>,
    public val sourceControls: List<BidiControl>,
)

public data class BidiControl(
    public val kind: String,
    public val utf16Range: IntRange,
    public val depthBefore: Int,
    public val depthAfter: Int,
    public val balanced: Boolean,
)

public data class BidiTraceEvent(
    public val rule: String,
    public val utf16Range: IntRange,
    public val beforeClass: String,
    public val afterClass: String,
)

public data class BidiResolution(
    public val unicodeVersion: String,
    public val sourceTextHash: String,
    public val paragraphDirection: String,
    public val clusters: List<GraphemeCluster>,
    public val runs: List<ResolvedBidiRun>,
    public val sourceControls: List<BidiControl>,
    public val trace: List<BidiTraceEvent>,
    public val diagnostics: List<ShapingDiagnostic>,
)

public data class BidiFixtureDumpInput(
    public val fixtureName: String,
    public val sourceText: String,
    public val result: BidiResolution,
)

public data class BidiRunsDump(
    public val unicodeVersion: String,
    public val inputs: List<BidiFixtureDumpInput>,
) {
    public fun toCanonicalJson(): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": 1,\n")
        append("  \"dumpId\": \"bidi-runs\",\n")
        append("  \"ownerTickets\": [\"KFONT-M5-003\"],\n")
        appendJsonField("unicodeVersion", unicodeVersion, comma = true)
        appendJsonField("sourceTextHashAlgorithm", BIDI_SOURCE_TEXT_HASH_ALGORITHM, comma = true)
        append("  \"inputs\": [\n")
        append(inputs.joinToString(",\n") { input -> input.toCanonicalJson().prependIndent("    ") })
        append("\n  ],\n")
        append("  \"nonClaims\": [\n")
        append(BidiNonClaims.joinToString(",\n") { nonClaim -> "    ${jsonString(nonClaim)}" })
        append("\n  ]\n")
        append("}\n")
    }
}

public interface DetailedBidiResolver : BidiResolver {
    public fun resolveDetailed(
        request: ShapingRequest,
        requireParagraphOrdering: Boolean = true,
    ): BidiResolution
}

public class DefaultBidiResolver(
    private val unicodeDataSet: UnicodeDataSet,
    private val expectedUnicodeVersion: String = PinnedUnicodeDataGenerator.PinnedUnicodeVersion,
) : DetailedBidiResolver {
    override fun resolve(request: ShapingRequest): List<BidiRun> =
        resolveDetailed(request).runs.map { run ->
            BidiRun(
                textRange = run.logicalUtf16Range,
                level = run.embeddingLevel,
                isRightToLeft = run.direction == BidiDirectionRtl,
            )
        }

    override fun resolveDetailed(
        request: ShapingRequest,
        requireParagraphOrdering: Boolean,
    ): BidiResolution {
        val scopedRange = request.textRange.coerceFor(request.text)
        val text = if (scopedRange.isEmpty()) "" else request.text.substring(scopedRange)
        val offset = scopedRange.first.coerceAtLeast(0)
        val sourceTextHash = text.sourceTextHash()
        if (unicodeDataSet.version.value != expectedUnicodeVersion) {
            return BidiResolution(
                unicodeVersion = unicodeDataSet.version.value,
                sourceTextHash = sourceTextHash,
                paragraphDirection = paragraphDirectionName(request.paragraphDirection, emptyList()),
                clusters = emptyList(),
                runs = emptyList(),
                sourceControls = emptyList(),
                trace = emptyList(),
                diagnostics = listOf(
                    UnicodeDataVersionMismatchDiagnostic(
                        expectedUnicodeVersion = expectedUnicodeVersion,
                        actualUnicodeVersion = unicodeDataSet.version.value,
                        subject = "bidi-resolution",
                        textRange = request.textRange.takeUnless { it.isEmpty() },
                    ).toShapingDiagnostic(),
                ),
            )
        }

        val clusterer = GraphemeClusterer(unicodeDataSet)
        val segmentation = clusterer.segment(text)
        val clusters = segmentation.clusters.map { cluster ->
            cluster.copy(utf16Range = cluster.utf16Range.shift(offset))
        }
        val diagnostics = segmentation.diagnostics
            .map { diagnostic -> diagnostic.shiftTextRange(offset) }
            .toMutableList()
        if (diagnostics.isNotEmpty()) {
            return BidiResolution(
                unicodeVersion = unicodeDataSet.version.value,
                sourceTextHash = sourceTextHash,
                paragraphDirection = paragraphDirectionName(request.paragraphDirection, emptyList()),
                clusters = clusters,
                runs = emptyList(),
                sourceControls = emptyList(),
                trace = emptyList(),
                diagnostics = diagnostics.distinctBy { diagnostic -> diagnostic.code to diagnostic.textRange },
            )
        }
        val scalars = decodeBidiScalars(text, offset)
        val controls = sourceControls(scalars)
        val unbalancedControls = controls.filterNot { control -> control.balanced }
        diagnostics += unbalancedControls.map { control ->
            ShapingDiagnostic(
                code = TEXT_UNICODE_BIDI_CONTROL_UNBALANCED_DIAGNOSTIC_CODE,
                message = "Bidi control ${control.kind} is unbalanced at UTF-16 range ${control.utf16Range.toRangeLabel()}.",
                textRange = control.utf16Range,
            )
        }
        if (scalars.isEmpty()) {
            return BidiResolution(
                unicodeVersion = unicodeDataSet.version.value,
                sourceTextHash = sourceTextHash,
                paragraphDirection = paragraphDirectionName(request.paragraphDirection, emptyList()),
                clusters = clusters,
                runs = emptyList(),
                sourceControls = controls,
                trace = emptyList(),
                diagnostics = diagnostics,
            )
        }

        val paragraphDirection = paragraphDirectionName(request.paragraphDirection, scalars)
        val explicit = applyExplicitControls(scalars)
        val resolved = resolveWeakAndNeutralClasses(explicit, paragraphDirection)
        val runs = buildRuns(resolved, clusters, paragraphDirection)
        if (!requireParagraphOrdering && runs.any { run -> run.direction != baseDirectionCode(paragraphDirection) }) {
            diagnostics += ShapingDiagnostic(
                code = TEXT_SHAPING_PARAGRAPH_BIDI_REQUIRED_DIAGNOSTIC_CODE,
                message = "Paragraph-level visual bidi ordering is required for mixed-direction text; M8 owns line ordering.",
                textRange = request.textRange,
            )
        }

        return BidiResolution(
            unicodeVersion = unicodeDataSet.version.value,
            sourceTextHash = sourceTextHash,
            paragraphDirection = paragraphDirection,
            clusters = clusters,
            runs = runs,
            sourceControls = controls,
            trace = resolved.flatMap { scalar -> scalar.trace },
            diagnostics = diagnostics.distinctBy { diagnostic -> diagnostic.code to diagnostic.textRange },
        )
    }

    private fun decodeBidiScalars(text: String, offset: Int): List<BidiScalar> {
        val scalars = mutableListOf<BidiScalar>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val codePoint: Int
            val localRange: IntRange
            if (char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                codePoint = Character.toCodePoint(char, text[index + 1])
                localRange = index..index + 1
                index += 2
            } else {
                codePoint = char.code
                localRange = index..index
                index += 1
            }
            val textRange = localRange.shift(offset)
            scalars += BidiScalar(
                codePoint = codePoint,
                utf16Range = textRange,
                clusterIndex = -1,
                originalClass = bidiClassFor(codePoint),
                resolvedClass = bidiClassFor(codePoint),
                explicitDirection = null,
                explicitRule = null,
                trace = emptyList(),
            )
        }
        return scalars
    }

    private fun bidiClassFor(codePoint: Int): String =
        when (codePoint) {
            in 0x0030..0x0039 -> "EN"
            in 0x0660..0x0669, in 0x06F0..0x06F9 -> "AN"
            in 0x0590..0x05FF -> "R"
            in 0x0600..0x06FF, in 0x0750..0x077F, in 0x08A0..0x08FF -> "AL"
            0x0020 -> "WS"
            0x0021, 0x002C, 0x002E, 0x003A, 0x003B, 0x003F, 0x2603 -> "ON"
            0x202A -> "LRE"
            0x202B -> "RLE"
            0x202C -> "PDF"
            0x202D -> "LRO"
            0x202E -> "RLO"
            0x2066 -> "LRI"
            0x2067 -> "RLI"
            0x2068 -> "FSI"
            0x2069 -> "PDI"
            else -> unicodeDataSet.bidiClass.valueAt(codePoint)
        }

    private fun sourceControls(scalars: List<BidiScalar>): List<BidiControl> {
        val stack = mutableListOf<OpenBidiControl>()
        return scalars.mapNotNull { scalar ->
            val kind = controlKind(scalar.originalClass) ?: return@mapNotNull null
            val before = stack.size
            var balanced = true
            when (kind) {
                "LRE", "RLE", "LRO", "RLO" -> stack += OpenBidiControl(kind, expectedCloser = "PDF")
                "LRI", "RLI", "FSI" -> stack += OpenBidiControl(kind, expectedCloser = "PDI")
                "PDF", "PDI" -> {
                    val lastOpen = stack.lastOrNull()
                    if (lastOpen?.expectedCloser == kind) {
                        stack.removeAt(stack.lastIndex)
                    } else {
                        balanced = false
                    }
                }
            }
            BidiControl(
                kind = kind,
                utf16Range = scalar.utf16Range,
                depthBefore = before,
                depthAfter = stack.size,
                balanced = balanced,
            )
        }.markOpenControls(stack.size)
    }

    private fun applyExplicitControls(scalars: List<BidiScalar>): List<BidiScalar> {
        val stack = mutableListOf<ExplicitBidiState>()
        return scalars.map { scalar ->
            when (scalar.originalClass) {
                "RLE" -> {
                    stack += ExplicitBidiState(BidiDirectionRtl, "X2/RLE-embedding")
                    scalar
                }
                "LRE" -> {
                    stack += ExplicitBidiState(BidiDirectionLtr, "X3/LRE-embedding")
                    scalar
                }
                "LRO" -> {
                    stack += ExplicitBidiState(BidiDirectionLtr, "X4/LRO-override")
                    scalar
                }
                "RLO" -> {
                    stack += ExplicitBidiState(BidiDirectionRtl, "X5/RLO-override")
                    scalar
                }
                "PDF" -> {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    scalar
                }
                else -> {
                    val state = stack.lastOrNull()
                    if (state == null || controlKind(scalar.originalClass) != null) {
                        scalar
                    } else {
                        scalar.copy(
                            explicitDirection = state.direction,
                            explicitRule = state.rule,
                        )
                    }
                }
            }
        }
    }

    private fun resolveWeakAndNeutralClasses(
        scalars: List<BidiScalar>,
        paragraphDirection: String,
    ): List<BidiScalar> {
        val withStrong = scalars.mapIndexed { index, scalar ->
            if (scalar.explicitDirection != null) {
                scalar.copy(
                    resolvedClass = scalar.explicitDirection,
                    trace = scalar.trace + BidiTraceEvent(
                        scalar.explicitRule ?: "X9/explicit-direction",
                        scalar.utf16Range,
                        scalar.originalClass,
                        scalar.explicitDirection,
                    ),
                )
            } else {
                when (scalar.originalClass) {
                "AL" -> scalar.copy(
                    resolvedClass = "R",
                    trace = scalar.trace + BidiTraceEvent("W3/AL-to-R", scalar.utf16Range, "AL", "R"),
                )
                "NSM" -> {
                    val inherited = scalars.take(index).lastOrNull { it.originalClass in StrongBidiClasses }?.originalClass ?: baseDirectionCode(paragraphDirection)
                    scalar.copy(
                        resolvedClass = inherited,
                        trace = scalar.trace + BidiTraceEvent("W1/NSM-inherits", scalar.utf16Range, "NSM", inherited),
                    )
                }
                "AN" -> scalar.copy(
                    resolvedClass = "R",
                    trace = scalar.trace + BidiTraceEvent("W2/AN-inside-rtl-run", scalar.utf16Range, "AN", "R"),
                )
                "EN" -> scalar.copy(
                    resolvedClass = if (nearestStrongBefore(scalars, index) == "R") "R" else "L",
                    trace = scalar.trace + BidiTraceEvent(
                        "W7/EN-from-nearest-strong",
                        scalar.utf16Range,
                        "EN",
                        if (nearestStrongBefore(scalars, index) == "R") "R" else "L",
                    ),
                )
                else -> scalar
                }
            }
        }
        return withStrong.mapIndexed { index, scalar ->
            if (scalar.resolvedClass !in NeutralBidiClasses) {
                scalar
            } else {
                val previous = withStrong.take(index).lastOrNull { it.resolvedClass in setOf("L", "R") }?.resolvedClass
                val next = withStrong.drop(index + 1).firstOrNull { it.resolvedClass in setOf("L", "R") }?.resolvedClass
                val resolved = when {
                    previous != null && previous == next -> previous
                    previous != null && next == null -> previous
                    previous == null && next != null -> next
                    else -> baseDirectionCode(paragraphDirection)
                }
                scalar.copy(
                    resolvedClass = resolved,
                    trace = scalar.trace + BidiTraceEvent("W7/ON-neutral-resolution", scalar.utf16Range, scalar.originalClass, resolved),
                )
            }
        }
    }

    private fun buildRuns(
        scalars: List<BidiScalar>,
        clusters: List<GraphemeCluster>,
        paragraphDirection: String,
    ): List<ResolvedBidiRun> {
        val directionalScalars = scalars.filterNot { scalar -> controlKind(scalar.originalClass) != null }
        if (directionalScalars.isEmpty()) return emptyList()
        val runs = mutableListOf<ResolvedBidiRun>()
        var startIndex = 0
        var currentDirection = directionForClass(directionalScalars.first().resolvedClass, paragraphDirection)
        for (index in 1..directionalScalars.lastIndex) {
            val direction = directionForClass(directionalScalars[index].resolvedClass, paragraphDirection)
            if (direction != currentDirection) {
                runs += runFor(directionalScalars, startIndex, index - 1, clusters, currentDirection, paragraphDirection)
                startIndex = index
                currentDirection = direction
            }
        }
        runs += runFor(directionalScalars, startIndex, directionalScalars.lastIndex, clusters, currentDirection, paragraphDirection)
        return runs
    }

    private fun runFor(
        scalars: List<BidiScalar>,
        startIndex: Int,
        endIndex: Int,
        clusters: List<GraphemeCluster>,
        direction: String,
        paragraphDirection: String,
    ): ResolvedBidiRun {
        val rawRange = scalars[startIndex].utf16Range.first..scalars[endIndex].utf16Range.last
        val overlappingClusters = clusters.filter { cluster -> cluster.utf16Range.overlaps(rawRange) }
        val logicalRange = if (overlappingClusters.isEmpty()) {
            rawRange
        } else {
            overlappingClusters.minOf { cluster -> cluster.utf16Range.first }..
                overlappingClusters.maxOf { cluster -> cluster.utf16Range.last }
        }
        val clusterRange = if (overlappingClusters.isEmpty()) {
            0..0
        } else {
            overlappingClusters.first().clusterIndex..overlappingClusters.last().clusterIndex
        }
        val level = when {
            direction == BidiDirectionRtl -> 1
            paragraphDirection == ParagraphDirectionRtl -> 2
            else -> 0
        }
        return ResolvedBidiRun(
            logicalUtf16Range = logicalRange,
            clusterRange = clusterRange,
            embeddingLevel = level,
            direction = direction,
            paragraphDirection = paragraphDirection,
            resolvedBidiClasses = scalars.subList(startIndex, endIndex + 1).map { scalar -> scalar.resolvedClass },
            sourceControls = emptyList(),
        )
    }
}

private data class BidiScalar(
    val codePoint: Int,
    val utf16Range: IntRange,
    val clusterIndex: Int,
    val originalClass: String,
    val resolvedClass: String,
    val explicitDirection: String?,
    val explicitRule: String?,
    val trace: List<BidiTraceEvent>,
)

private data class ExplicitBidiState(
    val direction: String,
    val rule: String,
)

private data class OpenBidiControl(
    val kind: String,
    val expectedCloser: String,
)

private const val BIDI_SOURCE_TEXT_HASH_ALGORITHM = "sha256-utf16-code-units"
private const val ParagraphDirectionLtr = "LeftToRight"
private const val ParagraphDirectionRtl = "RightToLeft"
private const val BidiDirectionLtr = "L"
private const val BidiDirectionRtl = "R"

private val StrongBidiClasses = setOf("L", "R", "AL")
private val NeutralBidiClasses = setOf("B", "S", "WS", "ON", "BN", "LRE", "RLE", "LRO", "RLO", "PDF", "LRI", "RLI", "FSI", "PDI")
private val BidiNonClaims = listOf(
    "bounded-kfont-m5-003-fixture-evidence-only",
    "no-complete-uax9-claim",
    "no-paired-bracket-resolution-claim",
    "no-paragraph-visual-line-ordering-claim",
    "no-gsub-gpos-shaping-claim",
    "no-gpu-text-route-claim",
)

private fun paragraphDirectionName(requestedDirection: Int, scalars: List<BidiScalar>): String =
    when {
        requestedDirection < 0 -> ParagraphDirectionRtl
        requestedDirection > 0 -> ParagraphDirectionLtr
        else -> {
            val firstStrong = scalars.firstOrNull { scalar -> scalar.originalClass in StrongBidiClasses }?.originalClass
            if (firstStrong == "R" || firstStrong == "AL") ParagraphDirectionRtl else ParagraphDirectionLtr
        }
    }

private fun baseDirectionCode(paragraphDirection: String): String =
    if (paragraphDirection == ParagraphDirectionRtl) BidiDirectionRtl else BidiDirectionLtr

private fun directionForClass(bidiClass: String, paragraphDirection: String): String =
    when (bidiClass) {
        "R", "AL", "AN" -> BidiDirectionRtl
        "L", "EN" -> BidiDirectionLtr
        else -> baseDirectionCode(paragraphDirection)
    }

private fun nearestStrongBefore(scalars: List<BidiScalar>, index: Int): String? =
    scalars.take(index).lastOrNull { scalar -> scalar.originalClass in StrongBidiClasses }?.originalClass?.let { bidiClass ->
        if (bidiClass == "AL") "R" else bidiClass
    }

private fun controlKind(bidiClass: String): String? =
    when (bidiClass) {
        "LRE", "RLE", "LRO", "RLO", "PDF", "LRI", "RLI", "FSI", "PDI" -> bidiClass
        else -> null
    }

private fun List<BidiControl>.markOpenControls(openDepth: Int): List<BidiControl> {
    if (openDepth == 0) return this
    var remaining = openDepth
    return asReversed().map { control ->
        if (remaining > 0 && control.kind in setOf("LRE", "RLE", "LRO", "RLO", "LRI", "RLI", "FSI")) {
            remaining -= 1
            control.copy(balanced = false)
        } else {
            control
        }
    }.asReversed()
}

private fun IntRange.coerceFor(text: String): IntRange {
    if (text.isEmpty()) return 0..-1
    val first = first.coerceAtLeast(0)
    val last = last.coerceAtMost(text.lastIndex)
    return if (first <= last) first..last else 0..-1
}

private fun IntRange.shift(offset: Int): IntRange =
    first + offset..last + offset

private fun ShapingDiagnostic.shiftTextRange(offset: Int): ShapingDiagnostic =
    textRange?.let { range -> copy(textRange = range.shift(offset)) } ?: this

private fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

private fun IntRange.toRangeLabel(): String =
    if (isEmpty()) "" else "$first..$last"

private fun String.sourceTextHash(): String {
    val bytes = ByteArray(length * 2)
    forEachIndexed { index, char ->
        val value = char.code
        bytes[index * 2] = (value ushr 8).toByte()
        bytes[index * 2 + 1] = value.toByte()
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}

private fun BidiFixtureDumpInput.toCanonicalJson(): String = buildString {
    append("{\n")
    appendJsonField("fixtureName", fixtureName, comma = true, indent = "  ")
    appendJsonField("inputTextHash", result.sourceTextHash, comma = true, indent = "  ")
    appendJsonField("paragraphDirection", result.paragraphDirection, comma = true, indent = "  ")
    append("  \"clusters\": [\n")
    append(result.clusters.joinToString(",\n") { cluster -> cluster.toCanonicalJson().prependIndent("    ") })
    append("\n  ],\n")
    append("  \"runs\": [\n")
    append(result.runs.joinToString(",\n") { run -> run.toCanonicalJson().prependIndent("    ") })
    append("\n  ],\n")
    append("  \"sourceControls\": [")
    if (result.sourceControls.isNotEmpty()) {
        append("\n")
        append(result.sourceControls.joinToString(",\n") { control -> control.toCanonicalJson().prependIndent("    ") })
        append("\n  ")
    }
    append("],\n")
    append("  \"trace\": [")
    if (result.trace.isNotEmpty()) {
        append("\n")
        append(result.trace.joinToString(",\n") { event -> event.toCanonicalJson().prependIndent("    ") })
        append("\n  ")
    }
    append("],\n")
    append("  \"diagnostics\": [")
    if (result.diagnostics.isNotEmpty()) {
        append("\n")
        append(result.diagnostics.joinToString(",\n") { diagnostic -> diagnostic.toCanonicalJson().prependIndent("    ") })
        append("\n  ")
    }
    append("]\n")
    append("}")
}

private fun GraphemeCluster.toCanonicalJson(): String = buildString {
    append("{")
    append(jsonString("clusterIndex")).append(": ").append(clusterIndex).append(", ")
    append(jsonString("utf16Range")).append(": ").append(jsonString(utf16Range.toRangeLabel())).append(", ")
    append(jsonString("codePointRange")).append(": ").append(jsonString(codePointRange.toRangeLabel())).append(", ")
    append(jsonString("clusterLevel")).append(": ").append(clusterLevel).append(", ")
    append(jsonString("sourceTextHash")).append(": ").append(jsonString(sourceTextHash)).append(", ")
    append(jsonString("unicodeVersion")).append(": ").append(jsonString(unicodeVersion)).append(", ")
    append(jsonString("breakBeforeRuleId")).append(": ").append(jsonString(breakBeforeRuleId))
    append("}")
}

private fun ResolvedBidiRun.toCanonicalJson(): String = buildString {
    append("{")
    append(jsonString("logicalUtf16Range")).append(": ").append(jsonString(logicalUtf16Range.toRangeLabel())).append(", ")
    append(jsonString("clusterRange")).append(": ").append(jsonString(clusterRange.toRangeLabel())).append(", ")
    append(jsonString("embeddingLevel")).append(": ").append(embeddingLevel).append(", ")
    append(jsonString("direction")).append(": ").append(jsonString(direction)).append(", ")
    append(jsonString("paragraphDirection")).append(": ").append(jsonString(paragraphDirection)).append(", ")
    append(jsonString("resolvedBidiClasses")).append(": ").append(resolvedBidiClasses.joinToString(prefix = "[", postfix = "]") { jsonString(it) }).append(", ")
    append(jsonString("sourceControls")).append(": ").append(sourceControls.joinToString(prefix = "[", postfix = "]") { it.toCanonicalJson() })
    append("}")
}

private fun BidiControl.toCanonicalJson(): String = buildString {
    append("{")
    append(jsonString("kind")).append(": ").append(jsonString(kind)).append(", ")
    append(jsonString("utf16Range")).append(": ").append(jsonString(utf16Range.toRangeLabel())).append(", ")
    append(jsonString("depthBefore")).append(": ").append(depthBefore).append(", ")
    append(jsonString("depthAfter")).append(": ").append(depthAfter).append(", ")
    append(jsonString("balanced")).append(": ").append(balanced)
    append("}")
}

private fun BidiTraceEvent.toCanonicalJson(): String = buildString {
    append("{")
    append(jsonString("rule")).append(": ").append(jsonString(rule)).append(", ")
    append(jsonString("utf16Range")).append(": ").append(jsonString(utf16Range.toRangeLabel())).append(", ")
    append(jsonString("beforeClass")).append(": ").append(jsonString(beforeClass)).append(", ")
    append(jsonString("afterClass")).append(": ").append(jsonString(afterClass))
    append("}")
}

private fun ShapingDiagnostic.toCanonicalJson(): String = buildString {
    append("{")
    append(jsonString("code")).append(": ").append(jsonString(code)).append(", ")
    append(jsonString("severity")).append(": ").append(jsonString("refusal")).append(", ")
    append(jsonString("textRange")).append(": ").append(jsonString(textRange?.toRangeLabel())).append(", ")
    append(jsonString("message")).append(": ").append(jsonString(message))
    append("}")
}

private fun StringBuilder.appendJsonField(name: String, value: String?, comma: Boolean, indent: String = "  ") {
    append(indent).append(jsonString(name)).append(": ").append(jsonString(value))
    if (comma) append(",")
    append("\n")
}

private fun jsonString(value: String?): String =
    value?.let {
        buildString {
            append('"')
            for (char in it) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    } ?: "null"
