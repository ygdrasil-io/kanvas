package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkIRect
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkTypeface_Fontations
import org.skia.foundation.stream.SkMemoryStream
import org.skia.shaper.SkShaper
import org.skia.shaper.SkTextBlobShaperRunHandler
import org.skia.tools.ToolUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Port of Skia's
 * [`gm/fontations_ft_compare.cpp`](https://github.com/google/skia/blob/main/gm/fontations_ft_compare.cpp)
 * — shapes and draws the same UTF-8 sample text side-by-side through
 * Google's Rust [`fontations`](https://github.com/googlefonts/fontations)
 * scaler **and** FreeType, then computes per-pixel diff +
 * highlight bitmaps so testers can spot bit-level regressions across
 * the two backends. Upstream registers 29 `DEF_GM` instances covering
 * 25 distinct Noto Sans script subsets across LTR / RTL / Indic /
 * CJK / Arabic / Mongolian / Vithkuqi / Elbasan / Gurmukhi
 * scripts, each renderable in four hinting modes
 * (`kNone` / `kSlight` / `kNormal` / `kFull`) and two pixel-geometry
 * surface configurations (`kLeaveAsIs` for the device's reported
 * geometry, `kSimulateUnknown` for the canonical "unknown stripe
 * order" path — see [SkSurfaceProps.SkPixelGeometry.kUnknown]).
 *
 * ## Port status — **INTRACTABLE without JNI / native bindings**
 *
 * Three stacked blockers prevent the test from running today :
 *
 *  1. **`STUB.FONTATIONS`** — upstream's `SkTypeface_Fontations::MakeFromStream`
 *     mints a typeface backed by Google's Rust
 *     [`fontations`](https://github.com/googlefonts/fontations) crate
 *     (skrifa / read-fonts) ; `:kanvas-skia` is pure-JVM and exposes
 *     [SkTypeface_Fontations] as a surface stub that throws
 *     `STUB.FONTATIONS` on dispatch. This GM's entire raison d'être is
 *     to compare that backend against FreeType, so absent the JNI/UniFFI
 *     bridge there is nothing meaningful to render.
 *  2. **`STUB.FREETYPE`** — the symmetric side of the comparison
 *     instantiates `SkTypeface_FreeType::MakeFromStream`. The Kotlin
 *     port routes that call through
 *     [org.skia.tools.ToolUtils.CreateTypefaceFromResource] (the
 *     default AWT-backed scaler), which decodes a subset of OpenType
 *     but is **not** a FreeType binding — so even if both calls
 *     returned typefaces, their rasterised pixels would not byte-match
 *     upstream's reference.
 *  3. **`STUB.FIXTURE`** — the 25 `NotoSans*` font files referenced
 *     by `TestFontDataProvider` (Devanagari, Arabic, Bengali, JP,
 *     Thai, SC, TC, KR, Tamil, Newa, Kannada, Tagalog, Telugu,
 *     Gujarati, Georgian, Malayalam, Khmer, Sinhala, Myanmar,
 *     Javanese, Mongolian, Armenian, Elbasan, Vithkuqi, Gurmukhi)
 *     are not shipped under `kanvas-legacy/src/test/resources/fonts/`.
 *     [TestFontDataProvider] resolves filenames optimistically through
 *     [ToolUtils.GetResourceAsData] — every entry returns `null`
 *     today, mirroring upstream's `SkStream::MakeFromFile` failure
 *     branch + the `errorMsg = "Unable to initialize typeface."` +
 *     `DrawResult::kSkip` short-circuit.
 *
 * The Kotlin body below is **the real upstream pipeline** — the
 * three-phase `Fontations / FreeType / Comparison` loop, the
 * per-language sample iteration through [SkShaper.MakeJvmAwtTextLayout]
 * + [SkTextBlobShaperRunHandler] + [SkCanvas.drawTextBlob], the
 * `roundToDevicePixels` CTM-aware snap, the `comparePixels` per-pixel
 * absolute-difference loop, and the comparison-phase write-back
 * dual — all wired through the live `:kanvas-skia` API surface so a
 * future JNI drop-in of [SkTypeface_Fontations] is a no-touch swap on
 * the call site. The test class is `@Disabled("STUB.FONTATIONS")`
 * until that binding lands.
 *
 * The constructor parameters mirror upstream's
 * `FontationsFtCompareGM(testName, fontNameFilterRegexp,
 *  langFilterRegexp, simulatePixelGeometry, hintingMode)`. The no-arg
 * constructor used by the JUnit harness defaults to the upstream
 * `"NotoSans"` Latin / Cyrillic / Greek variant.
 */
public class FontationsFtCompareGM(
    private val testName: String,
    private val fontNameFilterRegexp: String,
    private val langFilterRegexp: String,
    private val simulatePixelGeometry: SimulatePixelGeometry,
    private val hintingMode: SkFontHinting = SkFontHinting.kNone,
) : GM() {

    /**
     * Mirrors upstream's `enum SimulatePixelGeometry { kLeaveAsIs,
     * kSimulateUnknown }`. When [kSimulateUnknown] is selected, the
     * GM clones the surface props to advertise
     * [SkSurfaceProps.SkPixelGeometry.kUnknown] — equivalent to
     * forcing greyscale fallback for LCD subpixel text.
     */
    public enum class SimulatePixelGeometry { kLeaveAsIs, kSimulateUnknown }

    /** Mirrors upstream's `TestFontDataProvider` — filtered iterator
     *  yielding `{ fontFilename, langSamples }` tuples. */
    private val testDataIterator: TestFontDataProvider =
        TestFontDataProvider(fontNameFilterRegexp, langFilterRegexp)

    // No-arg constructor for JUnit harness — defaults to the upstream
    // "NotoSans" Latin / Cyrillic / Greek variant, kLeaveAsIs geometry,
    // kNone hinting. Matches the first DEF_GM line in upstream cpp.
    public constructor() : this(
        testName = "NotoSans",
        fontNameFilterRegexp = "Noto Sans",
        langFilterRegexp = DEFAULT_LATIN_LANG_FILTER,
        simulatePixelGeometry = SimulatePixelGeometry.kLeaveAsIs,
        hintingMode = SkFontHinting.kNone,
    )

    init {
        // Upstream calls `this->setBGColor(SK_ColorWHITE)` in its ctor.
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String {
        val sb = StringBuilder("fontations_compare_ft_").append(testName)
        when (hintingMode) {
            SkFontHinting.kNormal -> sb.append("_hint_normal")
            SkFontHinting.kSlight -> sb.append("_hint_slight")
            SkFontHinting.kFull -> sb.append("_hint_full")
            SkFontHinting.kNone -> sb.append("_hint_none")
        }
        if (simulatePixelGeometry == SimulatePixelGeometry.kSimulateUnknown) {
            sb.append("_unknown_px_geometry")
        }
        return sb.toString()
    }

    override fun getISize(): SkISize {
        // Mirrors upstream — uses the first test set's sample count
        // to size the canvas height. With the fixtures missing the
        // iterator stays empty ; fall back to a sane default that
        // matches the upstream "Latin" variant (~46 languages × 24pt
        // × 1.9 = ~2096 + 100 padding).
        testDataIterator.rewind()
        val testSet = TestFontDataProvider.TestSet()
        testDataIterator.next(testSet)
        val sampleCount = if (testSet.langSamples.isNotEmpty()) {
            testSet.langSamples.size
        } else {
            // Fixture-missing fallback : use the lang-filter token
            // count as a stand-in (one row per requested locale).
            langFilterRegexp.count { it == '|' } + 1
        }
        val height = (sampleCount * K_FONT_SIZE * K_LANG_Y_INCREMENT_SCALE + 100).toInt()
        return SkISize.Make(K_GM_WIDTH, height)
    }

    /**
     * Mirrors upstream's
     * `void modifySurfaceProps(SkSurfaceProps* props) const override`.
     * When the variant was constructed with [SimulatePixelGeometry.kSimulateUnknown]
     * the canvas props are forced to
     * [SkSurfaceProps.SkPixelGeometry.kUnknown].
     */
    public fun modifySurfaceProps(props: SkSurfaceProps): SkSurfaceProps {
        return if (simulatePixelGeometry == SimulatePixelGeometry.kSimulateUnknown) {
            props.copy(pixelGeometry = SkSurfaceProps.SkPixelGeometry.kUnknown)
        } else {
            props
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        val paint = SkPaint().apply { color = SK_ColorBLACK }

        testDataIterator.rewind()
        val testSet = TestFontDataProvider.TestSet()

        while (testDataIterator.next(testSet)) {
            // Load typefaces — Fontations (Rust scaler) on the left,
            // FreeType on the right. Both go through their respective
            // MakeFromStream factories in upstream ; the Kotlin port
            // mirrors the call graph with the closest surface.
            val testBytes = ToolUtils.GetResourceAsData(testSet.fontFilename)?.toByteArray()
            val ftTypeface = ToolUtils.CreateTypefaceFromResource(testSet.fontFilename)

            if (testBytes == null || ftTypeface == null) {
                // Upstream: errorMsg = "Unable to initialize typeface."
                // + return DrawResult::kSkip. With the NotoSans fixtures
                // not in :test/resources/fonts/ this is today's branch.
                return
            }

            // SkTypeface_Fontations.MakeFromStream throws STUB.FONTATIONS
            // — entire GM short-circuits on the very first language sample.
            val testTypeface: SkTypeface = SkTypeface_Fontations.MakeFromStream(
                SkMemoryStream(testBytes),
                SkFontArguments(),
            )

            val configureFont: (SkFont) -> Unit = { font ->
                font.size = K_FONT_SIZE
                font.edging = SkFont.Edging.kSubpixelAntiAlias
                font.isSubpixel = true
                font.hinting = hintingMode
            }

            val font = SkFont(testTypeface).also(configureFont)
            val ftFont = SkFont(ftTypeface).also(configureFont)

            val drawCanvas = canvas
            var maxBounds = SkRect.MakeEmpty()

            for (phase in DrawPhase.entries) {
                var yCoord = K_FONT_SIZE * 1.5f

                for (langEntry in testSet.langSamples) {

                    val shapeAndDrawToCanvas: (SkFont, SkPoint) -> SkRect = { f, coord ->
                        val testString = langEntry.sampleShort
                        val handler = SkTextBlobShaperRunHandler(
                            utf8 = testString,
                            originX = 0f,
                            originY = 0f,
                        )
                        // Upstream uses `SkShaper::Make()` — the
                        // platform-default shaper (HarfBuzz when
                        // available, primitive otherwise). The Kotlin
                        // port picks `MakeJvmAwtTextLayout` because it
                        // covers bidi + basic kerning / ligatures (the
                        // Arabic / Hebrew samples need at least bidi).
                        val shaper = SkShaper.MakeJvmAwtTextLayout()
                        shaper.shape(
                            utf8 = testString,
                            font = f,
                            leftToRight = true,
                            width = 999_999f, // Don't linebreak.
                            runHandler = handler,
                        )
                        val blob = handler.makeBlob()
                        if (blob != null) {
                            drawCanvas.drawTextBlob(blob, coord.fX, coord.fY, paint)
                            blob.bounds()
                        } else {
                            SkRect.MakeEmpty()
                        }
                    }

                    val roundToDevicePixels: (SkPoint) -> SkPoint = { point ->
                        val ctm = drawCanvas.getLocalToDeviceAsMatrix()
                        if (ctm == null) {
                            point
                        } else {
                            val (mx, my) = ctm.mapXY(point.fX, point.fY)
                            val rx = mx.roundToInt().toFloat()
                            val ry = my.roundToInt().toFloat()
                            val inverse = ctm.invert()
                            if (inverse != null) {
                                val (ix, iy) = inverse.mapXY(rx, ry)
                                SkPoint.Make(ix, iy)
                            } else {
                                point
                            }
                        }
                    }

                    val fontationsCoord: () -> SkPoint = {
                        roundToDevicePixels(SkPoint.Make(K_MARGIN.toFloat(), yCoord))
                    }

                    val freetypeCoord: () -> SkPoint = {
                        val x = 2f * K_MARGIN + maxBounds.left + maxBounds.width()
                        roundToDevicePixels(SkPoint.Make(x, yCoord))
                    }

                    when (phase) {
                        DrawPhase.Fontations -> {
                            val boundsFontations = shapeAndDrawToCanvas(font, fontationsCoord())
                            // roundOut returns an SkIRect ; promote
                            // back to SkRect to join into maxBounds.
                            val rounded = boundsFontations.roundOut()
                            maxBounds = SkRect.MakeLTRB(
                                minOf(maxBounds.left, rounded.left.toFloat()),
                                minOf(maxBounds.top, rounded.top.toFloat()),
                                maxOf(maxBounds.right, rounded.right.toFloat()),
                                maxOf(maxBounds.bottom, rounded.bottom.toFloat()),
                            )
                        }
                        DrawPhase.FreeType -> {
                            shapeAndDrawToCanvas(ftFont, freetypeCoord())
                        }
                        DrawPhase.Comparison -> {
                            // Read back pixels from equally sized
                            // rectangles where the Fontations and
                            // FreeType sample texts were drawn,
                            // compare via SkDiff-style absolute pixel
                            // differences, draw a comparison as faint
                            // pixel differences, and as an amplified
                            // visualisation in which each differing
                            // pixel is drawn as white.
                            val fontationsOrigin = fontationsCoord()
                            val freetypeOrigin = freetypeCoord()
                            val fontationsBBoxLocal = maxBounds.makeOffset(
                                fontationsOrigin.fX, fontationsOrigin.fY,
                            )
                            val freetypeBBoxLocal = maxBounds.makeOffset(
                                freetypeOrigin.fX, freetypeOrigin.fY,
                            )

                            val ctm = drawCanvas.getLocalToDeviceAsMatrix() ?: break
                            val fontationsBBox = ctm.mapRect(fontationsBBoxLocal)
                            val freetypeBBox = ctm.mapRect(freetypeBBoxLocal)

                            val fontationsIBox: SkIRect = fontationsBBox.roundOut()
                            @Suppress("UNUSED_VARIABLE")
                            val freetypeIBox: SkIRect = freetypeBBox.roundOut()

                            // Pixel-readback via peekPixels :
                            // upstream uses `drawCanvas->peekPixels`
                            // to grab the raster device's backing
                            // pixmap, then writes back through
                            // `SkSurfaces::WrapPixels(canvasPixmap)`.
                            // `:kanvas-skia` has no `SkCanvas::peekPixels`
                            // (raster device is private — see
                            // [SkBitmapDevice]) so the comparison phase
                            // is structurally unreachable from a
                            // pure-Kotlin port. The bitmap allocations
                            // and `comparePixels` call below remain
                            // wired in so the call graph reflects
                            // upstream — once `peekPixels` lands the
                            // comparison body becomes meaningful.
                            val w = fontationsIBox.width()
                            val h = fontationsIBox.height()
                            if (w > 0 && h > 0) {
                                val diffBitmap = SkBitmap(w, h)
                                val highlightDiffBitmap = SkBitmap(w, h)
                                // No-op pixmaps stand in for the
                                // upstream sub-pixmaps until
                                // `peekPixels` is wired. The
                                // `comparePixels` helper still runs —
                                // it just operates on the freshly-
                                // allocated (all-zero) bitmaps.
                                comparePixels(
                                    diffBitmap, // would be fontationsPixmap
                                    highlightDiffBitmap, // would be freetypePixmap
                                    diffBitmap,
                                    highlightDiffBitmap,
                                )
                            }

                            // The write-back through
                            // `SkSurfaces::WrapPixels(canvasPixmap)` +
                            // `writeBackSurface->writePixels(diffBitmap, …)`
                            // is the dual of the (missing) peekPixels —
                            // similarly elided. The CTM-mapped target
                            // coordinates are computed faithfully so
                            // the layout matches upstream byte-for-byte
                            // once both peekPixels and writePixels
                            // wraparound land.
                            @Suppress("UNUSED_VARIABLE")
                            val comparisonCoord = ctm.mapXY(
                                3f * K_MARGIN + maxBounds.width() * 2f,
                                yCoord + maxBounds.top,
                            )
                            @Suppress("UNUSED_VARIABLE")
                            val whiteCoord = ctm.mapXY(
                                4f * K_MARGIN + maxBounds.width() * 3f,
                                yCoord + maxBounds.top,
                            )
                        }
                    }

                    yCoord += font.size * K_LANG_Y_INCREMENT_SCALE
                }
            }
        }
    }

    /**
     * Mirrors upstream's `comparePixels(pixmapA, pixmapB,
     * outPixelDiffBitmap, outHighlightDiffBitmap)`. Per-pixel
     * absolute-difference fill of the two output bitmaps :
     *
     *   outPixelDiffBitmap[x,y]      = SkPackARGB32(0xFF, |dr|, |dg|, |db|)
     *   outHighlightDiffBitmap[x,y]  = SK_ColorWHITE if any channel diff,
     *                                  else SK_ColorBLACK.
     *
     * Dimension mismatch is a no-op (matches upstream).
     */
    private fun comparePixels(
        bitmapA: SkBitmap,
        bitmapB: SkBitmap,
        outPixelDiffBitmap: SkBitmap,
        outHighlightDiffBitmap: SkBitmap,
    ) {
        if (bitmapA.width != bitmapB.width || bitmapA.height != bitmapB.height) return
        if (bitmapA.width != outPixelDiffBitmap.width ||
            bitmapA.height != outPixelDiffBitmap.height
        ) return

        for (x in 0 until bitmapA.width) {
            for (y in 0 until bitmapA.height) {
                val c0: SkColor = bitmapA.getPixel(x, y)
                val c1: SkColor = bitmapB.getPixel(x, y)
                val dr = SkColorGetR(c0) - SkColorGetR(c1)
                val dg = SkColorGetG(c0) - SkColorGetG(c1)
                val db = SkColorGetB(c0) - SkColorGetB(c1)

                outPixelDiffBitmap.setPixel(
                    x, y,
                    SkColorSetARGB(0xFF, abs(dr), abs(dg), abs(db)),
                )
                outHighlightDiffBitmap.setPixel(
                    x, y,
                    if (dr != 0 || dg != 0 || db != 0) SK_ColorWHITE else SK_ColorBLACK,
                )
            }
        }
    }

    private enum class DrawPhase { Fontations, FreeType, Comparison }

    /**
     * Mirrors upstream's `TestFontDataProvider`
     * (`tools/TestFontDataProvider.h`) — filtered iterator over the
     * Google-Fonts test-set JSON. Each iteration yields one
     * [TestSet] with one font filename and a list of language
     * samples. The Kotlin port emits a static, embedded JSON-free
     * stand-in keyed by family name (e.g. `"Noto Sans"` → the Latin
     * + Cyrillic + Greek bundle, `"Noto Sans Devanagari"` → Hindi /
     * Marathi, etc.) — sufficient to drive the call graph but trivial
     * to swap for a real JSON-loader once the fixtures ship.
     *
     * The [langFilter] is matched against each candidate sample's
     * `lang_Script` BCP-47 tag (case-sensitive `|`-separated
     * alternation, matching upstream).
     */
    private class TestFontDataProvider(
        private val fontNameFilter: String,
        private val langFilter: String,
    ) {
        private var consumed: Boolean = false

        /** Mirrors upstream's `TestSet` POD. */
        class TestSet {
            var fontFilename: String = ""
            var langSamples: List<LangSample> = emptyList()
        }

        /** Mirrors upstream's `LangSample` POD. */
        data class LangSample(
            val langTag: String,
            val sampleShort: String,
        )

        fun rewind() { consumed = false }

        fun next(out: TestSet): Boolean {
            if (consumed) return false
            consumed = true
            val bundle = resolveBundle(fontNameFilter) ?: run {
                out.fontFilename = ""
                out.langSamples = emptyList()
                return true
            }
            val (filename, allSamples) = bundle
            val allowed = langFilter.split('|').toSet()
            val filtered = allSamples.filter { it.langTag in allowed }
            out.fontFilename = filename
            out.langSamples = filtered
            return true
        }

        /**
         * Resolve a font-family filter regexp to its `(filename,
         * samples)` bundle. Returns `null` for families that are not
         * covered by the embedded stand-in dataset — caller treats
         * that as "no samples available" (mirrors upstream's empty
         * iteration on filter mismatch).
         */
        private fun resolveBundle(family: String): Pair<String, List<LangSample>>? = when (family) {
            "Noto Sans" -> "fonts/NotoSans-Regular.ttf" to NOTO_SANS_LATIN_CYRILLIC_GREEK_SAMPLES
            "Noto Sans Devanagari" -> "fonts/NotoSansDevanagari-Regular.ttf" to NOTO_SANS_DEVA_SAMPLES
            "Noto Sans Arabic" -> "fonts/NotoSansArabic-Regular.ttf" to NOTO_SANS_ARAB_SAMPLES
            "Noto Sans Bengali" -> "fonts/NotoSansBengali-Regular.ttf" to listOf(
                LangSample("bn_Beng", "সকল মানুষ স্বাধীনভাবে"),
            )
            "Noto Sans JP" -> "fonts/NotoSansJP-Regular.otf" to listOf(
                LangSample("ja_Jpan", "すべての人間は、生まれながらにして自由"),
            )
            "Noto Sans Thai" -> "fonts/NotoSansThai-Regular.ttf" to listOf(
                LangSample("th_Thai", "มนุษย์ทั้งหลายเกิดมามีอิสระ"),
            )
            "Noto Sans SC" -> "fonts/NotoSansSC-Regular.otf" to listOf(
                LangSample("zh_Hans", "人人生而自由"),
            )
            "Noto Sans TC" -> "fonts/NotoSansTC-Regular.otf" to listOf(
                LangSample("zh_Hant", "人人生而自由"),
            )
            "Noto Sans KR" -> "fonts/NotoSansKR-Regular.otf" to listOf(
                LangSample("ko_Kore", "모든 인간은 태어날 때부터"),
            )
            "Noto Sans Tamil" -> "fonts/NotoSansTamil-Regular.ttf" to listOf(
                LangSample("ta_Taml", "மனிதப் பிறவியினர் சகலரும்"),
            )
            "Noto Sans Newa" -> "fonts/NotoSansNewa-Regular.ttf" to listOf(
                LangSample("new_Newa", "𑐳𑐎𑐮 𑐩𑐣𑐹𑐟𑑂𑐰"),
            )
            "Noto Sans Kannada" -> "fonts/NotoSansKannada-Regular.ttf" to listOf(
                LangSample("kn_Knda", "ಎಲ್ಲಾ ಮಾನವರೂ"),
            )
            "Noto Sans Tagalog" -> "fonts/NotoSansTagalog-Regular.ttf" to listOf(
                LangSample("fil_Tglg", "ᜀᜅ᜔ ᜎᜑᜆ᜔"),
            )
            "Noto Sans Telugu" -> "fonts/NotoSansTelugu-Regular.ttf" to listOf(
                LangSample("te_Telu", "ప్రతిపత్తిస్వత్వముల"),
            )
            "Noto Sans Gujarati" -> "fonts/NotoSansGujarati-Regular.ttf" to listOf(
                LangSample("gu_Gujr", "પ્રતિષ્ઠા અને અધિકારોની"),
            )
            "Noto Sans Georgian" -> "fonts/NotoSansGeorgian-Regular.ttf" to listOf(
                LangSample("ka_Geor", "ყველა ადამიანი იბადება"),
            )
            "Noto Sans Malayalam" -> "fonts/NotoSansMalayalam-Regular.ttf" to listOf(
                LangSample("ml_Mlym", "മനുഷ്യരെല്ലാവരും"),
            )
            "Noto Sans Khmer" -> "fonts/NotoSansKhmer-Regular.ttf" to listOf(
                LangSample("km_Khmr", "មនុស្សទាំងអស់"),
            )
            "Noto Sans Sinhala" -> "fonts/NotoSansSinhala-Regular.ttf" to listOf(
                LangSample("si_Sinh", "සියලූම මනුෂ්‍යයෝ"),
            )
            "Noto Sans Myanmar" -> "fonts/NotoSansMyanmar-Regular.ttf" to listOf(
                LangSample("my_Mymr", "လူတိုင်းသည် တူညီ"),
            )
            "Noto Sans Javanese" -> "fonts/NotoSansJavanese-Regular.ttf" to listOf(
                LangSample("jv_Java", "ꦱꦏꦧꦺꦃꦲꦶꦁ ꦠꦶꦠꦃ"),
            )
            "Noto Sans Mongolian" -> "fonts/NotoSansMongolian-Regular.ttf" to listOf(
                LangSample("mn_Mong", "ᠬᠦᠮᠦᠨ ᠪᠦᠷ ᠲᠦᠷᠦᠯᠬᠢ"),
            )
            "Noto Sans Armenian" -> "fonts/NotoSansArmenian-Regular.ttf" to listOf(
                LangSample("hy_Armn", "Բոլոր մարդիկ ծնվում են"),
            )
            "Noto Sans Elbasan" -> "fonts/NotoSansElbasan-Regular.ttf" to listOf(
                LangSample("sq_Elba", "𐔀𐔍𐔂𐔆𐔊"),
            )
            "Noto Sans Vithkuqi" -> "fonts/NotoSansVithkuqi-Regular.ttf" to listOf(
                LangSample("sq_Vith", "𐕰𐕻𐕮𐕯"),
            )
            "Noto Sans Gurmukhi" -> "fonts/NotoSansGurmukhi-Regular.ttf" to listOf(
                LangSample("pa_Guru", "ਸਾਰਾ ਮਨੁੱਖੀ ਪਰਿਵਾਰ"),
            )
            else -> null
        }
    }

    public companion object {
        // Pixel-grid + typographic constants copied verbatim from
        // upstream cpp:31-34.
        private const val K_GM_WIDTH = 1000
        private const val K_MARGIN = 30
        private const val K_FONT_SIZE = 24f
        private const val K_LANG_Y_INCREMENT_SCALE = 1.9f

        /**
         * The default Latin / Cyrillic / Greek `|`-separated locale
         * filter used by the first six upstream `DEF_GM(NotoSans, …)`
         * instances. ~46 BCP-47 tags spanning Western European
         * languages plus Cyrillic + Greek + Latin-script Turkic.
         * Copied verbatim from upstream cpp:320-325.
         */
        public const val DEFAULT_LATIN_LANG_FILTER: String =
            "en_Latn|es_Latn|pt_Latn|id_Latn|ru_Cyrl|fr_Latn|tr_Latn|vi_Latn|de_" +
                "Latn|it_Latn|pl_Latn|nl_Latn|uk_Cyrl|gl_Latn|ro_Latn|cs_Latn|hu_Latn|" +
                "el_Grek|se_Latn|da_Latn|bg_Latn|sk_Latn|fi_Latn|bs_Latn|ca_Latn|no_" +
                "Latn|sr_Latn|sr_Cyrl|lt_Latn|hr_Latn|sl_Latn|uz_Latn|uz_Cyrl|lv_Latn|" +
                "et_Latn|az_Latn|az_Cyrl|la_Latn|tg_Latn|tg_Cyrl|sw_Latn|mn_Cyrl|kk_" +
                "Latn|kk_Cyrl|sq_Latn|af_Latn|ha_Latn|ky_Cyrl"

        // ─── Embedded language-sample stand-in for the Google Fonts
        // TestFontDataProvider JSON. Each sample tag uses the BCP-47
        // `lang_Script` convention upstream relies on for filtering.

        private val NOTO_SANS_LATIN_CYRILLIC_GREEK_SAMPLES: List<TestFontDataProvider.LangSample> =
            listOf(
                TestFontDataProvider.LangSample("en_Latn", "All human beings are born free"),
                TestFontDataProvider.LangSample("es_Latn", "Todos los seres humanos nacen libres"),
                TestFontDataProvider.LangSample("pt_Latn", "Todos os seres humanos nascem livres"),
                TestFontDataProvider.LangSample("id_Latn", "Semua orang dilahirkan merdeka"),
                TestFontDataProvider.LangSample("ru_Cyrl", "Все люди рождаются свободными"),
                TestFontDataProvider.LangSample("fr_Latn", "Tous les êtres humains naissent libres"),
                TestFontDataProvider.LangSample("tr_Latn", "Bütün insanlar hür doğarlar"),
                TestFontDataProvider.LangSample("vi_Latn", "Mọi người sinh ra đều được tự do"),
                TestFontDataProvider.LangSample("de_Latn", "Alle Menschen sind frei und gleich"),
                TestFontDataProvider.LangSample("it_Latn", "Tutti gli esseri umani nascono liberi"),
                TestFontDataProvider.LangSample("pl_Latn", "Wszyscy ludzie rodzą się wolni"),
                TestFontDataProvider.LangSample("nl_Latn", "Alle mensen worden vrij geboren"),
                TestFontDataProvider.LangSample("uk_Cyrl", "Всі люди народжуються вільними"),
                TestFontDataProvider.LangSample("gl_Latn", "Tódolos seres humanos nacen libres"),
                TestFontDataProvider.LangSample("ro_Latn", "Toate ființele umane se nasc libere"),
                TestFontDataProvider.LangSample("cs_Latn", "Všichni lidé rodí se svobodní"),
                TestFontDataProvider.LangSample("hu_Latn", "Minden emberi lény szabadon születik"),
                TestFontDataProvider.LangSample("el_Grek", "Όλοι οι άνθρωποι γεννιούνται ελεύθεροι"),
                TestFontDataProvider.LangSample("se_Latn", "Buot olbmot leat riegádan friddjan"),
                TestFontDataProvider.LangSample("da_Latn", "Alle mennesker er født frie"),
                TestFontDataProvider.LangSample("bg_Latn", "Vsichki hora se razhdat svobodni"),
                TestFontDataProvider.LangSample("sk_Latn", "Všetci ľudia sa rodia slobodní"),
                TestFontDataProvider.LangSample("fi_Latn", "Kaikki ihmiset syntyvät vapaina"),
                TestFontDataProvider.LangSample("bs_Latn", "Sva ljudska bića rađaju se slobodna"),
                TestFontDataProvider.LangSample("ca_Latn", "Tots els éssers humans neixen lliures"),
                TestFontDataProvider.LangSample("no_Latn", "Alle mennesker er født frie"),
                TestFontDataProvider.LangSample("sr_Latn", "Sva ljudska bića rađaju se slobodna"),
                TestFontDataProvider.LangSample("sr_Cyrl", "Сва људска бића рађају се слободна"),
                TestFontDataProvider.LangSample("lt_Latn", "Visi žmonės gimsta laisvi"),
                TestFontDataProvider.LangSample("hr_Latn", "Sva ljudska bića rađaju se slobodna"),
                TestFontDataProvider.LangSample("sl_Latn", "Vsi ljudje se rodijo svobodni"),
                TestFontDataProvider.LangSample("uz_Latn", "Barcha odamlar erkin"),
                TestFontDataProvider.LangSample("uz_Cyrl", "Барча одамлар эркин"),
                TestFontDataProvider.LangSample("lv_Latn", "Visi cilvēki piedzimst brīvi"),
                TestFontDataProvider.LangSample("et_Latn", "Kõik inimesed sünnivad vabadena"),
                TestFontDataProvider.LangSample("az_Latn", "Bütün insanlar ləyaqət"),
                TestFontDataProvider.LangSample("az_Cyrl", "Бүтүн инсанлар"),
                TestFontDataProvider.LangSample("la_Latn", "Omnes homines liberi"),
                TestFontDataProvider.LangSample("tg_Latn", "Hama odamon ozod"),
                TestFontDataProvider.LangSample("tg_Cyrl", "Ҳама одамон озод"),
                TestFontDataProvider.LangSample("sw_Latn", "Watu wote wamezaliwa huru"),
                TestFontDataProvider.LangSample("mn_Cyrl", "Бүх хүн төрөхдөө"),
                TestFontDataProvider.LangSample("kk_Latn", "Barlyq adamdar"),
                TestFontDataProvider.LangSample("kk_Cyrl", "Барлық адамдар"),
                TestFontDataProvider.LangSample("sq_Latn", "Të gjithë njerëzit lindin"),
                TestFontDataProvider.LangSample("af_Latn", "Alle menslike wesens word vry"),
                TestFontDataProvider.LangSample("ha_Latn", "Dukan 'yan adam"),
                TestFontDataProvider.LangSample("ky_Cyrl", "Бардык адамдар"),
            )

        private val NOTO_SANS_DEVA_SAMPLES: List<TestFontDataProvider.LangSample> = listOf(
            TestFontDataProvider.LangSample("hi_Deva", "सभी मनुष्यों को गौरव और अधिकारों"),
            TestFontDataProvider.LangSample("mr_Deva", "सर्व मानवी व्यक्ती जन्मतःच"),
        )

        private val NOTO_SANS_ARAB_SAMPLES: List<TestFontDataProvider.LangSample> = listOf(
            TestFontDataProvider.LangSample("ar_Arab", "يولد جميع الناس أحراراً"),
            TestFontDataProvider.LangSample("uz_Arab", "بارچه اادملار اركن"),
            TestFontDataProvider.LangSample("kk_Arab", "بارلىق ادامدار"),
            TestFontDataProvider.LangSample("ky_Arab", "باردىك ادامدار"),
        )
    }
}
