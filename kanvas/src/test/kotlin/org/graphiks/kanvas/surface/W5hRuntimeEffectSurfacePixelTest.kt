@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.RuntimeEffectResourceBinding
import org.graphiks.kanvas.pipeline.RuntimeEffectResourceBindings
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformValue
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.render.ir.RuntimeSamplerTypeV1
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.GraphLimits
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.PaintSceneAdapter
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.RuntimeEffectDescriptor
import org.graphiks.kanvas.render.ir.RuntimeEffectId
import org.graphiks.kanvas.render.ir.RuntimeEffectAbi
import org.graphiks.kanvas.render.ir.RuntimeUniformBlockV1
import org.graphiks.kanvas.render.ir.RuntimeUniformSlotV2
import org.graphiks.kanvas.render.ir.RuntimeUniformType
import org.graphiks.kanvas.render.ir.RuntimeUniformValue
import org.graphiks.kanvas.render.ir.RuntimeChildSlotV2
import org.graphiks.kanvas.render.ir.RuntimeChildType
import org.graphiks.kanvas.render.ir.RuntimeMaterialChild
import org.graphiks.kanvas.gpu.renderer.planning.GpuRenderContext
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfacePlanResult
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.*

class W5hRuntimeEffectSurfacePixelTest {
    @Test fun priorW5SourcesRetainByteExactControls() {
        val red=Shader.SolidColor(ColorARGB.Red)
        val controls=listOf(
            red to listOf(255,0,0,255),
            Shader.LinearGradient(org.graphiks.math.geometry.Point2F32(0f,0f),org.graphiks.math.geometry.Point2F32(1f,0f),
                listOf(org.graphiks.kanvas.paint.GradientStop(0f,ColorARGB.Red),org.graphiks.kanvas.paint.GradientStop(1f,ColorARGB.Red))) to listOf(255,0,0,255),
            Shader.Image(Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1))) to listOf(255,0,0,255),
            Shader.WithColorFilter(red,org.graphiks.kanvas.paint.ColorFilter.Matrix(org.graphiks.math.color.ColorMatrixF32.ofIdentity())) to listOf(255,0,0,255),
            Shader.Blend(BlendMode.SRC_OVER,red,Shader.SolidColor(ColorARGB.Blue)) to listOf(0,0,255,255),
            Shader.PerlinNoise(.125f,.25f,0,7,null) to listOf(0,0,0,0))
        for((shader,wanted) in controls) {
            val surface=Surface(1,1)
            surface.canvas { draw(this,shader,0) }
            repeat(2) { assertEquals(wanted,surface.render().pixels.take(4).map { it.toInt() }) }
        }
    }
    @ParameterizedTest(name = "route {0}, alpha {1}")
    @CsvSource("0,1.0", "0,0.5", "1,1.0", "1,0.5", "2,1.0", "2,0.5")
    fun childOpacityUsesUniformAndPremultiplication(route: Int, alpha: Float) {
        val surface = Surface(1, 1)
        surface.canvas { draw(this, effect(alpha), route) }
        val expected = expected(alpha.toDouble())
        repeat(2) { assertPixel(surface.render(), expected) }
    }

    @Test fun nestedOpacityKeepsNoncommutativeBlendChildOrder() {
        val other = ColorARGB.of(149, 32, 160, 224)
        val child = Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(COLOR), Shader.SolidColor(other))
        val shader = effect(.75f, effect(.5f, child))
        val wanted = W5hRuntimeEffectCpuOracle.attachment(W5hRuntimeEffectCpuOracle.opacity(
            W5hRuntimeEffectCpuOracle.opacity(W5hRuntimeEffectCpuOracle.srcOver(
                W5hRuntimeEffectCpuOracle.linearPremul(COLOR), W5hRuntimeEffectCpuOracle.linearPremul(other)), .5), .75))
        val reversed = W5hRuntimeEffectCpuOracle.attachment(W5hRuntimeEffectCpuOracle.opacity(
            W5hRuntimeEffectCpuOracle.srcOver(W5hRuntimeEffectCpuOracle.linearPremul(other),
                W5hRuntimeEffectCpuOracle.linearPremul(COLOR)), .375))
        assertTrue(wanted.indices.any { wanted[it].intersect(reversed[it]).isEmpty() })
        for (route in 0..2) {
            val surface = Surface(1, 1)
            surface.canvas { draw(this, shader, route) }
            repeat(2) { assertPixel(surface.render(), wanted) }
        }
    }

    @Test fun capturedChildrenAndUniformInputsSurviveMutationAndPictureReplay() {
        val values = linkedMapOf<String, UniformValue>("alpha" to UniformValue.F1(.5f))
        val matrix=org.graphiks.math.color.ColorMatrixF32.ofIdentity()
        val children = linkedMapOf("child" to Shader.WithColorFilter(Shader.SolidColor(COLOR),
            org.graphiks.kanvas.paint.ColorFilter.Matrix(matrix)) as Shader)
        val uniforms = UniformBlock { entries.putAll(values) }
        val shader = Shader.RuntimeEffect(builtin(), uniforms, children)
        val surface = Surface(1, 1)
        surface.canvas { draw(this, shader, 0) }
        val recorder = PictureRecorder()
        draw(recorder.beginRecording(BOUNDS), shader, 0)
        val picture = recorder.finishRecordingAsPicture()
        values["alpha"] = UniformValue.F1(1f)
        children["child"] = Shader.SolidColor(ColorARGB.Green)
        matrix.setScale(0f,0f,0f,0f)
        repeat(2) { assertPixel(surface.render(), expected(.5)) }
        for (replay in listOf(picture, assertNotNull(Picture.fromByteArray(picture.toByteArray())))) {
            val target = Surface(1, 1)
            target.canvas { replay.playback(this) }
            repeat(2) { assertPixel(target.render(), expected(.5)) }
        }
    }

    @Test fun extraStorageTextureAndSamplerRefuseAndSameSurfaceRecovers() {
        val resources = listOf(
            RuntimeEffectResourceBinding.StorageRead.copyOf(byteArrayOf(1, 2, 3, 4)),
            RuntimeEffectResourceBinding.SampledTexture(Image.fromPixels(1, 1, byteArrayOf(0, 0, 0, 0))),
            RuntimeEffectResourceBinding.Sampler(RuntimeSamplerTypeV1.FILTERING))
        for (resource in resources) {
            val surface = Surface(1, 1)
            val failure = assertFailsWith<IllegalArgumentException> {
                surface.canvas { draw(this, builtin().makeShader(UniformBlock { float1("alpha", .5f) },
                    mapOf("child" to Shader.SolidColor(COLOR)),
                    RuntimeEffectResourceBindings.of(mapOf("extra" to resource))), 0) }
            }
            assertEquals("invalid.material.runtime_effect.resource_extra", failure.message)
            surface.canvas { draw(this, effect(.5f), 0) }
            assertPixel(surface.render(), expected(.5))
        }
    }

    @Test fun invalidChildrenAndUniformShapesRefuseAndSameSurfaceRecovers() {
        val good = UniformBlock { float1("alpha", .5f) }
        val validChild = mapOf("child" to Shader.SolidColor(COLOR))
        val cases = listOf(
            Triple(good, emptyMap(), "invalid.material.runtime_effect.cpu_children"),
            Triple(good, validChild + ("extra" to Shader.SolidColor(COLOR)), "invalid.material.runtime_effect.cpu_children"),
            Triple(good, mapOf("wrong" to Shader.SolidColor(COLOR)), "invalid.material.runtime_effect.cpu_children"),
            Triple(UniformBlock.EMPTY, validChild, "invalid.material.runtime_effect.cpu_uniforms"),
            Triple(UniformBlock { int1("alpha", 1) }, validChild, "invalid.material.runtime_effect.cpu_uniforms"),
            Triple(UniformBlock { float2("alpha", .5f, .5f) }, validChild, "invalid.material.runtime_effect.cpu_uniforms"),
            Triple(UniformBlock { float1("alpha", .5f); float1("extra", 1f) }, validChild, "invalid.material.runtime_effect.cpu_uniforms"))
        for ((uniforms, children, category) in cases) {
            val surface = Surface(1, 1)
            val failure = assertFails {
                surface.canvas { draw(this, builtin().makeShader(uniforms, children), 0) }
                surface.render()
            }
            assertEquals(category,failure.message.orEmpty().substringBefore(':'))
            surface.discardRecordedOperations()
            surface.canvas { draw(this, effect(.5f), 0) }
            assertPixel(surface.render(), expected(.5))
        }
    }

    @Test fun recordingUniformBudgetIsCumulativeAndDiscardPreservesCanvasState() {
        val surface=Surface(2,1,captureLimits=SceneCaptureLimits(maxRuntimeUniformBytesI64=4))
        surface.canvas { translate(1f,0f); draw(this,effect(.5f),0) }
        val failure=assertFailsWith<org.graphiks.kanvas.canvas.SceneRecordingLimitException> { surface.canvas { draw(this,effect(.5f),0) } }
        assertEquals("unsupported.material.runtime_effect.budget",failure.diagnostic.code.value)
        surface.discardRecordedOperations()
        surface.canvas { draw(this,effect(.5f),0) }
        repeat(2) {
            val pixels=surface.render().pixels
            assertTrue(pixels.take(4).all { it == 0.toUByte() })
            expected(.5).forEachIndexed { channel,codes -> assertTrue(pixels[4+channel].toInt() in codes) }
        }
    }

    @Test fun nonfiniteAndOutOfRangeUniformsRefuseAndSameSurfaceRecovers() {
        for (alpha in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f)) {
            val surface = Surface(1, 1)
            val failure = assertFails {
                surface.canvas { draw(this, effect(alpha), 0) }
                surface.render()
            }
            assertEquals("invalid.material.runtime_effect.cpu_uniforms", failure.message.orEmpty().substringBefore(':'))
            surface.discardRecordedOperations()
            surface.canvas { draw(this, effect(.5f), 0) }
            assertPixel(surface.render(), expected(.5))
        }
    }

    @Suppress("DEPRECATION")
    @Test fun compiledV0RefusesAndSameSurfaceRecovers() {
        val legacy = SceneRecordingScope.recordingOnly {
            RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        }.makeShader(UniformBlock.EMPTY)
        val surface = Surface(1, 1)
        surface.canvas { draw(this, legacy, 0) }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.runtime_effect.unregistered_semantics", failure.message.orEmpty().substringBefore(':'))
        surface.discardRecordedOperations()
        surface.canvas { draw(this, effect(.5f), 0) }
        assertPixel(surface.render(), expected(.5))
    }

    @Test fun uniformByteDepthAndNodeBudgetsRefuseBeforePublicationAndRecover() {
        val deep = effect(.75f, effect(.5f))
        val pair = Shader.Blend(BlendMode.SRC_OVER, effect(.5f), effect(.75f))
        val cases = listOf(
            SceneCaptureLimits(maxRuntimeUniformBytesI64 = 4) to deep,
            SceneCaptureLimits(maxDepth = 2) to deep,
            SceneCaptureLimits(graphLimits = GraphLimits(maxNodes = 3)) to pair)
        for ((limits, shader) in cases) {
            val surface = Surface(1, 1, captureLimits = limits)
            val failure = assertFails {
                surface.canvas { draw(this, shader, 0) }
                surface.render()
            }
            assertEquals("unsupported.material.runtime_effect.budget",failure.message.orEmpty().substringBefore(':'))
            surface.discardRecordedOperations()
            surface.canvas { draw(this, effect(.5f), 0) }
            assertPixel(surface.render(), expected(.5))
        }
    }

    @ParameterizedTest(name = "{0} budget precedes unregistered semantics")
    @CsvSource("depth", "nodes", "uniform-bytes")
    fun planningRejectsOversizedRuntimeSubtreeBeforeUnregisteredSemantics(limit: String) {
        var child: MaterialNode = MaterialNode.Solid(COLOR)
        if (limit == "depth") repeat(64) { child = MaterialNode.Opacity(child, .5f) }
        else if (limit == "nodes") repeat(12) { child = MaterialNode.Blend(org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER, child, child) }
        else {
            // 2048 occurrences of a 32,832-byte block exceed 64 MiB, while the
            // whole tree has exactly 4096 nodes and shares one immutable uniform owner.
            val slots = List(513) { RuntimeUniformSlotV2("u$it", RuntimeUniformType.MAT4X4, it * 64, 64, 16, 1, 0) }
            val largeBlock = RuntimeEffectDescriptor.of(RuntimeEffectId("test.unregistered-large-block"),
                RuntimeEffectAbi.SHADER, 1, RuntimeUniformBlockV1.of(slots, 513 * 64), emptyList())
            val matrix = RuntimeUniformValue.M4(FloatArray(16))
            child = MaterialNode.RuntimeEffect.of(largeBlock, slots.associate { it.name to matrix }, emptyList())
            repeat(11) { child = MaterialNode.Blend(org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER, child, child) }
        }
        // Public immutable IR deliberately bypasses Canvas capture, exercising planning itself.
        val descriptor = RuntimeEffectDescriptor.of(RuntimeEffectId("test.unregistered-budget-order"),
            RuntimeEffectAbi.SHADER, 1, RuntimeUniformBlockV1.of(emptyList(), 0),
            listOf(RuntimeChildSlotV2("child", RuntimeChildType.SHADER, false)))
        val material = MaterialNode.RuntimeEffect.of(descriptor, emptyMap(), listOf(RuntimeMaterialChild("child", child)))
        val paint = PaintSceneAdapter.capture(Paint(antiAlias = false)).paint.copy(shader = material)
        val extent = SceneExtent(1, 1)
        val scene = SceneSnapshot.of(extent, ColorSpace.SRGB, listOf(SceneCommand.Draw(DrawNode(
            GeometryNode.Rect.of(BOUNDS), material, CoverageRequest.HARD_EDGE, ClipStackNode.Empty,
            BlendNode.SrcOver, EffectStack.Empty, Matrix3x3F32.Identity, paint = paint))))
        GpuRenderContext.createProduction().use { context ->
            val result = context.planSurfaceExecutor().plan(scene, RenderTargetDescriptor(extent, ColorSpace.SRGB),
                frameLocalBudgetBytes = 64L * 1024L * 1024L)
            val refusal = assertIs<GpuPlanSurfacePlanResult.Terminal>(result)
            assertEquals("unsupported.material.runtime_effect.budget", refusal.diagnostics.single().code.value)
        }
    }

    private fun builtin() = assertNotNull(RuntimeEffect.registered("kanvas.runtime.child-opacity", 1))
    private fun effect(alpha: Float, child: Shader = Shader.SolidColor(COLOR)): Shader =
        builtin().makeShader(UniformBlock { float1("alpha", alpha) }, mapOf("child" to child))
    private fun expected(alpha: Double) = W5hRuntimeEffectCpuOracle.attachment(
        W5hRuntimeEffectCpuOracle.opacity(W5hRuntimeEffectCpuOracle.linearPremul(COLOR), alpha))

    private fun draw(canvas: Canvas, shader: Shader, route: Int) {
        val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
        if (route == 0) canvas.drawRect(BOUNDS, paint) else canvas.drawPath(Path().apply {
            if (route == 1) { moveTo(-10f, -10f); lineTo(20f, -10f); lineTo(-10f, 20f) }
            else { moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(5f, 5f); lineTo(2f, 2f); lineTo(-1f, 5f) }
            close()
        }, paint)
    }
    private fun assertPixel(result: RenderResult, expected: List<IntRange>) {
        expected.forEachIndexed { channel, codes -> assertTrue(result.pixels[channel].toInt() in codes,
            "channel $channel: ${result.pixels[channel]} outside $codes") }
    }
    companion object {
        private val COLOR = ColorARGB.of(173, 192, 64, 128)
        private val BOUNDS = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }
}
