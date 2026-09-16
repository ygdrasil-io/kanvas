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
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.MeshChildren
import org.graphiks.kanvas.paint.ShaderChild
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.surface.gpu.GPUPreparedTextTestFixtures
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.pipeline.ClipOp
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

/** Public Task 5 material witnesses; the synthetic text fixture supplies coverage only. */
class W5hTextVerticesSurfacePixelTest {
    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textGradients")
    fun textFourGradientsCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "gradient/$variant") { gradientFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshGradients")
    fun verticesMeshFourGradientsCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "gradient/$variant") { gradientFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textAddressing")
    fun textAddressingCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "address/$variant") { addressingFixture(variant) }
        if (variant == "tile-DECAL") captureBlend(lane, "address/tile-DECAL-outside") { addressingFixture("tile-DECAL-outside") }
        if (variant == "tile-MIRROR") captureBlend(lane, "address/tile-MIRROR-distinct") { addressingFixture("tile-MIRROR-distinct") }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshAddressing")
    fun verticesMeshAddressingCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "address/$variant") { addressingFixture(variant) }
        if (variant == "tile-DECAL") captureBlend(lane, "address/tile-DECAL-outside") { addressingFixture("tile-DECAL-outside") }
        if (variant == "tile-MIRROR") captureBlend(lane, "address/tile-MIRROR-distinct") { addressingFixture("tile-MIRROR-distinct") }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textImages")
    fun textImageSampleCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "image/$variant") { imageFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshImages")
    fun verticesMeshImageSampleCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "image/$variant") { imageFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textFilters")
    fun textColorFiltersCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "filter/$variant") { filterFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshFilters")
    fun verticesMeshColorFiltersCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "filter/$variant") { filterFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textProcedural")
    fun textBlendNoiseCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "procedural/$variant") { proceduralFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshProcedural")
    fun verticesMeshBlendNoiseCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "procedural/$variant") { proceduralFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("textRuntime")
    fun textRuntimeCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "runtime/$variant") { runtimeFixture(variant) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("verticesMeshRuntime")
    fun verticesMeshRuntimeCaptureBlend(lane: String, variant: String) {
        captureBlend(lane, "runtime/$variant") { runtimeFixture(variant) }
    }

    /** A captured alias, dropped paint alpha, wrong child or lost final blend changes this pixel. */
    private fun captureBlend(lane: String, fixtureKey: String, make: () -> Fixture) {
        val prepared = FINALS.map { mode ->
            val fixture = make()
            val witness = EXPECTATIONS.getOrPut("${lane == "Vertices-colored"}/$fixtureKey/$mode") {
            val expected = W5fColorCpuOracle.capturedShaderTree(vertexSource(lane, fixture.oracle), PAINT_ALPHA, fixture.external,
                destinationBlend = BlendMode.SRC)
            val mutated = W5fColorCpuOracle.capturedShaderTree(vertexSource(lane, fixture.changedOracle), PAINT_ALPHA, fixture.changedExternal,
                destinationBlend = BlendMode.SRC)
            val addressing = fixture.addressingOracle?.let { W5fColorCpuOracle.capturedShaderTree(vertexSource(lane, it), PAINT_ALPHA,
                fixture.external, destinationBlend = BlendMode.SRC) }
            val reversedPrimitive = if (lane == "Vertices-colored")
                W5fColorCpuOracle.capturedShaderTree(Shader.Blend(BlendMode.SRC_ATOP,
                    fixture.oracle, Shader.SolidColor(VERTEX_COLOR)), PAINT_ALPHA, fixture.external,
                    destinationBlend = BlendMode.SRC) else null
            val substitutedVertex = if (lane == "Vertices-colored")
                W5fColorCpuOracle.capturedShaderTree(Shader.Blend(BlendMode.SRC_ATOP,
                    Shader.SolidColor(ColorARGB.White), fixture.oracle), PAINT_ALPHA, fixture.external,
                    destinationBlend = BlendMode.SRC) else null
            // Oracle-only fixture selection precedes every public capture/native action. Neither
            // the candidate set nor its strict two-adjacent-code gate consults rendered pixels.
            var lastResult = ""
            val witness = DESTINATIONS.firstNotNullOfOrNull { destination ->
                val wanted = runCatching { expected(destination,mode) }.getOrNull()
                val changed = runCatching { mutated(destination,mode) }.getOrNull()
                val substituted = addressing?.let { runCatching { it(destination,mode) }.getOrNull() }
                val reversed = reversedPrimitive?.let { runCatching { it(destination,mode) }.getOrNull() }
                val vertexChanged = substitutedVertex?.let { runCatching { it(destination,mode) }.getOrNull() }
                fun describe(value: WgslFloatEnvelopeV1Oracle.DrawResult?) =
                    if (value is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) value.channels.toString() else value.toString()
                lastResult = "wanted=${describe(wanted)} / changed=${describe(changed)} / " +
                    "addressing=${describe(substituted)} / reversed=${describe(reversed)} / vertex=${describe(vertexChanged)}"
                if (wanted is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    changed is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    wanted.channels.indices.any { wanted.channels[it].intersect(changed.channels[it]).isEmpty() } &&
                    (addressing == null || substituted is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                        wanted.channels.indices.any { wanted.channels[it].intersect(substituted.channels[it]).isEmpty() }) &&
                    (reversedPrimitive == null || reversed is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                        wanted.channels.indices.any { wanted.channels[it].intersect(reversed.channels[it]).isEmpty() }) &&
                    (substitutedVertex == null || vertexChanged is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                        (0..2).any { wanted.channels[it].intersect(vertexChanged.channels[it]).isEmpty() }))
                    destination to wanted else null
            }
            assertNotNull(witness, "No bounded independent destination for $fixtureKey/$mode: $lastResult")
            }
            Triple(mode, fixture, witness)
        }
        for ((mode, fixture, witness) in prepared) {
            val (destination,wanted) = witness
            val runtimeIdeal = if (lane != "Vertices-colored" && mode == BlendMode.SRC_IN && fixtureKey in listOf("runtime/child", "runtime/nested")) {
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
            for (replay in supportedReplays(lane, picture)) {
                val target = Surface(1, 1)
                target.canvas { replay.playback(this) }
                repeat(2) { assertCaptured(target.render()) }
            }
        }
    }

    private fun vertexSource(lane: String, shader: Shader): Shader =
        if (lane == "Vertices-colored") Shader.Blend(BlendMode.SRC_ATOP, Shader.SolidColor(VERTEX_COLOR), shader)
        else shader

    private fun supportedReplays(lane: String, picture: Picture): List<Picture> {
        val decoded = Picture.fromByteArray(picture.toByteArray())
        return if (lane == "Text") listOfNotNull(picture, decoded)
        else listOf(picture, assertNotNull(decoded))
    }

    private fun assertLanePixels(lane: String, result: RenderResult, wanted: WgslFloatEnvelopeV1Oracle.DrawResult) {
        assertEquals(4, result.pixels.size, lane)
        WgslFloatEnvelopeV1Oracle.assertAdmits(wanted, result.pixels)
    }

    private fun Canvas.background(destination: ColorARGB = DESTINATION) =
        drawRect(UNIT, Paint(color = destination, blendMode = BlendMode.SRC, antiAlias = false))

    private fun triangle(textured: Boolean = false, colored: Boolean = false) = Vertices(
        VertexMode.TRIANGLES, listOf(Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f)),
        texCoords = if (textured) listOf(Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f)) else null,
        colors = if (colored) List(3) { VERTEX_COLOR } else null, indices = listOf(0, 1, 2))

    private fun Canvas.drawLane(lane: String, paint: Paint) {
        when (lane) {
            // Translation of the established (10,40) interior sample into the one-pixel target.
            "Text" -> drawText(TEXT, -6f, 18f, paint)
            "Vertices" -> drawVertices(triangle(), paint)
            "Vertices-colored" -> drawVertices(triangle(colored = true), BlendMode.SRC_ATOP, paint)
            "Vertices-textured" -> drawVertices(triangle(textured = true), paint)
            "Mesh-textured" -> drawMesh(Mesh(triangle(textured = true), bounds = RectF32.ofLTRB(-1f,-1f,5f,5f)),
                paint.copy(blendMode = BlendMode.SRC_OVER), paint.blendMode)
            else -> error(lane)
        }
    }

    private class Fixture(val shader: Shader, val oracle: Shader = shader, val changedOracle: Shader,
        val external: ColorFilter? = null, val changedExternal: ColorFilter? = external,
        val addressingOracle: Shader? = null, val mutate: () -> Unit)

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
            val tile = if (variant.startsWith("tile-")) TileMode.valueOf(variant.removePrefix("tile-").removeSuffix("-outside").removeSuffix("-distinct")) else TileMode.CLAMP
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
                "tile-MIRROR-distinct" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-3f, 0f))
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
        val addressing = if (variant == "tile-MIRROR-distinct") Shader.WithLocalMatrix(
            gradient("linear", stops.toList(), TileMode.CLAMP), Matrix3x3F32.translation(-3f, 0f)) else null
        return Fixture(wrap(stops), changedOracle = counterfactual, external = exteriorFilter, addressingOracle = addressing) {
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
        val wanted = W5fColorCpuOracle.expectedShaderTree(vertexSource(lane, valid.oracle), PAINT_ALPHA, destination = DESTINATION,
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

    @ParameterizedTest(name = "authenticated topology: {0}/{1}") @MethodSource("finalBlendTopology")
    fun preparedFinalBlendTopologyPreservesPixels(lane: String, variant: String) {
        val fixture = runtimeFixture("child")
        val paint = Paint(color = ColorARGB.of(149, 255, 255, 255), shader = fixture.shader, antiAlias = false)
        val difference = variant in listOf("difference-first", "dst-then-difference")
        val wanted = if (difference) W5fColorCpuOracle.expectedShaderTree(vertexSource(lane, fixture.oracle), PAINT_ALPHA,
            finalBlend = BlendMode.DIFFERENCE)
        else W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(
            if (variant == "dst-background") DESTINATION else ColorARGB.Transparent))
        W5fSurfacePixelFixtures.requireBounded(wanted)
        fun Canvas.frame() {
            if (variant == "dst-background") background()
            if (variant != "difference-first") drawLane(lane, paint.copy(blendMode = BlendMode.DST))
            if (difference) drawLane(lane, paint.copy(blendMode = BlendMode.DIFFERENCE))
        }
        val surface = Surface(1, 1)
        surface.canvas { frame() }
        val recorder = PictureRecorder()
        recorder.beginRecording(UNIT).frame()
        val picture = recorder.finishRecordingAsPicture()
        fixture.mutate()
        repeat(2) { assertLanePixels(lane, surface.render(), wanted) }
        for (replay in supportedReplays(lane, picture)) {
            val target = Surface(1, 1)
            target.canvas { replay.playback(this) }
            repeat(2) { assertLanePixels(lane, target.render(), wanted) }
        }
    }

    @ParameterizedTest(name = "empty clip topology: {0}/{1}") @MethodSource("emptyClipTopology")
    fun preparedEmptyClipPreservesMaterialTopology(lane: String, variant: String) {
        val fixture = runtimeFixture("child")
        val paint = Paint(color = ColorARGB.of(149, 255, 255, 255), shader = fixture.shader,
            blendMode = BlendMode.DIFFERENCE, antiAlias = false)
        val wanted = if (variant == "only") W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Transparent))
            else W5fColorCpuOracle.expectedShaderTree(fixture.oracle, PAINT_ALPHA, finalBlend = BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        fun Canvas.frame() {
            if (variant == "after") drawLane(lane, paint)
            save()
            clipRect(RectF32.ofLTRB(10f, 10f, 20f, 20f), antiAlias = false)
            drawLane(lane, paint)
            restore()
            if (variant == "before") drawLane(lane, paint)
        }
        val surface = Surface(1, 1)
        surface.canvas { frame() }
        fixture.mutate()
        // This is a Surface publication/footprint control. The historical Picture clip
        // replay boundary is independent of material promotion and remains unchanged.
        repeat(2) { assertLanePixels(lane, surface.render(), wanted) }
    }

    @ParameterizedTest(name = "historical siblings: {0}/{1}") @MethodSource("historicalMixedFrames")
    fun preparedMixedClearColorAndFractionalRectPreservePixels(lane: String, variant: String) {
        val wanted = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Red),
            destination = ColorARGB.Blue, finalBlend = BlendMode.SRC_OVER, destinationBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        fun Canvas.frame() {
            when (variant) {
                "Clear" -> clear(ColorARGB.Blue)
                "DrawColor" -> drawColor(ColorARGB.Blue, BlendMode.SRC)
                "fractional-rect" -> drawRect(RectF32.ofLTRB(-1.5f, -2f, 4f, 4f),
                    Paint(color = ColorARGB.Blue, blendMode = BlendMode.SRC, antiAlias = false))
                else -> error(variant)
            }
            drawLane(lane, Paint(color = ColorARGB.Red, blendMode = BlendMode.SRC_OVER, antiAlias = false))
        }
        val surface = Surface(1, 1)
        surface.canvas { frame() }
        if (lane == "Text" && variant in listOf("Clear", "DrawColor")) {
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals("invalid.recording.w5b-mixed-timeline: Required value was null.", failure.message)
            surface.discardRecordedOperations()
            surface.canvas { background(ColorARGB.Blue); drawLane(lane,
                Paint(color = ColorARGB.Red, blendMode = BlendMode.SRC_OVER, antiAlias = false)) }
            repeat(2) { assertLanePixels(lane, surface.render(), wanted) }
            return
        }
        val recorder = PictureRecorder()
        recorder.beginRecording(UNIT).frame()
        val picture = recorder.finishRecordingAsPicture()
        repeat(2) { assertLanePixels(lane, surface.render(), wanted) }
        for (replay in supportedReplays(lane, picture)) {
            val target = Surface(1, 1)
            target.canvas { replay.playback(this) }
            repeat(2) { assertLanePixels(lane, target.render(), wanted) }
        }
    }

    @Test fun legacyMeshProgramRefusesThenSameSurfaceRecovers() {
        val surface = Surface(1, 1)
        val effect = org.graphiks.kanvas.pipeline.RuntimeEffect(
            id = "kanvas.runtime.child-opacity",
            module = org.graphiks.kanvas.pipeline.ShaderModule.fromSource("fn main() -> vec4<f32> { return vec4<f32>(1.0); }"),
            uniformLayout = org.graphiks.kanvas.pipeline.UniformLayout(emptyList()), children = emptyList())
        surface.canvas {
            drawMesh(Mesh(triangle(), MeshProgram(effect), RectF32.ofLTRB(-1f,-1f,5f,5f)),
                Paint(antiAlias = false))
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.material.runtime_effect.unregistered_semantics"), failure.message)
        surface.discardRecordedOperations()
        val valid = runtimeFixture("child")
        val wanted = W5fColorCpuOracle.expectedShaderTree(valid.oracle, PAINT_ALPHA, destination = DESTINATION,
            finalBlend = BlendMode.SRC_IN, destinationBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        surface.canvas { background(); drawLane("Vertices", Paint(color = ColorARGB.of(149,255,255,255),
            shader = valid.shader, blendMode = BlendMode.SRC_IN, antiAlias = false)) }
        repeat(2) { assertLanePixels("Vertices", surface.render(), wanted) }
    }

    @ParameterizedTest(name = "raw operation fallback: {0}/{1}") @MethodSource("rawOperationFallbackCases")
    fun rawVerticesOperationBlendSurvivesHistoricalSiblings(sibling: String, finalMode: BlendMode) {
        val material = Shader.Opacity(Shader.SolidColor(SOURCE), .5f)
        val wantedSource = vertexSource("Vertices-colored", material)
        val wantedOracle = W5fColorCpuOracle.capturedShaderTree(wantedSource, PAINT_ALPHA, null, destinationBlend = BlendMode.SRC)
        val wrongAlpha = W5fColorCpuOracle.capturedShaderTree(vertexSource("Vertices-colored",
            Shader.Opacity(material, PAINT_ALPHA)), 1f, null, destinationBlend = BlendMode.SRC)
        val reversed = W5fColorCpuOracle.capturedShaderTree(Shader.Blend(BlendMode.SRC_ATOP, material,
            Shader.SolidColor(VERTEX_COLOR)), PAINT_ALPHA, null, destinationBlend = BlendMode.SRC)
        val ignored = W5fColorCpuOracle.capturedShaderTree(Shader.Blend(BlendMode.MODULATE,
            Shader.SolidColor(VERTEX_COLOR), material), PAINT_ALPHA, null, destinationBlend = BlendMode.SRC)
        val witness = assertNotNull((listOf(ColorARGB.Transparent) + DESTINATIONS.take(3)).firstNotNullOfOrNull { destination ->
            val wanted = wantedOracle(destination, finalMode) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
                ?: return@firstNotNullOfOrNull null
            val alternatives = listOf(wrongAlpha, reversed, ignored).map { it(destination, finalMode) }
            if (alternatives.all { other -> other is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    (0..3).any { wanted.channels[it].intersect(other.channels[it]).isEmpty() } }) destination to wanted else null
        })
        val colors = MutableList(3) { VERTEX_COLOR }
        val vertices = Vertices(VertexMode.TRIANGLES,
            listOf(Point2F32(-1f,-1f), Point2F32(5f,-1f), Point2F32(-1f,5f)), colors = colors, indices = listOf(0,1,2))
        val paint = Paint(color = ColorARGB.of(149,255,255,255), shader = material,
            blendMode = finalMode, antiAlias = false)
        fun Canvas.frame() {
            when (sibling) {
                "Clear" -> clear(ColorARGB.Blue)
                "DrawColor" -> drawColor(ColorARGB.Blue, BlendMode.SRC)
                "fractional-rect" -> drawRect(RectF32.ofLTRB(-1.5f,-2f,4f,4f),
                    Paint(color = ColorARGB.Blue, blendMode = BlendMode.SRC, antiAlias = false))
            }
            background(witness.first)
            drawVertices(vertices, BlendMode.SRC_ATOP, paint)
        }
        val surface = Surface(1, 1)
        surface.canvas { frame() }
        if (sibling != "fractional-rect") {
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals("invalid.recording.w5b-mixed-timeline: Required value was null.", failure.message)
            surface.discardRecordedOperations()
            surface.canvas {
                drawRect(RectF32.ofLTRB(-1.5f,-2f,4f,4f),
                    Paint(color = ColorARGB.Blue, blendMode = BlendMode.SRC, antiAlias = false))
                background(witness.first)
                drawVertices(vertices, BlendMode.SRC_ATOP, paint)
            }
            colors.replaceAll { ColorARGB.White }
            repeat(2) { assertLanePixels("raw fallback recovery", surface.render(), witness.second) }
            return
        }
        val recorder = PictureRecorder()
        recorder.beginRecording(UNIT).frame()
        val picture = recorder.finishRecordingAsPicture()
        colors.replaceAll { ColorARGB.White }
        repeat(2) { assertLanePixels("raw fallback", surface.render(), witness.second) }
        for (replay in supportedReplays("Vertices-colored", picture)) {
            val target = Surface(1, 1)
            target.canvas { replay.playback(this) }
            repeat(2) { assertLanePixels("raw fallback replay", target.render(), witness.second) }
        }
    }

    @ParameterizedTest(name = "zero-consumer Text: {0}") @MethodSource("zeroConsumerTextCases")
    fun zeroConsumerTextDoesNotPublishMaterial(variant: String) {
        val runs = if (variant == "empty-blob") emptyList()
            else listOf(KanvasGlyphRun(emptyList(), emptyList(), fontSize = 48f))
        val blob = TextBlob(runs, TEXT.typeface, 48f)
        val fixture = runtimeFixture("child")
        val paint = Paint(color = ColorARGB.of(149,255,255,255), shader = fixture.shader,
            blendMode = BlendMode.DIFFERENCE, antiAlias = false)
        val transparent = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Transparent))
        val surface = Surface(1, 1)
        surface.canvas { background(ColorARGB.Blue) }
        surface.render()
        surface.discardRecordedOperations()
        surface.canvas { drawText(blob, -6f, 18f, paint) }
        repeat(2) { assertLanePixels("zero-consumer", surface.render(), transparent) }
        surface.discardRecordedOperations()
        val wanted = W5fColorCpuOracle.expectedShaderTree(fixture.oracle, PAINT_ALPHA, finalBlend = BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        surface.canvas { drawText(blob, -6f,18f,paint); drawLane("Text",paint); drawText(blob,-6f,18f,paint) }
        fixture.mutate()
        repeat(2) { assertLanePixels("zero-consumer recovery", surface.render(), wanted) }
    }

    companion object {
        private val UNIT = RectF32.ofLTRB(0f, 0f, 1f, 1f)
        private val SOURCE = ColorARGB.of(191, 224, 64, 128)
        private val SECOND = ColorARGB.of(191, 64, 128, 224)
        private val MUTATED = ColorARGB.of(223, 96, 224, 64)
        private val DESTINATION = ColorARGB.of(127, 16, 0, 0)
        private val DESTINATIONS = listOf(DESTINATION, ColorARGB.of(127, 0, 16, 0), ColorARGB.of(127, 0, 0, 16)) +
            listOf(1, 2, 3, 4, 5, 7, 15, 31, 63, 127, 191).flatMap { alpha ->
            listOf(1, 2, 4, 8, 10, 16, 32, 72, 128, 224, 255).flatMap { component ->
                listOf(ColorARGB.of(alpha, component, 0, 0), ColorARGB.of(alpha, 0, component, 0), ColorARGB.of(alpha, 0, 0, component)) }
        }
        // Cache only independently computed immutable oracle values.
        private val EXPECTATIONS = mutableMapOf<String, Pair<ColorARGB, WgslFloatEnvelopeV1Oracle.DrawResult.Bounded>>()
        private const val PAINT_ALPHA = 149f / 255f
        private val FINALS = listOf(BlendMode.SRC_IN, BlendMode.DIFFERENCE)
        private val TEXT = TextBlob(listOf(KanvasGlyphRun(
            listOf(GPUPreparedTextTestFixtures.A8_GLYPH_ID.toUShort()), listOf(Point2F32(0f,0f)), fontSize = 48f)),
            FontTypeface(GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(), "W5h material coverage"), 48f)
        private val VERTEX_COLOR = ColorARGB.of(255, 64, 128, 224)
        private val TEXT_LANES = listOf("Text")
        private val VERTEX_LANES = listOf("Vertices", "Vertices-textured", "Mesh-textured")
        private val IMAGES = listOf("RGBA", "A8").flatMap { format -> listOf("nearest", "linear", "cubic").map { "$format/$it" } }
        private val FILTERS = listOf("Matrix", "Compose", "Compose-reversed", "Lerp-0", "Lerp-0.25", "Lerp-0.5", "Lerp-1",
            "Table", "Lighting", "SRGBToLinear", "LinearToSRGB", "HSLAMatrix", "HighContrast", "Luma", "Overdraw",
            "filter-before-opacity", "filter-after-opacity", "nested-opacity-filters") + BlendMode.entries.map { "Blend-$it" }
        private val PROCEDURAL = listOf("Blend-shared-ordered", "Perlin", "Fractal")
        private val RUNTIME = listOf("child", "nested", "image-child", "stops-child", "noise-child")
        private fun cases(lanes: List<String>, variants: List<String>) = lanes.flatMap { lane -> variants.map { Arguments.of(lane, it) } }
        private val GRADIENTS = listOf("linear", "radial", "sweep", "conical").flatMap { family ->
            listOf("1", "2", "16", "17", "duplicate").map { "$family/$it" } }
        private val ADDRESSING = listOf("identity", "translation", "uniform-scale", "nonuniform-scale",
            "rotation", "shear", "reflection", "projective", "clamp", "matrix-clamp", "clamp-matrix") + TileMode.entries.map { "tile-$it" }
        @JvmStatic fun textGradients() = cases(TEXT_LANES, GRADIENTS)
        @JvmStatic fun verticesMeshGradients() = cases(VERTEX_LANES, GRADIENTS) + Arguments.of("Vertices-colored", "linear/1")
        @JvmStatic fun textAddressing() = cases(TEXT_LANES, ADDRESSING)
        @JvmStatic fun verticesMeshAddressing() = cases(VERTEX_LANES, ADDRESSING) + Arguments.of("Vertices-colored", "tile-REPEAT")
        @JvmStatic fun textImages() = cases(TEXT_LANES, IMAGES)
        @JvmStatic fun verticesMeshImages() = cases(VERTEX_LANES, IMAGES) + Arguments.of("Vertices-colored", "RGBA/nearest")
        @JvmStatic fun textFilters() = cases(TEXT_LANES, FILTERS)
        @JvmStatic fun verticesMeshFilters() = cases(VERTEX_LANES, FILTERS) + Arguments.of("Vertices-colored", "Matrix")
        @JvmStatic fun textProcedural() = cases(TEXT_LANES, PROCEDURAL)
        @JvmStatic fun verticesMeshProcedural() = cases(VERTEX_LANES, PROCEDURAL) + Arguments.of("Vertices-colored", "Blend-shared-ordered")
        @JvmStatic fun textRuntime() = cases(TEXT_LANES, RUNTIME)
        @JvmStatic fun verticesMeshRuntime() = cases(VERTEX_LANES, RUNTIME) + Arguments.of("Vertices-colored", "child")
        @JvmStatic fun allLanes() = TEXT_LANES + VERTEX_LANES + "Vertices-colored"
        @JvmStatic fun finalBlendTopology() = cases(TEXT_LANES + VERTEX_LANES,
            listOf("dst-background", "dst-only", "difference-first", "dst-then-difference"))
        @JvmStatic fun emptyClipTopology() = cases(TEXT_LANES + VERTEX_LANES, listOf("only", "before", "after"))
        @JvmStatic fun rawOperationFallbackCases() = listOf("Clear", "DrawColor", "fractional-rect").flatMap { sibling ->
            FINALS.map { Arguments.of(sibling, it) }
        }
        @JvmStatic fun zeroConsumerTextCases() = listOf("empty-blob", "empty-run")
        @JvmStatic fun historicalMixedFrames() = cases(TEXT_LANES + VERTEX_LANES.filterNot { it == "Vertices-colored" },
            listOf("Clear", "DrawColor", "fractional-rect"))
    }
}
