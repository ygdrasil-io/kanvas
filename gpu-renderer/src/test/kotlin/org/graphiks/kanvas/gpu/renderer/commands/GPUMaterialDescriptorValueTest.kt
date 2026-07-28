package org.graphiks.kanvas.gpu.renderer.commands

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUMaterialDescriptorValueTest {
    @Test
    fun `runtime effect has deterministic value semantics and recursive child snapshots`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 0.25f,
            0f, 0f, 1f, 0.75f,
        )
        val pixels = byteArrayOf(1, 2, 3, 4)
        val blendBytes = byteArrayOf(5, 6, 7, 8)
        val children = linkedMapOf<String, GPUMaterialDescriptor>(
            "root" to nestedBlend(positions, colors, pixels, blendBytes),
        )
        val uniforms = linkedMapOf<String, GPURuntimeEffectUniformValue>(
            "matrix" to GPURuntimeEffectUniformValue.Matrix3x3(
                (0 until 9).map(Int::toFloat),
            ),
        )
        val descriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.value",
            descriptorVersion = 3,
            uniforms = uniforms,
            children = children,
        )
        val equalDescriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.value",
            descriptorVersion = 3,
            uniforms = mapOf(
                "matrix" to GPURuntimeEffectUniformValue.Matrix3x3(
                    (0 until 9).map(Int::toFloat),
                ),
            ),
            children = mapOf(
                "root" to nestedBlend(
                    floatArrayOf(0f, 1f),
                    floatArrayOf(
                        1f, 0f, 0f, 0.25f,
                        0f, 0f, 1f, 0.75f,
                    ),
                    byteArrayOf(1, 2, 3, 4),
                    byteArrayOf(5, 6, 7, 8),
                ),
            ),
        )
        val initialString = descriptor.toString()

        positions.fill(99f)
        colors.fill(99f)
        pixels.fill(99)
        blendBytes.fill(99)
        uniforms.clear()
        children.clear()

        assertEquals(equalDescriptor, descriptor)
        assertEquals(equalDescriptor.hashCode(), descriptor.hashCode())
        assertEquals(initialString, descriptor.toString())
        assertFalse(initialString.contains("[F@"), initialString)
        assertFalse(initialString.contains("[B@"), initialString)

        val copied = descriptor.copy(descriptorVersion = 4)
        assertEquals("runtime.value", descriptor.component1())
        assertEquals(3, descriptor.component2())
        assertEquals(descriptor.uniforms, descriptor.component3())
        assertEquals(descriptor, descriptor.copy(children = descriptor.component4()))
        assertEquals(4, copied.descriptorVersion)
        assertEquals(descriptor.copy(descriptorVersion = 4), copied)

        val escapedBlend = assertIs<GPUMaterialDescriptor.BlendShader>(
            descriptor.children.getValue("root"),
        )
        val escapedGradient = assertIs<GPUMaterialDescriptor.LinearGradient>(escapedBlend.dst)
        val escapedImage = assertIs<GPUMaterialDescriptor.ImageDraw>(escapedBlend.src)
        escapedGradient.allStopPositions!!.fill(88f)
        escapedGradient.allStopColors!!.fill(88f)
        escapedImage.rgbaPixels.fill(88)
        escapedBlend.uniformBytes.fill(88)

        val retainedBlend = assertIs<GPUMaterialDescriptor.BlendShader>(
            descriptor.children.getValue("root"),
        )
        val retainedGradient = assertIs<GPUMaterialDescriptor.LinearGradient>(retainedBlend.dst)
        val retainedImage = assertIs<GPUMaterialDescriptor.ImageDraw>(retainedBlend.src)
        assertContentEquals(floatArrayOf(0f, 1f), retainedGradient.allStopPositions)
        assertContentEquals(
            floatArrayOf(
                1f, 0f, 0f, 0.25f,
                0f, 0f, 1f, 0.75f,
            ),
            retainedGradient.allStopColors,
        )
        assertContentEquals(byteArrayOf(1, 2, 3, 4), retainedImage.rgbaPixels)
        assertContentEquals(byteArrayOf(5, 6, 7, 8), retainedBlend.uniformBytes)
        assertEquals(equalDescriptor, descriptor)
    }

    @Test
    fun `runtime color filter refusal evidence has recursive immutable value semantics`() {
        val uniforms = linkedMapOf<String, GPURuntimeEffectUniformValue>(
            "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
        )
        val childIdentities = linkedMapOf(
            "input" to "sha256:${"a".repeat(64)}",
        )
        val descriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.parent",
            children = mapOf(
                "refused" to GPUMaterialDescriptor.Unsupported(
                    reason =
                        GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                    originalKind = GPUMaterialKind.SolidColor,
                    source = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                    evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                        effectId = "runtime.filter",
                        uniforms = uniforms,
                        childIdentities = childIdentities,
                    ),
                ),
            ),
        )
        val equalDescriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.parent",
            children = mapOf(
                "refused" to GPUMaterialDescriptor.Unsupported(
                    reason =
                        GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                    originalKind = GPUMaterialKind.SolidColor,
                    source = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
                    evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                        effectId = "runtime.filter",
                        uniforms = mapOf(
                            "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
                        ),
                        childIdentities = mapOf(
                            "input" to "sha256:${"a".repeat(64)}",
                        ),
                    ),
                ),
            ),
        )

        uniforms.clear()
        childIdentities.clear()

        assertEquals(equalDescriptor, descriptor)
        assertEquals(equalDescriptor.hashCode(), descriptor.hashCode())
        assertEquals(equalDescriptor.toString(), descriptor.toString())

        val refused = assertIs<GPUMaterialDescriptor.Unsupported>(
            descriptor.children.getValue("refused"),
        )
        val evidence =
            assertIs<GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter>(
                refused.evidence,
            )
        assertEquals(
            GPURuntimeEffectUniformValue.Float1(0.5f),
            evidence.uniforms.getValue("amount"),
        )
        assertEquals("sha256:${"a".repeat(64)}", evidence.childIdentities.getValue("input"))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.uniforms as MutableMap<String, GPURuntimeEffectUniformValue>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.childIdentities as MutableMap<String, String>).clear()
        }
    }

    @Test
    fun `unsupported recursively snapshots mutable source payloads with complete value semantics`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 0.25f,
            0f, 0f, 1f, 0.75f,
        )
        val pixels = byteArrayOf(1, 2, 3, 4)
        val blendBytes = byteArrayOf(5, 6, 7, 8)
        val descriptor = unsupportedGraph(positions, colors, pixels, blendBytes)
        val equalDescriptor = unsupportedGraph(
            positions = floatArrayOf(0f, 1f),
            colors = floatArrayOf(
                1f, 0f, 0f, 0.25f,
                0f, 0f, 1f, 0.75f,
            ),
            pixels = byteArrayOf(1, 2, 3, 4),
            blendBytes = byteArrayOf(5, 6, 7, 8),
        )
        val initialHash = descriptor.hashCode()
        val initialString = descriptor.toString()

        assertEquals(equalDescriptor, descriptor)
        assertEquals(equalDescriptor.hashCode(), descriptor.hashCode())
        assertEquals(equalDescriptor.toString(), descriptor.toString())
        assertFalse(initialString.contains("[F@"), initialString)
        assertFalse(initialString.contains("[B@"), initialString)

        positions.fill(99f)
        colors.fill(99f)
        pixels.fill(99)
        blendBytes.fill(99)

        assertEquals(equalDescriptor, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialString, descriptor.toString())

        assertEquals(
            GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            descriptor.component1(),
        )
        assertEquals(GPUMaterialKind.ShaderBlend, descriptor.component2())
        assertEquals(descriptor, descriptor.copy())
        assertEquals(descriptor.evidence, descriptor.component4())

        val escapedNested = assertIs<GPUMaterialDescriptor.Unsupported>(
            descriptor.component3(),
        )
        val escapedBlend = assertIs<GPUMaterialDescriptor.BlendShader>(escapedNested.source)
        assertIs<GPUMaterialDescriptor.LinearGradient>(escapedBlend.dst)
            .allStopPositions!!
            .fill(88f)
        assertIs<GPUMaterialDescriptor.LinearGradient>(escapedBlend.dst)
            .allStopColors!!
            .fill(88f)
        escapedBlend.uniformBytes.fill(88)
        val escapedRuntime =
            assertIs<GPUMaterialDescriptor.RuntimeEffect>(escapedBlend.src)
        assertIs<GPUMaterialDescriptor.ImageDraw>(
            escapedRuntime.children.getValue("image"),
        ).rgbaPixels.fill(88)

        assertEquals(equalDescriptor, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialString, descriptor.toString())
        val retainedNested =
            assertIs<GPUMaterialDescriptor.Unsupported>(descriptor.source)
        val retainedBlend =
            assertIs<GPUMaterialDescriptor.BlendShader>(retainedNested.source)
        assertContentEquals(
            floatArrayOf(0f, 1f),
            assertIs<GPUMaterialDescriptor.LinearGradient>(
                retainedBlend.dst,
            ).allStopPositions,
        )
        assertContentEquals(byteArrayOf(5, 6, 7, 8), retainedBlend.uniformBytes)
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4),
            assertIs<GPUMaterialDescriptor.ImageDraw>(
                assertIs<GPUMaterialDescriptor.RuntimeEffect>(
                    retainedBlend.src,
                ).children.getValue("image"),
            ).rgbaPixels,
        )
    }

    @Test
    fun `unsupported diamond dag value operations are bounded and alias independent`() {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Thread(
            {
                try {
                    val sharedSource = runtimeDiamond(depth = 60)
                    val sharedRoot = GPUMaterialDescriptor.RuntimeEffect(
                        effectId = "runtime.diamond.root",
                        children = linkedMapOf(
                            "left" to sharedSource,
                            "right" to sharedSource,
                        ),
                    )
                    val duplicatedRoot = GPUMaterialDescriptor.RuntimeEffect(
                        effectId = "runtime.diamond.root",
                        children = linkedMapOf(
                            "left" to runtimeDiamond(depth = 60),
                            "right" to runtimeDiamond(depth = 60),
                        ),
                    )
                    val shared = GPUMaterialDescriptor.Unsupported(
                        reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
                        originalKind = GPUMaterialKind.RuntimeEffect,
                        source = sharedRoot,
                    )
                    val duplicated = GPUMaterialDescriptor.Unsupported(
                        reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
                        originalKind = GPUMaterialKind.RuntimeEffect,
                        source = duplicatedRoot,
                    )

                    assertEquals(duplicated, shared)
                    assertEquals(duplicated.hashCode(), shared.hashCode())
                    val sharedString = shared.toString()
                    assertEquals(duplicated.toString(), sharedString)
                    assertTrue(
                        sharedString.length < 16_384,
                        "Canonical DAG string unexpectedly expanded to ${sharedString.length} chars",
                    )
                } catch (caught: Throwable) {
                    failure.set(caught)
                } finally {
                    completed.countDown()
                }
            },
            "material-descriptor-diamond-value-semantics",
        ).apply {
            isDaemon = true
            start()
        }

        assertTrue(
            completed.await(5, TimeUnit.SECONDS),
            "Diamond DAG value operations did not complete within 5 seconds",
        )
        failure.get()?.let { throw it }
    }

    private fun unsupportedGraph(
        positions: FloatArray,
        colors: FloatArray,
        pixels: ByteArray,
        blendBytes: ByteArray,
    ): GPUMaterialDescriptor.Unsupported {
        val blend = GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = GPUMaterialDescriptor.LinearGradient(
                startX = 0f,
                startY = 0f,
                endX = 1f,
                endY = 1f,
                startR = 1f,
                startG = 0f,
                startB = 0f,
                startA = 0.25f,
                endR = 0f,
                endG = 0f,
                endB = 1f,
                endA = 0.75f,
                allStopPositions = positions,
                allStopColors = colors,
            ),
            src = GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.source",
                uniforms = mapOf(
                    "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
                ),
                children = mapOf(
                    "image" to GPUMaterialDescriptor.ImageDraw(
                        imageSourceId = "nested",
                        imageWidth = 1,
                        imageHeight = 1,
                        rgbaPixels = pixels,
                    ),
                ),
            ),
            wgslCombined = "nested-wgsl",
            uniformBytes = blendBytes,
        )
        val nested = GPUMaterialDescriptor.Unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
            originalKind = GPUMaterialKind.ShaderBlend,
            source = blend,
        )
        return GPUMaterialDescriptor.Unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            originalKind = GPUMaterialKind.ShaderBlend,
            source = nested,
            evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                effectId = "runtime.filter",
                uniforms = mapOf(
                    "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
                ),
                childIdentities = mapOf(
                    "input" to "sha256:${"a".repeat(64)}",
                ),
            ),
        )
    }

    private fun runtimeDiamond(depth: Int): GPUMaterialDescriptor {
        var descriptor: GPUMaterialDescriptor =
            GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f)
        repeat(depth) { index ->
            val sharedChild = descriptor
            descriptor = GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.diamond.$index",
                descriptorVersion = index,
                children = linkedMapOf(
                    "left" to sharedChild,
                    "right" to sharedChild,
                ),
            )
        }
        return descriptor
    }

    private fun nestedBlend(
        positions: FloatArray,
        colors: FloatArray,
        pixels: ByteArray,
        blendBytes: ByteArray,
    ): GPUMaterialDescriptor.BlendShader =
        GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = GPUMaterialDescriptor.LinearGradient(
                startX = 0f,
                startY = 0f,
                endX = 1f,
                endY = 1f,
                startR = 1f,
                startG = 0f,
                startB = 0f,
                startA = 0.25f,
                endR = 0f,
                endG = 0f,
                endB = 1f,
                endA = 0.75f,
                allStopPositions = positions,
                allStopColors = colors,
            ),
            src = GPUMaterialDescriptor.ImageDraw(
                imageSourceId = "nested",
                imageWidth = 1,
                imageHeight = 1,
                rgbaPixels = pixels,
            ),
            wgslCombined = "nested-wgsl",
            uniformBytes = blendBytes,
        )
}
