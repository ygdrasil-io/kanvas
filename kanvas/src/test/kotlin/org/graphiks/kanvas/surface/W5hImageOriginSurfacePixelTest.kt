@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Direct-image pixels; replacing mask material, tail alpha, filter order or final blend breaks these witnesses. */
class W5hImageOriginSurfacePixelTest {
    @ParameterizedTest(name = "{0}/mask={1}") @MethodSource("solidMasks")
    fun a8SolidOpacityCaptureBlend(variant: String, mask: Int) {
        capture("A8", mask, "solid/$variant") { solidFixture(variant) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("rgbaCases")
    fun rgbaPaintAlphaCaptureBlend(variant: String) {
        capture("RGBA", 127, "rgba/$variant") {
            runtimeFixture(variant)
        }
    }

    @Test fun rgbaHalfAlphaPublicControl() {
        // The public ARGB paint stores half alpha as byte 128; every H invocation remains 149.
        capture("RGBA",127,"rgba/half-alpha",paintAlpha=128) { runtimeFixture("child") }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("originBlends")
    fun imageOriginsFinalBlendCapture(origin: String, mode: BlendMode) {
        capture(origin, 127, "blend/$mode", modes = listOf(mode)) { runtimeFixture("child") }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("gradients")
    fun a8FourGradientsCaptureBlend(variant: String) {
        capture("A8", 127, "gradient/$variant") { wrapped(gradientFixture(variant)) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("addressing")
    fun a8AddressingCaptureBlend(variant: String) {
        capture("A8", 127, "address/$variant") { wrapped(addressingFixture(variant)) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("procedural")
    fun a8BlendNoiseCaptureBlend(variant: String) {
        capture("A8", 127, "procedural/$variant") { proceduralFixture(variant) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("runtimeCases")
    fun a8RuntimeCaptureBlend(variant: String) {
        capture("A8", 127, "runtime/$variant") { runtimeFixture(variant) }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("imageOrigins")
    fun imageOriginsRetainComplexClipAndCommonSource(origin: String) {
        capture(origin,127,"complex-clip",complexClip=true) { runtimeFixture("nested") }
    }

    private class Fixture(val shader: Shader?, val oracle: Shader, val changed: Shader,
        val counterfactual: Shader? = null, val mutate: () -> Unit)

    private fun capture(origin: String, mask: Int, key: String, paintAlpha: Int = 149,
        modes: List<BlendMode> = FINALS, complexClip: Boolean = false, make: () -> Fixture) {
        for (mode in modes) {
            val fixture = make()
            val rgba = byteArrayOf(224.toByte(), 64, 128.toByte(), 191.toByte())
            val bytes = if (origin == "A8") byteArrayOf(mask.toByte()) else rgba
            val marker = markerMatrix()
            if (key == "gradient/sweep/16" || key == "gradient/sweep/17") {
                marker.setScale(.125f,.125f,.125f,.5f)
                marker.postTranslate(.125f,.0625f,.125f,.25f)
            }
            val external = when {
                mode == BlendMode.SRC_OVER -> null
                key == "address/tile-DECAL" -> ColorFilter.Blend(SOURCE.withAlpha(127),BlendMode.SRC_OVER)
                else -> ColorFilter.Matrix(marker)
            }
            val source = if (origin == "A8") Shader.Opacity(fixture.oracle, mask / 255f)
                else Shader.SolidColor(SOURCE)
            val expected = W5fColorCpuOracle.capturedShaderTree(source, paintAlpha / 255f, external,
                destinationBlend = BlendMode.SRC)
            val alternate = fixture.counterfactual?.let { W5fColorCpuOracle.capturedShaderTree(
                Shader.Opacity(it, mask / 255f), paintAlpha / 255f, external, destinationBlend = BlendMode.SRC) }
            // A bounded finite oracle-only search, completed before Surface/PictureRecorder.
            val candidates = when {
                mode == BlendMode.SRC_OVER -> listOf(ColorARGB.Transparent) + DESTINATIONS.take(3)
                key == "address/tile-DECAL" -> listOf(ColorARGB.of(127,64,128,192),ColorARGB.of(63,32,64,128),ColorARGB.of(191,128,64,224))
                else -> DESTINATIONS
            }
            val witness = candidates.firstNotNullOfOrNull { destination ->
                val wanted = (if (mode == BlendMode.DST) W5fColorCpuOracle.expectedShaderTree(
                    Shader.SolidColor(destination), finalBlend = BlendMode.SRC) else expected(destination,
                    // Fixed-function S over an exactly clear target is S, with no destination term.
                    if (mode == BlendMode.SRC_OVER && destination == ColorARGB.Transparent) BlendMode.SRC else mode)) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
                val other = alternate?.invoke(destination, mode) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
                if (wanted != null &&
                    (alternate == null || other != null && disjoint(wanted, other))) destination to wanted else null
            }
            assertNotNull(witness, "No bounded independent witness: $origin/$key/$mode/mask=$mask")
            val (destination, wanted) = witness
            val untouched = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(destination),finalBlend=BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(untouched)
            val image = Image.fromPixels(1, 1, bytes,
                if (origin == "A8") ColorType.ALPHA_8 else ColorType.RGBA_8888, alphaType = AlphaType.UNPREMUL)
            val paint = Paint(color = if (fixture.shader == null && origin == "A8") SOURCE.withAlpha(paintAlpha)
                else ColorARGB.of(paintAlpha, 16, 224, 32), shader = fixture.shader,
                colorFilter = external, blendMode = mode, antiAlias = false)
            fun Canvas.frame() {
                val extent=if(complexClip) RectF32.ofLTRB(0f,0f,2f,1f) else UNIT
                val background=Paint(color = destination, blendMode = BlendMode.SRC, antiAlias = false)
                if(complexClip) drawPath(Path().apply { addRect(extent) },background) else drawRect(extent,background)
                if(complexClip) clipRect(RectF32.ofLTRB(1f,-1f,3f,2f),ClipOp.DIFFERENCE,antiAlias=false)
                drawImage(image, extent, SamplingOptions.NEAREST, paint)
            }
            val surface = Surface(if(complexClip) 2 else 1, 1)
            surface.canvas { frame() }
            // Complex-clip transport is the previously documented Picture limitation, not this source contract.
            val picture = if(complexClip) null else PictureRecorder().let { recorder ->
                recorder.beginRecording(UNIT).frame()
                recorder.finishRecordingAsPicture()
            }
            fixture.mutate()
            bytes.fill(0)
            marker.setScale(0f,0f,0f,0f)
            repeat(2) {
                val pixels=surface.render().pixels
                WgslFloatEnvelopeV1Oracle.assertAdmits(wanted, pixels.copyOfRange(0,4))
                if(complexClip) WgslFloatEnvelopeV1Oracle.assertAdmits(untouched,pixels.copyOfRange(4,8))
            }
            for (replay in picture?.let { listOf(it, assertNotNull(Picture.fromByteArray(it.toByteArray()))) }.orEmpty()) {
                val target = Surface(1, 1)
                target.canvas { replay.playback(this) }
                repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted, target.render().pixels) }
            }
        }
    }

    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded,
        b: WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) = a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() }

    private fun solidFixture(variant: String): Fixture {
        val solid = Shader.SolidColor(SOURCE)
        val children = linkedMapOf("child" to solid as Shader)
        return when (variant) {
            "solid" -> Fixture(null, Shader.SolidColor(SOURCE.withAlpha(255)), Shader.SolidColor(MUTATED)) { }
            "opacity" -> Fixture(Shader.Opacity(solid, .5f), Shader.Opacity(solid, .5f), Shader.SolidColor(MUTATED)) { }
            else -> Fixture(runtime(children), Shader.Opacity(solid, .5f), Shader.Opacity(Shader.SolidColor(MUTATED), .5f)) {
                children["child"] = Shader.SolidColor(MUTATED)
            }
        }
    }

    private fun wrapped(child: Fixture): Fixture {
        val children = linkedMapOf("child" to requireNotNull(child.shader))
        return Fixture(runtime(children), Shader.Opacity(child.oracle, .5f), Shader.Opacity(child.changed, .5f),
            child.counterfactual?.let { Shader.Opacity(it, .5f) }) {
            children["child"] = Shader.SolidColor(MUTATED)
            child.mutate()
        }
    }

    private fun gradientFixture(variant: String): Fixture {
        val (family, count) = variant.split('/')
        val stops = when (count) {
            "duplicate" -> mutableListOf(GradientStop(0f, SOURCE), GradientStop(.5f, SOURCE),
                GradientStop(.5f, SECOND), GradientStop(1f, SECOND))
            "1" -> mutableListOf(GradientStop(.25f, SOURCE))
            else -> MutableList(count.toInt()) { i -> GradientStop(i.toFloat() / (count.toInt() - 1),
                if (i < count.toInt() / 2) SOURCE else SECOND) }
        }
        return Fixture(gradient(family, stops), gradient(family, stops.toList()),
            gradient(family, listOf(GradientStop(0f, MUTATED), GradientStop(1f, MUTATED)))) {
            stops.replaceAll { it.copy(color = MUTATED) }
        }
    }

    private fun gradient(family: String, stops: List<GradientStop>, tile: TileMode = TileMode.CLAMP): Shader = when (family) {
        "linear" -> Shader.LinearGradient(Point2F32(0f,.5f), Point2F32(2f,.5f), stops, tileMode = tile)
        "radial" -> Shader.RadialGradient(Point2F32(-.5f,.5f), 4f, stops, tileMode = tile)
        "sweep" -> Shader.SweepGradient(Point2F32(-.5f,-.5f), 0f, 360f, stops, tileMode = tile)
        "conical" -> Shader.ConicalGradient(Point2F32(-.5f,.5f), 0f, Point2F32(-.5f,.5f), 4f, stops, tileMode = tile)
        else -> error(family)
    }

    private fun addressingFixture(variant: String): Fixture {
        val stops = mutableListOf(GradientStop(0f, SOURCE), GradientStop(.5f, SOURCE),
            GradientStop(.5f, SECOND), GradientStop(1f, SECOND))
        fun source(values: List<GradientStop>, substitute: Boolean = false): Shader {
            val tile = if (variant.startsWith("tile-") && !substitute)
                TileMode.valueOf(variant.removePrefix("tile-").removeSuffix("-outside").removeSuffix("-distinct")) else TileMode.CLAMP
            val leaf = gradient("linear", values, tile)
            return when (variant) {
                "identity" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32())
                "translation" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.translation(-1f, 0f))
                "uniform-scale" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(.25f,.25f))
                "nonuniform-scale" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(.25f,2f))
                "rotation" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(sx=0f,kx=-1f,ky=1f,sy=0f,tx=1f,ty=-1f))
                "shear" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(kx=-2f))
                "reflection" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(sx=-1f,tx=2f))
                "projective" -> Shader.WithLocalMatrix(leaf, Matrix3x3F32(persp0=.125f,sx=.5f))
                "clamp" -> Shader.CoordClamp(leaf, RectF32.ofLTRB(1.25f,2f,1.75f,3f))
                "matrix-clamp" -> Shader.WithLocalMatrix(Shader.CoordClamp(leaf,RectF32.ofLTRB(1.25f,2f,1.75f,3f)),Matrix3x3F32.translation(-4f,-8f))
                "clamp-matrix" -> Shader.CoordClamp(Shader.WithLocalMatrix(leaf,Matrix3x3F32.translation(-1f,0f)),RectF32.ofLTRB(-.25f,2f,.25f,3f))
                "tile-DECAL" -> Shader.WithLocalMatrix(leaf,Matrix3x3F32.translation(-1.25f,0f))
                "tile-MIRROR-distinct" -> Shader.WithLocalMatrix(leaf,Matrix3x3F32.translation(-3f,0f))
                else -> Shader.WithLocalMatrix(leaf,Matrix3x3F32.translation(-2.25f,0f))
            }
        }
        return Fixture(source(stops), source(stops.toList()), source(listOf(GradientStop(0f,MUTATED),GradientStop(1f,MUTATED))),
            if (variant in listOf("tile-DECAL-outside","tile-MIRROR-distinct")) source(stops.toList(), true) else null) {
            stops.replaceAll { it.copy(color = MUTATED) }
        }
    }

    private fun proceduralFixture(variant: String): Fixture {
        fun family(changed: Boolean): Shader = when (variant) {
            "Blend-shared-ordered" -> {
                val shared = Shader.SolidColor(SOURCE)
                val filtered = Shader.WithColorFilter(shared,ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) }))
                if (changed) Shader.Blend(BlendMode.SRC_OVER,shared,filtered) else Shader.Blend(BlendMode.SRC_OVER,filtered,shared)
            }
            "Perlin" -> Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(SOURCE.withAlpha(127)),
                Shader.PerlinNoise(.125f,.25f,2,if (changed) 19 else 7,null))
            else -> Shader.FractalNoise(.125f,.25f,2,if (changed) 19 else 7,null)
        }
        val original = family(false)
        val changed = family(true)
        val children = linkedMapOf("child" to original)
        return Fixture(runtime(children),Shader.Opacity(original,.5f),Shader.Opacity(changed,.5f),
            if (variant == "Blend-shared-ordered") Shader.Opacity(changed,.5f) else null) { children["child"] = changed }
    }

    private fun runtimeFixture(variant: String): Fixture {
        val child = when (variant) {
            "stops-child" -> gradientFixture("linear/17")
            "noise-child" -> proceduralFixture("Fractal")
            "image-child" -> {
                val bytes = byteArrayOf(224.toByte(),64,128.toByte(),191.toByte())
                val shader = Shader.Image(Image.fromPixels(1,1,bytes,alphaType=AlphaType.UNPREMUL),sampling=SamplingOptions.NEAREST)
                Fixture(shader,Shader.SolidColor(SOURCE),Shader.SolidColor(MUTATED)) { bytes.fill(0) }
            }
            else -> {
                val matrix = ColorMatrixF32.ofIdentity()
                val shader = Shader.WithColorFilter(Shader.SolidColor(SOURCE),ColorFilter.Matrix(matrix))
                Fixture(shader,Shader.SolidColor(SOURCE),Shader.SolidColor(MUTATED)) { matrix.setScale(0f,0f,0f,0f) }
            }
        }
        return if (variant == "nested") wrapped(wrapped(child)) else wrapped(child)
    }

    private fun runtime(children: Map<String,Shader>): Shader = Shader.RuntimeEffect(
        assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity",1)), UniformBlock { float1("alpha",.5f) }, children)

    private fun markerMatrix() = ColorMatrixF32.ofIdentity().apply {
        setScale(.5f,.75f,.25f,.5f)
        postTranslate(.125f,.0625f,.125f,.25f)
    }

    @Test fun sharedImageOwnerAndIndependentOwnersRespectBudgetAndRecovery() {
        val bytes = ByteArray(256 * 256 * 4) { i -> byteArrayOf(-1,0,0,-1)[i % 4] }
        fun image(source: String) = Image.fromPixels(256,256,bytes.copyOf(),sourceId=source,alphaType=AlphaType.UNPREMUL)
        val shared = image("shared")
        val child = linkedMapOf("child" to Shader.Image(shared,sampling=SamplingOptions.NEAREST) as Shader)
        val wanted = W5fColorCpuOracle.expectedShaderTree(Shader.Opacity(Shader.SolidColor(ColorARGB.Red),.5f),
            finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val surface = Surface(1,1,config=RenderConfig(frameLocalBudgetBytes=800_000L))
        surface.canvas {
            drawImage(shared,UNIT,SamplingOptions.NEAREST,Paint(blendMode=BlendMode.SRC,antiAlias=false))
            drawRect(UNIT,Paint(shader=Shader.Image(shared,sampling=SamplingOptions.NEAREST),blendMode=BlendMode.SRC,antiAlias=false))
            drawRect(UNIT,Paint(shader=runtime(child),blendMode=BlendMode.SRC,antiAlias=false))
        }
        child["child"] = Shader.SolidColor(ColorARGB.Green)
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
        surface.discardRecordedOperations()
        val first = image("independent-first")
        val second = image("independent-second")
        surface.canvas {
            drawImage(first,UNIT,SamplingOptions.NEAREST,Paint(blendMode=BlendMode.SRC,antiAlias=false))
            drawImage(second,UNIT,SamplingOptions.NEAREST,Paint(blendMode=BlendMode.SRC,antiAlias=false))
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("resource-limit.w5g.composed-binding",failure.message.orEmpty().substringBefore(':'))
        surface.discardRecordedOperations()
        surface.canvas { drawRect(UNIT,Paint(color=ColorARGB.Red,blendMode=BlendMode.SRC,antiAlias=false)) }
        val recovered = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Red),finalBlend=BlendMode.SRC)
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(recovered,surface.render().pixels) }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("topologyCases")
    fun culledImageOriginsDoNotPublishSources(origin: String, variant: String) {
        val fixture = runtimeFixture("child")
        val image = Image.fromPixels(1,1,if(origin=="A8") byteArrayOf(127) else byteArrayOf(224.toByte(),64,128.toByte(),191.toByte()),
            if(origin=="A8") ColorType.ALPHA_8 else ColorType.RGBA_8888,alphaType=AlphaType.UNPREMUL)
        val source = if(origin=="A8") Shader.Opacity(fixture.oracle,127f/255f) else Shader.SolidColor(SOURCE)
        val wanted = W5fColorCpuOracle.expectedShaderTree(if(variant=="only") Shader.SolidColor(ColorARGB.Transparent) else source,
            if(variant=="only") 1f else PAINT_ALPHA,finalBlend=BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val paint=Paint(color=ColorARGB.of(149,16,224,32),shader=fixture.shader,blendMode=BlendMode.DIFFERENCE,antiAlias=false)
        val surface=Surface(1,1)
        surface.canvas {
            if(variant=="after") drawImage(image,UNIT,SamplingOptions.NEAREST,paint)
            save(); clipRect(RectF32.ofLTRB(10f,10f,20f,20f),antiAlias=false)
            translate(10f,10f)
            skew(.25f,0f)
            drawImage(image,UNIT,SamplingOptions.NEAREST,paint)
            restore()
            if(variant=="before") drawImage(image,UNIT,SamplingOptions.NEAREST,paint)
        }
        fixture.mutate()
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
    }

    @Test fun rgbaIgnoresUnregisteredPaintShaderWhileA8NoOpAuthenticatesIt() {
        val effect = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }
        val shader = Shader.RuntimeEffect(effect,UniformBlock { },emptyMap())
        val image = Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1),alphaType=AlphaType.UNPREMUL)
        val wanted=W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Red),PAINT_ALPHA,finalBlend=BlendMode.SRC)
        val surface=Surface(1,1)
        surface.canvas { drawImage(image,UNIT,SamplingOptions.NEAREST,
            Paint(color=ColorARGB.of(149,0,255,0),shader=shader,blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
        surface.discardRecordedOperations()
        val mask=Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        surface.canvas { drawImage(mask,UNIT,SamplingOptions.NEAREST,Paint(shader=shader,blendMode=BlendMode.DST,antiAlias=false)) }
        val failure=assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.runtime_effect.unregistered_semantics",failure.message.orEmpty().substringBefore(':'))
        surface.discardRecordedOperations()
        surface.canvas {
            save(); translate(10f,10f); skew(.25f,0f)
            drawImage(mask,UNIT,SamplingOptions.NEAREST,Paint(shader=shader,blendMode=BlendMode.DIFFERENCE,antiAlias=false))
            restore()
        }
        val culledFailure=assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.runtime_effect.unregistered_semantics",culledFailure.message.orEmpty().substringBefore(':'))
        surface.discardRecordedOperations()
        surface.canvas { drawImage(image,UNIT,SamplingOptions.NEAREST,
            Paint(color=ColorARGB.of(149,0,255,0),blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
    }

    @Test fun culledImageKeepsAnOrdinarySourceConsumer() {
        val wanted=W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(SOURCE),finalBlend=BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        for (mask in listOf(false,true)) {
            val image=Image.fromPixels(1,1,if(mask) byteArrayOf(127) else byteArrayOf(-1,0,0,-1),
                if(mask) ColorType.ALPHA_8 else ColorType.RGBA_8888,alphaType=AlphaType.UNPREMUL)
            val fixture=runtimeFixture("child")
            val surface=Surface(1,1)
            surface.canvas {
                save(); translate(10f,10f); skew(.25f,0f)
                drawImage(image,UNIT,SamplingOptions.NEAREST,Paint(shader=fixture.shader,blendMode=BlendMode.DIFFERENCE,antiAlias=false))
                restore()
                drawRect(UNIT,Paint(color=SOURCE,blendMode=BlendMode.DIFFERENCE,antiAlias=false))
            }
            fixture.mutate()
            repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
        }
    }

    @Test fun a8NoOpRetainsOneStopCoordinateCollapse() {
        val color=Shader.SolidColor(SOURCE)
        val wanted=W5fColorCpuOracle.expectedShaderTree(Shader.Opacity(Shader.Opacity(color,.5f),127f/255f),
            PAINT_ALPHA,finalBlend=BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val shader=runtime(linkedMapOf("child" to Shader.WithLocalMatrix(
            gradient("linear",listOf(GradientStop(.25f,SOURCE))),Matrix3x3F32.scaling(0f,0f))))
        val image=Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        val paint=Paint(color=SOURCE.withAlpha(149),shader=shader,antiAlias=false)
        val surface=Surface(1,1)
        surface.canvas {
            drawImage(image,UNIT,SamplingOptions.NEAREST,paint.copy(blendMode=BlendMode.DIFFERENCE))
            drawImage(image,UNIT,SamplingOptions.NEAREST,paint.copy(blendMode=BlendMode.DST))
        }
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
    }

    @Test fun eliminatedImageAuthenticationKeepsFirstOwnerRefusalOrder() {
        val effect=SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }
        val unregistered=Shader.RuntimeEffect(effect,UniformBlock { },emptyMap())
        val singular=runtime(linkedMapOf("child" to Shader.WithLocalMatrix(
            gradient("linear",listOf(GradientStop(0f,SOURCE),GradientStop(1f,SECOND))),Matrix3x3F32.scaling(0f,0f))))
        val mask=Image.fromPixels(1,1,byteArrayOf(127),ColorType.ALPHA_8,alphaType=AlphaType.UNPREMUL)
        for (culled in listOf(false,true)) for (eliminatedFirst in listOf(false,true)) {
            val surface=Surface(1,1)
            fun Canvas.active() = drawImage(mask,UNIT,SamplingOptions.NEAREST,
                Paint(shader=unregistered,blendMode=BlendMode.DIFFERENCE,antiAlias=false))
            fun Canvas.eliminated() {
                save()
                if(culled) { translate(10f,10f); skew(.25f,0f) }
                drawImage(mask,UNIT,SamplingOptions.NEAREST,
                    Paint(shader=singular,blendMode=if(culled) BlendMode.DIFFERENCE else BlendMode.DST,antiAlias=false))
                restore()
            }
            surface.canvas {
                if(eliminatedFirst) { eliminated(); active() } else { active(); eliminated() }
            }
            val failure=assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals(if(eliminatedFirst) "unsupported.material.gradient.local-matrix-singular"
                else "unsupported.material.runtime_effect.unregistered_semantics",failure.message.orEmpty().substringBefore(':'),
                "culled=$culled/eliminatedFirst=$eliminatedFirst")
        }
    }

    @ParameterizedTest(name = "{0}/{1}") @MethodSource("topologyCases")
    fun noOpImageOriginsPreserveDestinationReadTopology(origin: String, variant: String) {
        val fixture = runtimeFixture("child")
        val image = Image.fromPixels(1,1,if(origin=="A8") byteArrayOf(127) else byteArrayOf(224.toByte(),64,128.toByte(),191.toByte()),
            if(origin=="A8") ColorType.ALPHA_8 else ColorType.RGBA_8888,alphaType=AlphaType.UNPREMUL)
        val shader = if(origin=="A8") Shader.Opacity(fixture.oracle,127f/255f) else Shader.SolidColor(SOURCE)
        val wanted = W5fColorCpuOracle.expectedShaderTree(if(variant=="only") Shader.SolidColor(ColorARGB.Transparent) else shader,
            if(variant=="only") 1f else PAINT_ALPHA,finalBlend=BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val paint=Paint(color=ColorARGB.of(149,16,224,32),shader=fixture.shader,blendMode=BlendMode.DST,antiAlias=false)
        fun Canvas.frame() {
            if(variant=="after") drawImage(image,UNIT,SamplingOptions.NEAREST,paint.copy(blendMode=BlendMode.DIFFERENCE))
            drawImage(image,UNIT,SamplingOptions.NEAREST,paint)
            if(variant=="before") drawImage(image,UNIT,SamplingOptions.NEAREST,paint.copy(blendMode=BlendMode.DIFFERENCE))
        }
        val surface=Surface(1,1)
        surface.canvas { drawRect(UNIT,Paint(color=ColorARGB.Blue,antiAlias=false)) }
        surface.render()
        surface.discardRecordedOperations()
        surface.canvas { frame() }
        val recorder=PictureRecorder()
        recorder.beginRecording(UNIT).frame()
        val picture=recorder.finishRecordingAsPicture()
        fixture.mutate()
        repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,surface.render().pixels) }
        for(replay in listOf(picture,assertNotNull(Picture.fromByteArray(picture.toByteArray())))) {
            val target=Surface(1,1)
            target.canvas { replay.playback(this) }
            repeat(2) { WgslFloatEnvelopeV1Oracle.assertAdmits(wanted,target.render().pixels) }
        }
    }

    companion object {
        private val UNIT=RectF32.ofLTRB(0f,0f,1f,1f)
        private val SOURCE=ColorARGB.of(191,224,64,128)
        private val SECOND=ColorARGB.of(191,64,128,224)
        private val MUTATED=ColorARGB.of(223,96,224,64)
        private const val PAINT_ALPHA=149f/255f
        private val FINALS=listOf(BlendMode.SRC_IN,BlendMode.DIFFERENCE)
        private val DESTINATIONS=listOf(ColorARGB.of(127,16,0,0),ColorARGB.of(127,0,16,0),ColorARGB.of(127,0,0,16)) +
            listOf(1,2,3,4,5,7,15,31,63,127,191).flatMap { a -> listOf(1,2,4,8,32,72,128,224).flatMap { c ->
                listOf(ColorARGB.of(a,c,0,0),ColorARGB.of(a,0,c,0),ColorARGB.of(a,0,0,c)) } }
        @JvmStatic fun solidMasks()=listOf("solid","opacity","runtime").flatMap { v -> listOf(0,127,255).map { Arguments.of(v,it) } }
        @JvmStatic fun rgbaCases()=listOf("child","nested")
        @JvmStatic fun imageOrigins()=listOf("A8","RGBA")
        @JvmStatic fun originBlends()=listOf("A8","RGBA").flatMap { o -> listOf(BlendMode.SRC_OVER,BlendMode.SRC_IN,BlendMode.DIFFERENCE,BlendMode.DST).map { Arguments.of(o,it) } }
        @JvmStatic fun gradients()=listOf("linear","radial","sweep","conical").flatMap { f -> listOf("1","2","16","17","duplicate").map { "$f/$it" } }
        @JvmStatic fun addressing()=listOf("identity","translation","uniform-scale","nonuniform-scale","rotation","shear","reflection","projective","clamp","matrix-clamp","clamp-matrix") + TileMode.entries.map { "tile-$it" } + listOf("tile-DECAL-outside","tile-MIRROR-distinct")
        @JvmStatic fun procedural()=listOf("Blend-shared-ordered","Perlin","Fractal")
        @JvmStatic fun runtimeCases()=listOf("child","nested","image-child","stops-child","noise-child")
        @JvmStatic fun topologyCases()=listOf("A8","RGBA").flatMap { o -> listOf("only","before","after").map { Arguments.of(o,it) } }
    }
}
