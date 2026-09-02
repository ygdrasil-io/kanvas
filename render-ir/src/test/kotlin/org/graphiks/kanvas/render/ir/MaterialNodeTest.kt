package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.matrix.Matrix3x3F32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class MaterialNodeTest {
    private val redStop = GradientStop(0f, ColorARGB.Red)
    private val blueStop = GradientStop(1f, ColorARGB.Blue)

    @Test
    fun `materials snapshot stops pixels uniforms children and mutable outputs`() {
        val stops = mutableListOf(redStop, blueStop)
        val pixels = byteArrayOf(1, 2, 3, 4)
        val image = ImageResourceSnapshot.rgba8(1, 1, pixels, ColorSpace.SRGB)
        val uniforms = linkedMapOf<String, RuntimeUniformValue>("matrix" to RuntimeUniformValue.M4(FloatArray(16) { (it + 1).toFloat() }))
        val children = mutableListOf(
            RuntimeMaterialChild("child", MaterialNode.Solid(ColorARGB.Green)),
        )
        val gradient = MaterialNode.LinearGradient.of(
            start = Point2F32(1f, 2f),
            end = Point2F32(3f, 4f),
            stops = stops,
        )
        val runtime = MaterialNode.RuntimeEffect.of(
            descriptor = runtimeDescriptor(),
            uniforms = uniforms,
            children = children,
        )
        val material = MaterialNode.Blend(
            BlendMode.SRC_OVER,
            gradient,
            MaterialNode.ImageSample(image),
        )
        val materialId = material.canonicalId
        val runtimeId = runtime.canonicalId
        val imageId = image.canonicalId

        stops.clear()
        pixels.fill(0)
        (uniforms.getValue("matrix") as RuntimeUniformValue.M4).copyValues()[0] = 9f
        uniforms["other"] = RuntimeUniformValue.F1(5f)
        children.clear()
        image.copyPixels()[0] = 9
        runtime.uniforms()["matrix"]!!.let { (it as RuntimeUniformValue.M4).copyValues()[0] = 9f }

        assertEquals(listOf(redStop, blueStop), gradient.stops())
        assertContentEquals(byteArrayOf(1, 2, 3, 4), image.copyPixels())
        assertContentEquals(FloatArray(16) { (it + 1).toFloat() }, (runtime.uniforms().getValue("matrix") as RuntimeUniformValue.M4).copyValues())
        assertEquals(1, runtime.childCount)
        assertEquals("child", runtime.childAt(0).name)
        assertEquals(materialId, material.canonicalId)
        assertEquals(runtimeId, runtime.canonicalId)
        assertEquals(imageId, image.canonicalId)
        assertFailsWith<UnsupportedOperationException> { (gradient.stops() as MutableList<GradientStop>).clear() }
        assertFailsWith<UnsupportedOperationException> { (runtime.uniforms() as MutableMap<String, RuntimeUniformValue>).clear() }
        assertFailsWith<UnsupportedOperationException> { (runtime.iterator() as MutableIterator<RuntimeMaterialChild>).remove() }
    }

    @Test
    fun `every material family preserves its public semantic fields`() {
        val image = ImageResourceSnapshot.rgba8(1, 1, byteArrayOf(1, 2, 3, 4), ColorSpace.DISPLAY_P3)
        val linear = MaterialNode.LinearGradient.of(Point2F32(1f, 2f), Point2F32(3f, 4f), listOf(redStop, blueStop), TileMode.MIRROR, ColorInterpolation.OKLAB)
        val radial = MaterialNode.RadialGradient.of(Point2F32(1f, 2f), 4f, listOf(redStop), TileMode.REPEAT, ColorInterpolation.LINEAR)
        val sweep = MaterialNode.SweepGradient.of(Point2F32(1f, 2f), 3f, 270f, listOf(blueStop), TileMode.DECAL, ColorInterpolation.HSL)
        val conical = MaterialNode.ConicalGradient.of(Point2F32(1f, 2f), 3f, Point2F32(4f, 5f), 6f, listOf(redStop), TileMode.CLAMP, ColorInterpolation.OKLCH)
        val sampled = MaterialNode.ImageSample(image, TileMode.REPEAT, TileMode.MIRROR, ImageSampling.Cubic(1f / 3f, 1f / 3f))
        val blended = MaterialNode.Blend(BlendMode.SCREEN, linear, radial)
        val runtime = MaterialNode.RuntimeEffect.of(
            RuntimeEffectDescriptor.of(
                RuntimeEffectId("family-effect"), RuntimeEffectAbi.SHADER,
                RuntimeUniformLayout.of(listOf(RuntimeUniformSlot("f", 0, RuntimeUniformType.FLOAT, 0))), emptyList(),
            ),
            mapOf("f" to RuntimeUniformValue.F1(2f)), emptyList(),
        )
        val local = MaterialNode.WithLocalMatrix(sweep, Matrix3x3F32(tx = 2f, ty = 3f))
        val filtered = MaterialNode.WithColorFilter(conical, ColorFilterNode.Lighting(ColorARGB.Red, ColorARGB.Blue))
        val opacity = MaterialNode.Opacity(sampled, 0.25f)
        val perlin = MaterialNode.PerlinNoise(2f, 3f, 4, 5, SizeF32(6f, 7f))
        val fractal = MaterialNode.FractalNoise(2f, 3f, 4, 5, null)
        val working = MaterialNode.WithWorkingColorSpace(blended, ColorInterpolation.LINEAR)
        val clamp = MaterialNode.CoordClamp(runtime, RectF32(1f, 2f, 3f, 4f))

        assertEquals(TileMode.MIRROR, linear.tileMode)
        assertEquals(4f, radial.radius)
        assertEquals(270f, sweep.endAngle)
        assertEquals(6f, conical.endRadius)
        assertEquals(TileMode.MIRROR, sampled.tileModeY)
        assertEquals(BlendMode.SCREEN, blended.mode)
        assertEquals(RuntimeEffectId("family-effect"), runtime.descriptor.id)
        assertEquals(Matrix3x3F32(tx = 2f, ty = 3f), local.matrix)
        assertEquals(ColorARGB.Blue, (filtered.filter as ColorFilterNode.Lighting).add)
        assertEquals(0.25f, opacity.alpha)
        assertEquals(SizeF32(6f, 7f), perlin.tileSize)
        assertEquals(null, fractal.tileSize)
        assertEquals(ColorInterpolation.LINEAR, working.interpolation)
        assertEquals(RectF32(1f, 2f, 3f, 4f), clamp.copySubset())
        assertNotEquals(perlin.canonicalId, fractal.canonicalId)
    }

    @Test
    fun `material graph limits are exact and report before backend planning`() {
        val solid = MaterialNode.Solid(ColorARGB.Red)
        val oneLevel = MaterialNode.WithLocalMatrix(solid, Matrix3x3F32.Identity)
        val twoLevels = MaterialNode.WithLocalMatrix(oneLevel, Matrix3x3F32.Identity)

        assertIs<GraphValidationResult.Valid>(MaterialGraph.validate(solid, GraphLimits(maxDepth = 1, maxNodes = 1)))
        assertIs<GraphValidationResult.Valid>(MaterialGraph.validate(oneLevel, GraphLimits(maxDepth = 2, maxNodes = 2)))
        val depthFailure = assertIs<GraphValidationResult.DepthLimitExceeded>(
            MaterialGraph.validate(twoLevels, GraphLimits(maxDepth = 2, maxNodes = 3)),
        )
        assertEquals(3, depthFailure.observedDepth)
        val nodeFailure = assertIs<GraphValidationResult.NodeLimitExceeded>(
            MaterialGraph.validate(MaterialNode.Blend(BlendMode.SRC_OVER, solid, solid), GraphLimits(maxDepth = 2, maxNodes = 2)),
        )
        assertEquals(3, nodeFailure.observedNodes)
    }

    @Test
    fun `shared material dag keeps canonical identity bounded before graph rejection`() {
        var shared: MaterialNode = MaterialNode.Solid(ColorARGB.Red)
        repeat(1_024) {
            shared = MaterialNode.Blend(BlendMode.SRC_OVER, shared, shared)
        }

        assertEquals(64, shared.canonicalId.value.length)
        val depthBounded = assertIs<MaterialGraphBuildResult.Rejected>(
            MaterialGraph.bounded(shared, GraphLimits(maxDepth = 1_024, maxNodes = 4_096)),
        )
        val depthFailure = assertIs<GraphValidationResult.DepthLimitExceeded>(depthBounded.validation)
        assertEquals(1_025, depthFailure.observedDepth)
        val nodeBounded = assertIs<MaterialGraphBuildResult.Rejected>(
            MaterialGraph.bounded(shared, GraphLimits(maxDepth = 2_048, maxNodes = 16)),
        )
        val nodeFailure = assertIs<GraphValidationResult.NodeLimitExceeded>(nodeBounded.validation)
        assertEquals(17, nodeFailure.observedNodes)
    }

    @Test
    fun `runtime metadata and ordered child collections remain owned by the graph`() {
        val slots = mutableListOf(RuntimeUniformSlot("value", 0, RuntimeUniformType.FLOAT, 1))
        val childSlots = mutableListOf(RuntimeChildSlot("child", RuntimeChildType.SHADER))
        val attributes = mutableListOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32X2, 0, 0))
        val descriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("owned-effect"), RuntimeEffectAbi.SHADER, RuntimeUniformLayout.of(slots), childSlots,
            RuntimeVertexLayout.of(8, attributes),
        )
        val uniformValues = floatArrayOf(1f)
        val uniforms = linkedMapOf<String, RuntimeUniformValue>("value" to RuntimeUniformValue.F1(uniformValues[0]))
        val children = mutableListOf(RuntimeMaterialChild("child", MaterialNode.Solid(ColorARGB.Red)))
        val runtime = MaterialNode.RuntimeEffect.of(descriptor, uniforms, children)
        val clipEntries = mutableListOf(
            ClipEntry(GeometryNode.Rect.of(RectF32(0f, 0f, 1f, 1f)), ClipOperation.INTERSECT),
        )
        val clip = ClipStackNode.Operations.of(clipEntries) as ClipStackNode.Operations
        val identity = runtime.canonicalId

        slots.clear()
        childSlots.clear()
        attributes.clear()
        uniformValues.fill(0f)
        uniforms.clear()
        children.clear()
        clipEntries.clear()
        uniformValues[0] = 9f

        assertEquals(1, descriptor.uniformLayout.slotCount)
        assertEquals(1, descriptor.childSlotCount)
        assertEquals(1, descriptor.vertexLayout!!.attributeCount)
        assertEquals(RuntimeUniformValue.F1(1f), runtime.uniforms().getValue("value"))
        assertEquals(1, runtime.childCount)
        assertEquals(1, clip.entryCount)
        assertEquals(identity, runtime.canonicalId)
        assertFailsWith<UnsupportedOperationException> { (descriptor.iterator() as MutableIterator<RuntimeChildSlot>).remove() }
        assertFailsWith<UnsupportedOperationException> { (clip.iterator() as MutableIterator<ClipEntry>).remove() }
    }

    private fun runtimeDescriptor(): RuntimeEffectDescriptor = RuntimeEffectDescriptor.of(
        id = RuntimeEffectId("effect-1"),
        abi = RuntimeEffectAbi.SHADER,
        uniformLayout = RuntimeUniformLayout.of(
            listOf(RuntimeUniformSlot("matrix", 0, RuntimeUniformType.MAT4X4, 0)),
        ),
        childSlots = listOf(RuntimeChildSlot("child", RuntimeChildType.SHADER)),
        vertexLayout = RuntimeVertexLayout.of(
            stride = 8,
            attributes = listOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32X2, 0, 0)),
        ),
    )
}
