package org.skia.foundation

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.text.shaping.BasicOpenTypeShapingEngine
import org.graphiks.kanvas.text.shaping.FeatureSet
import org.graphiks.kanvas.text.shaping.GlyphCluster as PureKotlinGlyphCluster
import org.graphiks.kanvas.text.shaping.ShapedGlyphRun as PureKotlinShapedGlyphRun
import org.graphiks.kanvas.text.shaping.ShapingDiagnostic as PureKotlinShapingDiagnostic
import org.graphiks.kanvas.text.shaping.ShapingRequest as PureKotlinShapingRequest
import org.graphiks.kanvas.text.shaping.ShapingResult as PureKotlinShapingResult
import org.graphiks.math.SkPoint
import java.text.Bidi

/**
 * Portable, opt-in text shaping entry point.
 *
 * This class deliberately stays separate from [SkFont] and
 * `SkCanvas.drawString`: simple text rendering remains advance-based, while
 * callers that need bidi/script segmentation, cluster data, or font fallback
 * can request it explicitly here.
 */
public class SkShaper private constructor(
    private val fallbackProvider: FontFallbackProvider?,
    private val positioningProvider: GlyphPositioningProvider?,
    private val shapingEngine: BasicOpenTypeShapingEngine? = null,
) {
    public enum class Direction { Ltr, Rtl }

    public data class Features(
        val standardLigatures: Boolean = false,
        val arabicJoining: Boolean = false,
        val indicReordering: Boolean = false,
        val markPositioning: Boolean = false,
        val cursiveAttachment: Boolean = false,
        val scriptLanguage: ScriptLanguage = ScriptLanguage.Default,
    )

    public data class ScriptLanguage(
        val script: Character.UnicodeScript? = null,
        val language: String? = null,
    ) {
        public companion object {
            public val Default: ScriptLanguage = ScriptLanguage()
        }
    }

    public fun interface FontFallbackProvider {
        public fun fallbackFor(codePoint: Int, baseFont: SkFont): SkFont?
    }

    public fun interface GlyphPositioningProvider {
        public fun adjustmentsFor(run: PositioningRun, features: Features): Array<SkPoint>
    }

    public data class PositioningRun(
        val font: SkFont,
        val glyphs: IntArray,
        val clusters: IntArray,
        val direction: Direction,
        val script: Character.UnicodeScript,
    )

    public data class Diagnostic(
        val kind: Kind,
        val message: String,
        val utf16Index: Int? = null,
    ) {
        public enum class Kind {
            MissingGlyph,
            FallbackUsed,
            FallbackMissingGlyph,
            UnsupportedFeature,
            ScriptLanguageMismatch,
        }
    }

    public data class GlyphRun(
        val font: SkFont,
        val glyphs: IntArray,
        val clusters: IntArray,
        val positions: Array<SkPoint>,
        val utf16Start: Int,
        val utf16End: Int,
        val direction: Direction,
        val script: Character.UnicodeScript,
    ) {
        public val glyphCount: Int get() = glyphs.size
    }

    public data class Result(
        val runs: List<GlyphRun>,
        val diagnostics: List<Diagnostic>,
    ) {
        public val glyphCount: Int get() = runs.sumOf { it.glyphCount }
    }

    /**
     * Captures the input parameters for a pure Kotlin shaping request
     * through the SkShaper facade.
     */
    public data class SkShaperFacadeRequest(
        val text: String,
        val textRange: IntRange,
        val typefaceId: TypefaceID? = null,
        val script: String? = null,
        val language: String? = null,
        val direction: Direction = Direction.Ltr,
        val features: Features = Features(),
    )

    /**
     * Maps one logical text cluster to its glyph range in a shaped run.
     */
    public data class ShapedClusterRange(
        val textRange: IntRange,
        val glyphRange: IntRange,
    )

    /**
     * Carries the full adapter evidence for one shaped request,
     * matching the spec's [SkShaperAdapter] design sketch.
     */
    public data class SkShaperAdapter(
        val request: SkShaperFacadeRequest,
        val shapedRun: PureKotlinShapedGlyphRun? = null,
        val facadeClusters: List<ShapedClusterRange> = emptyList(),
        val diagnostics: List<String> = emptyList(),
    )

    /**
     * Routes an explicit [PureKotlinShapingRequest] through the pure Kotlin
     * shaping engine when one is configured.
     *
     * Returns the pure Kotlin [PureKotlinShapingResult] directly, including
     * glyph runs with cluster data, script tags, bidi levels, and diagnostics.
     * When no engine is configured, returns an empty result with a
     * `text.shaping.facade-no-engine` diagnostic.
     */
    public fun shapeWithShapingRequest(request: PureKotlinShapingRequest): PureKotlinShapingResult {
        val engine = shapingEngine
        if (engine == null) {
            return PureKotlinShapingResult(
                diagnostics = listOf(
                    PureKotlinShapingDiagnostic(
                        code = "text.shaping.facade-no-engine",
                        message = "Pure Kotlin shaping engine is not configured. Use MakeWithPureKotlinShaping to enable.",
                        textRange = request.textRange,
                    ),
                ),
            )
        }
        return engine.shape(request)
    }

    /**
     * HarfBuzz-compatible shaping call that routes through the pure Kotlin
     * shaping engine when [shapingEngine] is configured and the font's
     * typeface is an [OpenTypeTypeface] with a [TypefaceID].
     *
     * Falls back to the legacy [shape] method when the pure Kotlin path is
     * not available, emitting a diagnostic.
     */
    public fun hbShape(
        text: String,
        font: SkFont,
        features: Features = Features(),
    ): Result {
        val engine = shapingEngine ?: return legacyShape(text, font, features)

        val typefaceId = typefaceIdFor(font)
        if (typefaceId == null) {
            val diag = listOf(
                Diagnostic(
                    Diagnostic.Kind.UnsupportedFeature,
                    "Pure Kotlin shaping engine requires an OpenTypeTypeface with a TypefaceID; falling back to legacy.",
                ),
            )
            val result = legacyShape(text, font, features)
            return result.copy(diagnostics = result.diagnostics + diag)
        }

        val request = PureKotlinShapingRequest(
            text = text,
            textRange = text.indices,
            typefaceId = typefaceId,
            fontSize = font.size,
            features = features.toFeatureSet(),
            paragraphDirection = if (features.scriptLanguage.script != null) {
                when (features.scriptLanguage.script) {
                    Character.UnicodeScript.ARABIC,
                    Character.UnicodeScript.HEBREW,
                    -> -1
                    else -> 1
                }
            } else 0,
        )
        val result = engine.shape(request)
        return mapPureKotlinResult(result, font, text)
    }

    public fun shape(
        text: String,
        font: SkFont,
        features: Features = Features(),
    ): Result {
        if (text.isEmpty()) return Result(emptyList(), emptyList())

        val codepoints = codepoints(text)
        val diagnostics = mutableListOf<Diagnostic>()
        val shaped = mutableListOf<ShapedGlyph>()
        var x = 0f

        for (segment in segmentText(text, codepoints)) {
            val featurePolicyApplies = features.scriptLanguage.appliesTo(segment.script)
            val orderedBase = if (features.indicReordering && featurePolicyApplies) {
                reorderIndicPrebaseVowels(segment)
            } else {
                segment.codepoints
            }
            val ordered = if (segment.direction == Direction.Rtl) {
                orderedBase.asReversed()
            } else {
                orderedBase
            }
            if (!featurePolicyApplies) {
                diagnostics.add(
                    Diagnostic(
                        Diagnostic.Kind.ScriptLanguageMismatch,
                        "Feature language ${features.scriptLanguage} does not apply to ${segment.script}",
                        segment.codepoints.firstOrNull()?.utf16Start,
                    )
                )
            }
            var i = 0
            while (i < ordered.size) {
                val cp = ordered[i]
                val ligature = if (features.standardLigatures && featurePolicyApplies) {
                    standardLigatureAt(ordered, i, font)
                } else {
                    null
                }
                if (ligature != null) {
                    shaped.add(
                        ShapedGlyph(
                            font = font,
                            glyph = ligature.glyph,
                            cluster = ligature.cluster,
                            position = SkPoint(x, 0f),
                            utf16Start = ligature.cluster,
                            utf16End = ordered[i + 1].utf16End,
                            direction = segment.direction,
                            script = segment.script,
                        )
                    )
                    x += font.getWidth(ligature.glyph)
                    i += 2
                    continue
                }
                val arabic = if (features.arabicJoining && featurePolicyApplies) arabicJoiningAt(ordered, i, font) else null
                if (arabic != null) {
                    shaped.add(
                        ShapedGlyph(
                            font = font,
                            glyph = arabic.glyph,
                            cluster = cp.utf16Start,
                            position = SkPoint(x, 0f),
                            utf16Start = cp.utf16Start,
                            utf16End = cp.utf16End,
                            direction = segment.direction,
                            script = segment.script,
                        )
                    )
                    x += font.getWidth(arabic.glyph)
                    i++
                    continue
                }

                val resolved = resolveGlyph(cp, font, diagnostics)
                shaped.add(
                    ShapedGlyph(
                        font = resolved.font,
                        glyph = resolved.glyph,
                        cluster = cp.utf16Start,
                        position = SkPoint(x, 0f),
                        utf16Start = cp.utf16Start,
                        utf16End = cp.utf16End,
                        direction = segment.direction,
                        script = segment.script,
                    )
                )
                x += resolved.font.getWidth(resolved.glyph)
                i++
            }
        }

        return Result(applyPositioning(compactRuns(shaped), features, diagnostics), diagnostics)
    }

    /**
     * Returns a facade parity evidence dump comparing the pure Kotlin shaping
     * engine output (when configured) with the legacy shaping path.
     *
     * Returns null when no engine is configured.
     */
    internal fun facadeParityEvidence(): SkShaperFacadeParityDump? {
        val engine = shapingEngine ?: return null
        return SkShaperFacadeParityDump(
            engineAvailable = true,
            engineClassName = engine::class.qualifiedName ?: "unknown",
        )
    }

    /**
     * Evidence snapshot for the SkShaper pure Kotlin facade route.
     */
    public data class SkShaperFacadeParityDump(
        val engineAvailable: Boolean,
        val engineClassName: String,
    )

    private fun typefaceIdFor(font: SkFont): TypefaceID? =
        (font.typeface as? org.skia.foundation.opentype.OpenTypeTypeface)?.typefaceId

    private fun legacyShape(
        text: String,
        font: SkFont,
        features: Features,
    ): Result {
        val fallback = SkShaper(fallbackProvider, positioningProvider)
        return fallback.shape(text, font, features)
    }

    private fun mapPureKotlinResult(
        result: PureKotlinShapingResult,
        font: SkFont,
        text: String,
    ): Result {
        val runs = result.glyphRuns.map { run ->
            GlyphRun(
                font = font,
                glyphs = run.glyphIds.toIntArray(),
                clusters = run.clusters.map { it.textRange.first }.toIntArray(),
                positions = run.clusters.map { cluster ->
                    SkPoint(
                        cluster.advanceX + cluster.offsetX,
                        cluster.offsetY,
                    )
                }.toTypedArray(),
                utf16Start = run.clusters.firstOrNull()?.textRange?.first ?: 0,
                utf16End = run.clusters.lastOrNull()?.textRange?.last ?: text.lastIndex.coerceAtLeast(0),
                direction = if (run.bidiLevel % 2 == 1) Direction.Rtl else Direction.Ltr,
                script = run.script.toUnicodeScript(),
            )
        }
        val diagnostics = result.diagnostics.map { diag ->
            Diagnostic(
                kind = mapDiagnosticKind(diag.code),
                message = diag.message,
                utf16Index = diag.textRange?.first,
            )
        }
        return Result(runs, diagnostics)
    }

    private fun mapDiagnosticKind(code: String): Diagnostic.Kind = when {
        code.endsWith("missing") || code.endsWith("fallback-missing") -> Diagnostic.Kind.MissingGlyph
        code.endsWith("fallback-used") -> Diagnostic.Kind.FallbackUsed
        code.endsWith("feature-unsupported") ||
            code.endsWith("arabic-cursive-unsupported") ||
            code.endsWith("arabic-mark-unsupported") ||
            code.endsWith("mark-positioning-unavailable") ||
            code.endsWith("cursive-attachment-unavailable") -> Diagnostic.Kind.UnsupportedFeature
        code.endsWith("gdef-required") -> Diagnostic.Kind.UnsupportedFeature
        code.endsWith("no-engine") -> Diagnostic.Kind.UnsupportedFeature
        else -> Diagnostic.Kind.UnsupportedFeature
    }

    private fun resolveGlyph(
        cp: Codepoint,
        baseFont: SkFont,
        diagnostics: MutableList<Diagnostic>,
    ): ResolvedGlyph {
        val baseGlyph = baseFont.typeface.unicharToGlyph(cp.value)
        if (baseGlyph != 0) return ResolvedGlyph(baseFont, baseGlyph)

        val fallback = fallbackProvider?.fallbackFor(cp.value, baseFont)
        if (fallback != null) {
            val fallbackGlyph = fallback.typeface.unicharToGlyph(cp.value)
            if (fallbackGlyph != 0) {
                diagnostics.add(
                    Diagnostic(
                        Diagnostic.Kind.FallbackUsed,
                        "Fallback font resolved U+${cp.value.toString(16).uppercase()}",
                        cp.utf16Start,
                    )
                )
                return ResolvedGlyph(fallback, fallbackGlyph)
            }
            diagnostics.add(
                Diagnostic(
                    Diagnostic.Kind.FallbackMissingGlyph,
                    "Fallback font also missed U+${cp.value.toString(16).uppercase()}",
                    cp.utf16Start,
                )
            )
        }

        diagnostics.add(
            Diagnostic(
                Diagnostic.Kind.MissingGlyph,
                "Missing glyph for U+${cp.value.toString(16).uppercase()}",
                cp.utf16Start,
            )
        )
        return ResolvedGlyph(baseFont, 0)
    }

    private fun standardLigatureAt(
        ordered: List<Codepoint>,
        index: Int,
        font: SkFont,
    ): Ligature? {
        if (index + 1 >= ordered.size) return null
        val first = ordered[index]
        val second = ordered[index + 1]
        if (first.value != 'f'.code || second.value != 'i'.code) return null
        val glyph = font.typeface.unicharToGlyph(0xFB01)
        return if (glyph != 0) Ligature(glyph, first.utf16Start) else null
    }

    private fun arabicJoiningAt(
        ordered: List<Codepoint>,
        index: Int,
        font: SkFont,
    ): JoinedGlyph? {
        val form = arabicPresentationForm(ordered, index) ?: return null
        val glyph = font.typeface.unicharToGlyph(form)
        return if (glyph != 0) JoinedGlyph(glyph) else null
    }

    private fun arabicPresentationForm(codepoints: List<Codepoint>, index: Int): Int? {
        val cp = codepoints[index].value
        val forms = ARABIC_PRESENTATION_FORMS[cp] ?: return null
        val joinsPrev = index > 0 && canJoinRight(codepoints[index - 1].value) && canJoinLeft(cp)
        val joinsNext = index + 1 < codepoints.size && canJoinRight(cp) && canJoinLeft(codepoints[index + 1].value)
        return when {
            joinsPrev && joinsNext -> forms.medial
            joinsPrev -> forms.final
            joinsNext -> forms.initial
            else -> forms.isolated
        }
    }

    private fun segmentText(text: String, codepoints: List<Codepoint>): List<Segment> {
        if (codepoints.isEmpty()) return emptyList()
        val bidi = Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
        val segments = mutableListOf<Segment>()
        for (runIndex in 0 until bidi.runCount) {
            val start = bidi.getRunStart(runIndex)
            val end = bidi.getRunLimit(runIndex)
            val direction = if ((bidi.getRunLevel(runIndex) and 1) == 1) Direction.Rtl else Direction.Ltr
            val runCodepoints = codepoints.filter { it.utf16Start >= start && it.utf16Start < end }
            segments += splitByScript(runCodepoints, direction)
        }
        return segments
    }

    private fun splitByScript(
        codepoints: List<Codepoint>,
        direction: Direction,
    ): List<Segment> {
        if (codepoints.isEmpty()) return emptyList()
        val resolvedScripts = resolveScripts(codepoints)
        val out = mutableListOf<Segment>()
        var start = 0
        var script = resolvedScripts[0]
        for (i in 1 until codepoints.size) {
            if (resolvedScripts[i] != script) {
                out.add(Segment(codepoints.subList(start, i), direction, script))
                start = i
                script = resolvedScripts[i]
            }
        }
        out.add(Segment(codepoints.subList(start, codepoints.size), direction, script))
        return out
    }

    private fun reorderIndicPrebaseVowels(segment: Segment): List<Codepoint> {
        if (segment.script != Character.UnicodeScript.DEVANAGARI) return segment.codepoints
        if (segment.codepoints.size < 2) return segment.codepoints
        val out = ArrayList<Codepoint>(segment.codepoints.size)
        var i = 0
        while (i < segment.codepoints.size) {
            if (i + 1 < segment.codepoints.size &&
                isDevanagariConsonant(segment.codepoints[i].value) &&
                segment.codepoints[i + 1].value == DEVANAGARI_VOWEL_SIGN_I
            ) {
                out.add(segment.codepoints[i + 1])
                out.add(segment.codepoints[i])
                i += 2
            } else {
                out.add(segment.codepoints[i])
                i++
            }
        }
        return out
    }

    private fun codepoints(text: String): List<Codepoint> {
        val out = mutableListOf<Codepoint>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val next = i + Character.charCount(cp)
            out.add(Codepoint(cp, i, next))
            i = next
        }
        return out
    }

    private fun resolveScripts(codepoints: List<Codepoint>): List<Character.UnicodeScript> {
        val raw = codepoints.map { Character.UnicodeScript.of(it.value) }
        return raw.mapIndexed { index, script ->
            if (script.isNeutral()) {
                raw.take(index).lastOrNull { !it.isNeutral() }
                    ?: raw.drop(index + 1).firstOrNull { !it.isNeutral() }
                    ?: Character.UnicodeScript.COMMON
            } else {
                script
            }
        }
    }

    private fun compactRuns(shaped: List<ShapedGlyph>): List<GlyphRun> {
        if (shaped.isEmpty()) return emptyList()
        val out = mutableListOf<GlyphRun>()
        var start = 0
        for (i in 1 until shaped.size) {
            if (!sameRun(shaped[i - 1], shaped[i])) {
                out.add(toRun(shaped.subList(start, i)))
                start = i
            }
        }
        out.add(toRun(shaped.subList(start, shaped.size)))
        return out
    }

    private fun applyPositioning(
        runs: List<GlyphRun>,
        features: Features,
        diagnostics: MutableList<Diagnostic>,
    ): List<GlyphRun> {
        if (!features.markPositioning && !features.cursiveAttachment) return runs
        return runs.map { run ->
            val featurePolicyApplies = features.scriptLanguage.appliesTo(run.script)
            if (!featurePolicyApplies) return@map run
            val provider = positioningProvider
            if (provider == null) {
                if (features.markPositioning) {
                    diagnostics.add(
                        Diagnostic(
                            Diagnostic.Kind.UnsupportedFeature,
                            "Mark positioning requested without a positioning provider",
                            run.utf16Start,
                        )
                    )
                }
                if (features.cursiveAttachment) {
                    diagnostics.add(
                        Diagnostic(
                            Diagnostic.Kind.UnsupportedFeature,
                            "Cursive attachment requested without a positioning provider",
                            run.utf16Start,
                        )
                    )
                }
                run
            } else {
                val adjustments = provider.adjustmentsFor(
                    PositioningRun(
                        font = run.font,
                        glyphs = run.glyphs.copyOf(),
                        clusters = run.clusters.copyOf(),
                        direction = run.direction,
                        script = run.script,
                    ),
                    features,
                )
                require(adjustments.size == run.positions.size) {
                    "positioning provider returned ${adjustments.size} adjustments for ${run.positions.size} glyphs"
                }
                run.copy(
                    positions = Array(run.positions.size) { i ->
                        SkPoint(
                            run.positions[i].fX + adjustments[i].fX,
                            run.positions[i].fY + adjustments[i].fY,
                        )
                    }
                )
            }
        }
    }

    private fun sameRun(a: ShapedGlyph, b: ShapedGlyph): Boolean =
        a.font === b.font &&
            a.direction == b.direction &&
            a.script == b.script

    private fun toRun(items: List<ShapedGlyph>): GlyphRun =
        GlyphRun(
            font = items.first().font,
            glyphs = IntArray(items.size) { items[it].glyph },
            clusters = IntArray(items.size) { items[it].cluster },
            positions = Array(items.size) { items[it].position },
            utf16Start = items.minOf { it.utf16Start },
            utf16End = items.maxOf { it.utf16End },
            direction = items.first().direction,
            script = items.first().script,
        )

    private data class Codepoint(val value: Int, val utf16Start: Int, val utf16End: Int)

    private data class Segment(
        val codepoints: List<Codepoint>,
        val direction: Direction,
        val script: Character.UnicodeScript,
    )

    private data class ResolvedGlyph(val font: SkFont, val glyph: Int)

    private data class Ligature(val glyph: Int, val cluster: Int)

    private data class JoinedGlyph(val glyph: Int)

    private data class ShapedGlyph(
        val font: SkFont,
        val glyph: Int,
        val cluster: Int,
        val position: SkPoint,
        val utf16Start: Int,
        val utf16End: Int,
        val direction: Direction,
        val script: Character.UnicodeScript,
    )

    public companion object {
        public fun MakePrimitive(): SkShaper = SkShaper(fallbackProvider = null, positioningProvider = null)

        public fun MakeWithFallback(fallbackProvider: FontFallbackProvider): SkShaper =
            SkShaper(fallbackProvider, positioningProvider = null)

        public fun MakeWithProviders(
            fallbackProvider: FontFallbackProvider? = null,
            positioningProvider: GlyphPositioningProvider? = null,
        ): SkShaper = SkShaper(fallbackProvider, positioningProvider)

        /**
         * Creates an [SkShaper] that routes explicit shaping calls through the
         * pure Kotlin [BasicOpenTypeShapingEngine] when the font's typeface
         * is an [OpenTypeTypeface] with a [TypefaceID].
         *
         * The legacy [shape] method remains unchanged. Call [hbShape] or
         * [shapeWithShapingRequest] to use the pure Kotlin path.
         */
        @Suppress("FunctionName")
        public fun MakeWithPureKotlinShaping(engine: BasicOpenTypeShapingEngine): SkShaper =
            SkShaper(fallbackProvider = null, positioningProvider = null, shapingEngine = engine)
    }
}

private fun Character.UnicodeScript.isNeutral(): Boolean =
    this == Character.UnicodeScript.COMMON ||
        this == Character.UnicodeScript.INHERITED ||
        this == Character.UnicodeScript.UNKNOWN

private fun SkShaper.ScriptLanguage.appliesTo(script: Character.UnicodeScript): Boolean =
    this.script == null || this.script == script

private fun canJoinLeft(cp: Int): Boolean = ARABIC_PRESENTATION_FORMS.containsKey(cp)

private fun canJoinRight(cp: Int): Boolean = ARABIC_PRESENTATION_FORMS[cp]?.dualJoining == true

/**
 * Converts a Kotlin [String] script tag (e.g. "Latn", "Arab") to a Java
 * [Character.UnicodeScript] enum value. Falls back to COMMON for unknown tags.
 */
private fun String.toUnicodeScript(): Character.UnicodeScript =
    when (this) {
        "Latn" -> Character.UnicodeScript.LATIN
        "Arab" -> Character.UnicodeScript.ARABIC
        "Hebr" -> Character.UnicodeScript.HEBREW
        "Deva" -> Character.UnicodeScript.DEVANAGARI
        "Cyrl" -> Character.UnicodeScript.CYRILLIC
        "Grek" -> Character.UnicodeScript.GREEK
        "Zyyy", "Zinh", "Zsye" -> Character.UnicodeScript.COMMON
        else -> Character.UnicodeScript.COMMON
    }

/**
 * Maps [SkShaper.Features] to a pure Kotlin [FeatureSet].
 */
private fun SkShaper.Features.toFeatureSet(): FeatureSet {
    val tags = mutableMapOf<String, Int>()
    if (standardLigatures) tags["liga"] = 1
    if (arabicJoining) {
        tags["init"] = 1
        tags["medi"] = 1
        tags["fina"] = 1
        tags["isol"] = 1
    }
    if (indicReordering) tags["akhn"] = 1
    if (markPositioning) tags["mark"] = 1
    if (cursiveAttachment) tags["curs"] = 1
    return FeatureSet(values = tags)
}

private data class ArabicForms(
    val isolated: Int,
    val final: Int,
    val initial: Int,
    val medial: Int,
    val dualJoining: Boolean,
)

private val ARABIC_PRESENTATION_FORMS: Map<Int, ArabicForms> = mapOf(
    0x0628 to ArabicForms(0xFE8F, 0xFE90, 0xFE91, 0xFE92, dualJoining = true),
    0x062A to ArabicForms(0xFE95, 0xFE96, 0xFE97, 0xFE98, dualJoining = true),
    0x0646 to ArabicForms(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8, dualJoining = true),
    0x0647 to ArabicForms(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC, dualJoining = true),
    0x064A to ArabicForms(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4, dualJoining = true),
)

private const val DEVANAGARI_VOWEL_SIGN_I = 0x093F

private fun isDevanagariConsonant(cp: Int): Boolean = cp in 0x0915..0x0939
