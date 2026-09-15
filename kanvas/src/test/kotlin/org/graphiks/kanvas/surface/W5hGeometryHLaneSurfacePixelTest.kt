@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertEquals

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.types.PointMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Public H04/H08/H12–14/H17–19/H22–24/H28–30; geometry never supplies the oracle. */
class W5hGeometryHLaneSurfacePixelTest {
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointGradients")
    fun pointsFourGradientsCaptureBlend(lane: String, variant: String) =
        captureBlend(lane, "gradient/$variant") { gradientFixture(variant) }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointAddressing")
    fun pointsAddressingCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "address/$variant") { addressingFixture(variant) }
        if (variant == "tile-DECAL") captureBlend(lane, "address/tile-DECAL-outside") { addressingFixture("tile-DECAL-outside") }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("rrectImages")
    fun rrectImageSampleCaptureBlend(lane: String, variant: String) = captureBlend(lane, "image/$variant") { imageFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("strokeImages")
    fun strokeImageSampleCaptureBlend(lane: String, variant: String) = captureBlend(lane, "image/$variant") { imageFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointImages")
    fun pointsImageSampleCaptureBlend(lane: String, variant: String) = captureBlend(lane, "image/$variant") { imageFixture(variant) }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("rrectFilters")
    fun rrectColorFiltersCaptureBlend(lane: String, variant: String) = captureBlend(lane, "filter/$variant") { filterFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("strokeFilters")
    fun strokeColorFiltersCaptureBlend(lane: String, variant: String) = captureBlend(lane, "filter/$variant") { filterFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointFilters")
    fun pointsColorFiltersCaptureBlend(lane: String, variant: String) = captureBlend(lane, "filter/$variant") { filterFixture(variant) }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("rrectProcedural")
    fun rrectBlendNoiseCaptureBlend(lane: String, variant: String) = captureBlend(lane, "procedural/$variant") { proceduralFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("strokeProcedural")
    fun strokeBlendNoiseCaptureBlend(lane: String, variant: String) = captureBlend(lane, "procedural/$variant") { proceduralFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointProcedural")
    fun pointsBlendNoiseCaptureBlend(lane: String, variant: String) = captureBlend(lane, "procedural/$variant") { proceduralFixture(variant) }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("rrectRuntime")
    fun rrectRuntimeCaptureBlend(lane: String, variant: String) = captureBlend(lane, "runtime/$variant") { runtimeFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("strokeRuntime")
    fun strokeRuntimeCaptureBlend(lane: String, variant: String) = captureBlend(lane, "runtime/$variant") { runtimeFixture(variant) }
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("pointRuntime")
    fun pointsRuntimeCaptureBlend(lane: String, variant: String) = captureBlend(lane, "runtime/$variant") { runtimeFixture(variant) }

    /** A captured alias, dropped paint alpha, wrong child or lost final blend changes this pixel. */
    private fun captureBlend(lane: String, fixtureKey: String, make: () -> Fixture) {
        val prepared = FINALS.map { mode ->
            val fixture = make()
            val witness = EXPECTATIONS.getOrPut("$fixtureKey/$mode") {
            val expected = W5fColorCpuOracle.capturedShaderTree(fixture.oracle, PAINT_ALPHA, fixture.external,
                destinationBlend = BlendMode.SRC)
            val mutated = W5fColorCpuOracle.capturedShaderTree(fixture.changedOracle, PAINT_ALPHA, fixture.changedExternal,
                destinationBlend = BlendMode.SRC)
            // Oracle-only fixture selection precedes every public capture/native action. Neither
            // the candidate set nor its strict two-adjacent-code gate consults rendered pixels.
            var lastResult = ""
            val witness = DESTINATIONS.firstNotNullOfOrNull { destination ->
                val wanted = runCatching { expected(destination,mode) }.getOrNull()
                val changed = runCatching { mutated(destination,mode) }.getOrNull()
                lastResult = "$wanted / $changed"
                if (wanted is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    changed is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    wanted.channels.indices.any { wanted.channels[it].intersect(changed.channels[it]).isEmpty() })
                    destination to wanted else null
            }
            assertNotNull(witness, "No bounded independent destination for $fixtureKey/$mode: $lastResult")
            }
            Triple(mode, fixture, witness)
        }
        for ((mode, fixture, witness) in prepared) {
            val (destination,wanted) = witness
            val runtimeIdeal = if (mode == BlendMode.SRC_IN && fixtureKey in listOf("runtime/child", "runtime/nested")) {
                val child = W5hRuntimeEffectCpuOracle.linearPremul(SOURCE)
                val nested = W5hRuntimeEffectCpuOracle.opacity(child, if (fixtureKey == "runtime/nested") .75 else 1.0)
                val runtime = W5hRuntimeEffectCpuOracle.opacity(nested, .5)
                val painted = W5hRuntimeEffectCpuOracle.opacity(runtime, PAINT_ALPHA.toDouble())
                W5hRuntimeEffectCpuOracle.attachment(W5hRuntimeEffectCpuOracle.opacity(painted, destination.alpha / 255.0))
            } else null
            fun assertCaptured(result: RenderResult) {
                assertLanePixels(lane, result, wanted)
                runtimeIdeal?.forEachIndexed { channel, codes ->
                    assertTrue(result.pixels[channel].toInt() in codes, "Task 3 runtime contract, channel $channel: $codes")
                }
            }
            val paint = Paint(color = ColorARGB.of(149, 255, 255, 255), shader = fixture.shader,
                colorFilter = fixture.external, blendMode = mode, antiAlias = false)
            val surface = Surface(1, 1)
            surface.canvas { background(destination); drawLane(lane, paint) }
            val recorder = PictureRecorder()
            recorder.beginRecording(UNIT).apply { background(destination); drawLane(lane, paint) }
            val picture = recorder.finishRecordingAsPicture()
            fixture.mutate()
            repeat(2) { assertCaptured(surface.render()) }
            for (replay in listOf(picture, assertNotNull(Picture.fromByteArray(picture.toByteArray())))) {
                val target = Surface(1, 1)
                target.canvas { replay.playback(this) }
                repeat(2) { assertCaptured(target.render()) }
            }
        }
    }

    private fun assertLanePixels(lane: String, result: RenderResult, wanted: WgslFloatEnvelopeV1Oracle.DrawResult) {
        // Pixels first expose causal material errors even on the baseline's legacy route.
        assertEquals(4, result.pixels.size)
        WgslFloatEnvelopeV1Oracle.assertAdmits(wanted, result.pixels)
        // Prepared Point(s) does not publish the plan route's native scope labels.
        if (lane !in listOf("DrawPoint", "POINTS"))
            W5fSurfacePixelFixtures.assertNativePixels(result, listOf(wanted))
    }

    private fun Canvas.background(destination: ColorARGB = DESTINATION) =
        drawRect(UNIT, Paint(color = destination, blendMode = BlendMode.SRC, antiAlias = false))

    private fun Canvas.drawLane(lane: String, paint: Paint) {
        when (lane) {
            "RRect" -> drawRRect(RRectF32.of(RectF32.ofLTRB(-2f, -2f, 3f, 3f), CornerRadiiF32.of(1f)),
                paint.copy(antiAlias = true))
            "Stroke", "Hairline" -> drawPath(Path().apply { moveTo(-2f, .5f); lineTo(3f, .5f) },
                paint.copy(style = PaintStyle.STROKE, strokeWidth = if (lane == "Hairline") 0f else 2f))
            "DrawPoint" -> drawPoint(.5f, .5f, paint.copy(strokeWidth = 0f))
            "POINTS" -> drawPoints(PointMode.POINTS, listOf(Point2F32(.5f, .5f), Point2F32(4f, .5f)),
                paint.copy(strokeWidth = 0f))
            "LINES" -> drawPoints(PointMode.LINES, listOf(Point2F32(-2f, .5f), Point2F32(3f, .5f)),
                paint.copy(style = PaintStyle.STROKE, strokeWidth = 2f))
            "POLYGON" -> drawPoints(PointMode.POLYGON,
                listOf(Point2F32(-2f, .5f), Point2F32(3f, .5f), Point2F32(3f, 3f)),
                paint.copy(style = PaintStyle.STROKE, strokeWidth = 2f))
            else -> error(lane)
        }
    }

    private class Fixture(val shader: Shader, val oracle: Shader = shader, val changedOracle: Shader,
        val external: ColorFilter? = null, val changedExternal: ColorFilter? = external, val mutate: () -> Unit)

    private fun gradientFixture(variant: String): Fixture {
        val (family, size) = variant.split('/')
        val stops = when (size) {
            "duplicate" -> mutableListOf(GradientStop(0f, SOURCE), GradientStop(.5f, SOURCE),
                GradientStop(.5f, SECOND), GradientStop(1f, SECOND))
            "1" -> mutableListOf(GradientStop(.25f, SOURCE))
            else -> MutableList(size.toInt()) { i -> GradientStop(i.toFloat() / (size.toInt() - 1),
                if (i < size.toInt() / 2) SOURCE else SECOND) }
        }
        val original = gradient(family, stops)
        val changed = gradient(family, listOf(GradientStop(0f, MUTATED), GradientStop(1f, MUTATED)))
        return Fixture(original, changedOracle = changed) {
            stops.replaceAll { it.copy(position = if (it.position < .75f) 0f else 1f, color = MUTATED) }
        }
    }

    private fun gradient(family: String, stops: List<GradientStop>, tile: TileMode = TileMode.CLAMP): Shader =
        when (family) {
            "linear" -> Shader.LinearGradient(Point2F32(0f, .5f), Point2F32(2f, .5f), stops, tileMode = tile)
            "radial" -> Shader.RadialGradient(Point2F32(-.5f, .5f), 4f, stops, tileMode = tile)
            "sweep" -> Shader.SweepGradient(Point2F32(-.5f, -.5f), 0f, 360f, stops, tileMode = tile)
            "conical" -> Shader.ConicalGradient(Point2F32(-.5f, .5f), 0f, Point2F32(-.5f, .5f), 4f, stops, tileMode = tile)
            else -> error(family)
        }

    private fun addressingFixture(variant: String): Fixture {
        val stops = mutableListOf(GradientStop(0f, SOURCE), GradientStop(.5f, SOURCE),
            GradientStop(.5f, SECOND), GradientStop(1f, SECOND))
        fun wrap(values: List<GradientStop>): Shader {
            val tile = if (variant.startsWith("tile-")) TileMode.valueOf(variant.removePrefix("tile-").removeSuffix("-outside")) else TileMode.CLAMP
            val leaf = gradient("linear", values, tile)
            return when (variant) {
                "identity" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32())
                "translation" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-1f, 0f))
                "uniform-scale" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(.25f, .25f))
                "nonuniform-scale" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(.25f, 2f))
                "rotation" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f, tx = 1f, ty = -1f))
                "shear" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(kx = -2f))
                "reflection" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(sx = -1f, tx = 2f))
                "projective" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(persp0 = .125f, sx = .5f))
                "clamp" -> Shader.CoordClamp(leaf, RectF32.ofLTRB(1.25f, 2f, 1.75f, 3f))
                "matrix-clamp" -> Shader.WithLocalMatrix(Shader.CoordClamp(leaf,
                    RectF32.ofLTRB(1.25f, 2f, 1.75f, 3f)), Matrix3x3F32.translation(-4f, -8f))
                "clamp-matrix" -> Shader.CoordClamp(Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-1f, 0f)),
                    RectF32.ofLTRB(-.25f, 2f, .25f, 3f))
                "tile-DECAL" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-1.25f, 0f))
                else -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-2.25f, 0f))
            }
        }
        // The exterior DECAL pixel additionally rejects CLAMP substitution; its companion interior
        // capture above proves that mutating the same stop storage does not affect replay.
        val counterfactual = if (variant == "tile-DECAL-outside") Shader.WithLocalMatrix(
            gradient("linear", stops, TileMode.CLAMP), Matrix3x3F32.translation(-2.25f, 0f))
        else wrap(listOf(GradientStop(0f, MUTATED), GradientStop(1f, MUTATED)))
        // A post-source bias makes the exterior DECAL/CLAMP distinction observable without
        // an unbounded quantized destination decode/re-encode identity fixture.
        val exteriorFilter = if (variant == "tile-DECAL-outside") ColorFilter.Matrix(noiseMarker()) else null
        return Fixture(wrap(stops), changedOracle = counterfactual, external = exteriorFilter) {
            stops.replaceAll { it.copy(position = if (it.position < .75f) 0f else 1f, color = MUTATED) }
        }
    }

    private fun imageFixture(variant: String): Fixture {
        val (format, sampler) = variant.split('/')
        val mask = format == "A8"
        val bytes = if (mask) byteArrayOf(64, -64) else byteArrayOf(-1, 0, 0, -128, 0, 0, -1, -128)
        val type = if (mask) ColorType.ALPHA_8 else ColorType.RGBA_8888
        val sampling = when (sampler) { "nearest" -> SamplingOptions.NEAREST; "linear" -> SamplingOptions.LINEAR
            else -> SamplingOptions.Cubic.CatmullRom }
        fun shader(pixels: ByteArray): Shader = Shader.WithLocalMatrix(
            Shader.Image(Image.fromPixels(2, 1, pixels, type, alphaType = AlphaType.UNPREMUL), sampling = sampling),
            Matrix3x3F32.translation(-.25f, 0f))
        val changed = if (mask) byteArrayOf(-33, -33) else byteArrayOf(96, -32, 64, -33, 96, -32, 64, -33)
        return Fixture(shader(bytes), changedOracle = shader(changed)) { changed.copyInto(bytes) }
    }

    private fun filterFixture(variant: String): Fixture {
        fun markerMatrix(redBias: Float) = ColorMatrixF32.ofIdentity().apply {
            setScale(.25f, .25f, .25f, .5f)
            postTranslate(redBias, .125f, .125f, .125f)
        }
        val redBias = if (variant == "Blend-CLEAR") .25f else .125f
        val marker = markerMatrix(redBias)
        val scale = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f, .75f, .25f, .75f) })
        val translated = ColorFilter.Matrix(marker)
        val kind: ColorFilter = when {
            variant.startsWith("Blend-") -> ColorFilter.Blend(ColorARGB.of(127, 96, 144, 192),
                BlendMode.valueOf(variant.removePrefix("Blend-")))
            variant == "Matrix" -> scale
            variant == "Compose" -> ColorFilter.Compose(scale, translated)
            variant == "Compose-reversed" -> ColorFilter.Compose(translated, scale)
            variant.startsWith("Lerp-") -> ColorFilter.Lerp(variant.removePrefix("Lerp-").toFloat(),
                ColorFilter.Compose(scale, translated), ColorFilter.Compose(translated, scale))
            variant == "Table" -> ColorFilter.Table(UByteArray(256) { (255 - it).toUByte() })
            variant == "Lighting" -> ColorFilter.Lighting(ColorARGB.of(0, 128, 255, 0), ColorARGB.of(19, 64, 0, 255))
            variant == "SRGBToLinear" -> ColorFilter.SRGBToLinear
            variant == "LinearToSRGB" -> ColorFilter.LinearToSRGB
            variant == "HSLAMatrix" -> ColorFilter.HSLAMatrix(floatArrayOf(1f,0f,0f,0f,.25f,
                0f,.5f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,.5f,0f))
            variant == "HighContrast" -> ColorFilter.HighContrast
            variant == "Luma" -> ColorFilter.Luma
            variant == "Overdraw" -> ColorFilter.Overdraw
            else -> scale
        }
        val leaf = Shader.SolidColor(SOURCE)
        val shader = when (variant) {
            "filter-before-opacity" -> Shader.Opacity(Shader.WithColorFilter(leaf, translated), .5f)
            "filter-after-opacity" -> Shader.WithColorFilter(Shader.Opacity(leaf, .5f), translated)
            "nested-opacity-filters" -> Shader.WithColorFilter(Shader.Opacity(
                Shader.WithColorFilter(Shader.Opacity(leaf, .25f), translated), .5f), scale)
            else -> leaf
        }
        val external = ColorFilter.Compose(translated, kind)
        val changed = ColorFilter.Compose(ColorFilter.Matrix(markerMatrix(redBias + .5f)), kind)
        return Fixture(shader, changedOracle = shader, external = external, changedExternal = changed) {
            marker.postTranslate(.5f, 0f, 0f, 0f)
        }
    }

    private fun proceduralFixture(variant: String): Fixture {
        fun family(changed: Boolean): Shader = when (variant) {
            "Blend-shared-ordered" -> {
                val shared = Shader.SolidColor(SOURCE)
                val filtered = Shader.WithColorFilter(shared, ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply {
                    setScale(.5f, 1f, 1f, 1f)
                }))
                if (changed) Shader.Blend(BlendMode.SRC_OVER, shared, filtered)
                else Shader.Blend(BlendMode.SRC_OVER, filtered, shared)
            }
            "Perlin" -> Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(SOURCE.withAlpha(127)),
                Shader.PerlinNoise(.125f, .25f, 2, if (changed) 19 else 7, null))
            else -> Shader.FractalNoise(.125f, .25f, 2, if (changed) 19 else 7, null)
        }
        val original = family(false)
        val changed = family(true)
        val children = linkedMapOf("child" to original)
        return Fixture(runtime(children, 1f), original, changed) { children["child"] = changed }
    }

    private fun noiseMarker() = ColorMatrixF32.ofIdentity().apply {
        setScale(.25f, .25f, .25f, .5f)
        postTranslate(.125f, .125f, .125f, .25f)
    }

    private fun runtimeFixture(variant: String): Fixture {
        val child = when (variant) {
            "image-child" -> imageFixture("RGBA/nearest")
            "stops-child" -> gradientFixture("linear/17")
            "noise-child" -> {
                val matrix = noiseMarker()
                val shader = Shader.WithColorFilter(Shader.FractalNoise(.125f, .25f, 2, 7, null), ColorFilter.Matrix(matrix))
                Fixture(shader, changedOracle = Shader.SolidColor(ColorARGB.Green)) { matrix.setScale(0f, 0f, 0f, 0f) }
            }
            else -> {
                val matrix = ColorMatrixF32.ofIdentity()
                val shader = Shader.WithColorFilter(Shader.SolidColor(SOURCE), ColorFilter.Matrix(matrix))
                Fixture(shader, changedOracle = Shader.SolidColor(ColorARGB.Green)) { matrix.setScale(0f, 0f, 0f, 0f) }
            }
        }
        val nested = variant == "nested"
        val inner = linkedMapOf("child" to child.shader)
        val children = linkedMapOf("child" to if (nested) runtime(inner, .75f) else child.shader)
        val oracle = Shader.Opacity(if (nested) Shader.Opacity(child.oracle, .75f) else child.oracle, .5f)
        // Task 3's independent opacity contract is multiplication in LINEAR_PREMUL.
        // W5f's independent opacity interpreter supplies its directed F32 envelope and final blend.
        return Fixture(runtime(children, .5f), oracle, Shader.Opacity(Shader.SolidColor(MUTATED), .5f)) {
            children["child"] = Shader.SolidColor(MUTATED)
            inner["child"] = Shader.SolidColor(ColorARGB.Blue)
            child.mutate()
        }
    }

    private fun runtime(children: Map<String, Shader>, alpha: Float): Shader = Shader.RuntimeEffect(
        assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1)),
        UniformBlock { float1("alpha", alpha) }, children)

    @ParameterizedTest(name = "same Surface recovery: {0}") @MethodSource("allLanes")
    fun materialRefusalThenSameSurfaceRecovers(lane: String) {
        val valid = runtimeFixture("child")
        val wanted = W5fColorCpuOracle.expectedShaderTree(valid.oracle, PAINT_ALPHA, destination = DESTINATION,
            finalBlend = BlendMode.SRC_IN, destinationBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val surface = Surface(1, 1)
        val failure = assertFailsWith<IllegalArgumentException> {
            surface.canvas { drawLane(lane, Paint(shader = Shader.WithColorFilter(Shader.SolidColor(SOURCE),
                ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(Float.NaN, 0f, 0f, 0f) })))) }
        }
        assertTrue(failure.message.orEmpty().contains("non-finite-value"), failure.message)
        surface.canvas { background(); drawLane(lane, Paint(color = ColorARGB.of(149, 255, 255, 255),
            shader = valid.shader, blendMode = BlendMode.SRC_IN, antiAlias = false)) }
        repeat(2) { assertLanePixels(lane, surface.render(), wanted) }
    }

    companion object {
        private val UNIT = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        private val SOURCE = ColorARGB.of(191, 224, 64, 128)
        private val SECOND = ColorARGB.of(191, 64, 128, 224)
        private val MUTATED = ColorARGB.of(223, 96, 224, 64)
        private val DESTINATION = ColorARGB.of(127, 16, 0, 0)
        private val DESTINATIONS = listOf(DESTINATION) + listOf(1, 2, 3, 4, 5, 7, 15, 31, 63, 127, 191).flatMap { alpha ->
            listOf(1, 2, 4, 8, 10, 16, 32, 72, 128, 224, 255).map { red -> ColorARGB.of(alpha, red, 0, 0) }
        }
        // Same numerical fixture across seven geometry lanes; cache only immutable oracle values.
        private val EXPECTATIONS = mutableMapOf<String, Pair<ColorARGB, WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>>()
        private const val PAINT_ALPHA = 149f / 255f
        private val FINALS = listOf(BlendMode.SRC_IN, BlendMode.DIFFERENCE)
        private val POINTS = listOf("DrawPoint", "POINTS", "LINES", "POLYGON")
        private val STROKES = listOf("Stroke", "Hairline")
        private val RRECT = listOf("RRect")
        private val IMAGES = listOf("RGBA", "A8").flatMap { format -> listOf("nearest", "linear", "cubic").map { "$format/$it" } }
        private val FILTERS = listOf("Matrix", "Compose", "Compose-reversed", "Lerp-0", "Lerp-0.25", "Lerp-0.5", "Lerp-1",
            "Table", "Lighting", "SRGBToLinear", "LinearToSRGB", "HSLAMatrix", "HighContrast", "Luma", "Overdraw",
            "filter-before-opacity", "filter-after-opacity", "nested-opacity-filters") + BlendMode.entries.map { "Blend-$it" }
        private val PROCEDURAL = listOf("Blend-shared-ordered", "Perlin", "Fractal")
        private val RUNTIME = listOf("child", "nested", "image-child", "stops-child", "noise-child")
        private fun cases(lanes: List<String>, variants: List<String>) = lanes.flatMap { lane -> variants.map { Arguments.of(lane, it) } }
        @JvmStatic fun pointGradients() = cases(POINTS, listOf("linear", "radial", "sweep", "conical").flatMap { family ->
            listOf("1", "2", "16", "17", "duplicate").map { "$family/$it" } })
        @JvmStatic fun pointAddressing() = cases(POINTS, listOf("identity", "translation", "uniform-scale", "nonuniform-scale",
            "rotation", "shear", "reflection", "projective", "clamp", "matrix-clamp", "clamp-matrix") + TileMode.entries.map { "tile-$it" })
        @JvmStatic fun rrectImages() = cases(RRECT, IMAGES)
        @JvmStatic fun strokeImages() = cases(STROKES, IMAGES)
        @JvmStatic fun pointImages() = cases(POINTS, IMAGES)
        @JvmStatic fun rrectFilters() = cases(RRECT, FILTERS)
        @JvmStatic fun strokeFilters() = cases(STROKES, FILTERS)
        @JvmStatic fun pointFilters() = cases(POINTS, FILTERS)
        @JvmStatic fun rrectProcedural() = cases(RRECT, PROCEDURAL)
        @JvmStatic fun strokeProcedural() = cases(STROKES, PROCEDURAL)
        @JvmStatic fun pointProcedural() = cases(POINTS, PROCEDURAL)
        @JvmStatic fun rrectRuntime() = cases(RRECT, RUNTIME)
        @JvmStatic fun strokeRuntime() = cases(STROKES, RUNTIME)
        @JvmStatic fun pointRuntime() = cases(POINTS, RUNTIME)
        @JvmStatic fun allLanes() = RRECT + STROKES + POINTS
    }
}
