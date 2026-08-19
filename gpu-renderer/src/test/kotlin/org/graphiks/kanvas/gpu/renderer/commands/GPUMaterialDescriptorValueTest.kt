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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GPUMaterialDescriptorValueTest {
    @Test
    fun `radial and sweep descriptor equality and hash include gradient facts`() {
        val radial = GPUMaterialDescriptor.RadialGradient(
            centerX = 1f,
            centerY = 2f,
            radius = 3f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        )
        val sweep = GPUMaterialDescriptor.SweepGradient(
            centerX = 1f,
            centerY = 2f,
            startAngle = 0f,
            endAngle = 360f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        )

        val radialWithFacts = radial.withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(interpolation = "linear"),
        )
        val sweepWithFacts = sweep.withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(
                localMatrix = listOf(
                    1f, 0f, 2f,
                    0f, 1f, 3f,
                    0f, 0f, 1f,
                ),
            ),
        )

        assertNotEquals(radial, radialWithFacts)
        assertNotEquals(sweep, sweepWithFacts)
    }

    @Test
    fun `nested blend and unsupported descriptors distinguish NaN payloads`() {
        val firstNaN = Float.fromBits(0x7fc00001)
        val secondNaN = Float.fromBits(0x7fc00002)
        fun blend(gradient: GPUMaterialDescriptor.RadialGradient) = GPUMaterialDescriptor.BlendShader(
            mode = "SRC_OVER",
            dst = gradient,
            src = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 1f),
            wgslCombined = "blend",
            uniformBytes = byteArrayOf(1, 2, 3, 4),
        )
        fun unsupported(blend: GPUMaterialDescriptor.BlendShader) = GPUMaterialDescriptor.Unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.LOCAL_MATRIX,
            originalKind = GPUMaterialKind.ShaderBlend,
            source = blend,
        )

        val firstBlend = blend(
            radialWithStops(
                positions = floatArrayOf(0f, 1f),
                colors = floatArrayOf(firstNaN, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
            ),
        )
        val secondBlend = blend(
            radialWithStops(
                positions = floatArrayOf(0f, 1f),
                colors = floatArrayOf(secondNaN, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
            ),
        )
        val firstUnsupported = unsupported(firstBlend)
        val secondUnsupported = unsupported(secondBlend)

        assertNotEquals(firstBlend, secondBlend)
        assertNotEquals(firstBlend.hashCode(), secondBlend.hashCode())
        assertNotEquals(firstUnsupported, secondUnsupported)
        assertNotEquals(firstUnsupported.hashCode(), secondUnsupported.hashCode())
    }

    @Test
    fun `radial gradient stops remain immutable snapshots across copies and facts`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        )
        val descriptor = radialWithStops(positions, colors)
        val copy = descriptor.copy()
        val factsCopy = descriptor.withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(
                interpolation = descriptor.interpolation,
                localMatrix = descriptor.localMatrix,
            ),
        )
        val expectedHash = descriptor.hashCode()
        val expectedText = descriptor.toString()

        positions[0] = 0.25f
        colors[0] = 0.25f
        descriptor.allStopPositions!!.also { it[0] = 0.5f }
        copy.allStopColors!!.also { it[0] = 0.5f }
        factsCopy.allStopPositions!!.also { it[0] = 0.5f }

        assertContentEquals(floatArrayOf(0f, 1f), descriptor.allStopPositions)
        assertContentEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
            descriptor.allStopColors,
        )
        assertEquals(descriptor, copy)
        assertEquals(descriptor, factsCopy)
        assertEquals(expectedHash, descriptor.hashCode())
        assertEquals(expectedText, descriptor.toString())
        assertEquals(expectedText, copy.toString())
        assertEquals(expectedText, factsCopy.toString())
    }

    @Test
    fun `sweep gradient stops remain immutable snapshots across copies and facts`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        )
        val descriptor = sweepWithStops(positions, colors)
        val copy = descriptor.copy()
        val factsCopy = descriptor.withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(
                interpolation = descriptor.interpolation,
                localMatrix = descriptor.localMatrix,
            ),
        )
        val expectedHash = descriptor.hashCode()
        val expectedText = descriptor.toString()

        positions[1] = 0.25f
        colors[4] = 0.25f
        descriptor.allStopColors!!.also { it[0] = 0.5f }
        copy.allStopPositions!!.also { it[0] = 0.5f }
        factsCopy.allStopColors!!.also { it[0] = 0.5f }

        assertContentEquals(floatArrayOf(0f, 1f), descriptor.allStopPositions)
        assertContentEquals(
            floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
            descriptor.allStopColors,
        )
        assertEquals(descriptor, copy)
        assertEquals(descriptor, factsCopy)
        assertEquals(expectedHash, descriptor.hashCode())
        assertEquals(expectedText, descriptor.toString())
        assertEquals(expectedText, copy.toString())
        assertEquals(expectedText, factsCopy.toString())
    }

    @Test
    fun `radial and sweep descriptors have deterministic canonical toString values`() {
        val radial = radialWithStops(floatArrayOf(0f, 1f), floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val radialCopy = radialWithStops(floatArrayOf(0f, 1f), floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val sweep = sweepWithStops(floatArrayOf(0f, 1f), floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))
        val sweepCopy = sweepWithStops(floatArrayOf(0f, 1f), floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 0f, 1f, 1f,
        ))

        assertEquals(radial, radialCopy)
        assertEquals(radial.toString(), radialCopy.toString())
        assertTrue(radial.toString().startsWith("RadialGradient("))
        assertTrue(radial.toString().contains("positions=["))
        assertTrue(radial.toString().contains("colors=["))
        assertFalse(radial.toString().contains("@"))

        assertEquals(sweep, sweepCopy)
        assertEquals(sweep.toString(), sweepCopy.toString())
        assertTrue(sweep.toString().startsWith("SweepGradient("))
        assertTrue(sweep.toString().contains("positions=["))
        assertTrue(sweep.toString().contains("colors=["))
        assertFalse(sweep.toString().contains("@"))
    }

    @Test
    fun `radial and sweep public copies preserve facts arrays and component access`() {
        val radial = GPUMaterialDescriptor.RadialGradient(
            centerX = 1f,
            centerY = 2f,
            radius = 3f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        ).withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(
                interpolation = "linear",
                localMatrix = listOf(
                    1f, 0f, 2f,
                    0f, 1f, 3f,
                    0f, 0f, 1f,
                ),
            ),
        )
        val radialCopy = radial.copy(centerX = 9f)

        assertEquals(9f, radialCopy.component1())
        assertEquals(radial.interpolation, radialCopy.interpolation)
        assertEquals(radial.localMatrix, radialCopy.localMatrix)
        assertContentEquals(radial.allStopPositions, radialCopy.allStopPositions)
        assertContentEquals(radial.allStopColors, radialCopy.allStopColors)

        val sweep = GPUMaterialDescriptor.SweepGradient(
            centerX = 1f,
            centerY = 2f,
            startAngle = 0f,
            endAngle = 360f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        ).withGradientFacts(
            GPUMaterialDescriptor.GradientFacts(interpolation = "linear"),
        )
        val sweepCopy = sweep.copy(endAngle = 180f)

        assertEquals(180f, sweepCopy.component4())
        assertEquals(sweep.interpolation, sweepCopy.interpolation)
        assertEquals(sweep.localMatrix, sweepCopy.localMatrix)
        assertContentEquals(sweep.allStopPositions, sweepCopy.allStopPositions)
        assertContentEquals(sweep.allStopColors, sweepCopy.allStopColors)
    }

    @Test
    fun `radial descriptor signed zero semantics agree across every scalar and array element`() {
        val base = radialSignedZeroBase()
        val scalarVariants: List<Pair<String, (Float) -> GPUMaterialDescriptor.RadialGradient>> = listOf(
            "centerX" to { value -> base.copy(centerX = value) },
            "centerY" to { value -> base.copy(centerY = value) },
            "radius" to { value -> base.copy(radius = value) },
            "startR" to { value -> base.copy(startR = value) },
            "startG" to { value -> base.copy(startG = value) },
            "startB" to { value -> base.copy(startB = value) },
            "startA" to { value -> base.copy(startA = value) },
            "endR" to { value -> base.copy(endR = value) },
            "endG" to { value -> base.copy(endG = value) },
            "endB" to { value -> base.copy(endB = value) },
            "endA" to { value -> base.copy(endA = value) },
        )

        scalarVariants.forEach { (name, variant) ->
            assertSignedZeroHashContract(name, variant(0f), variant(-0f))
        }

        assertSignedZeroHashContract(
            "stop positions",
            base.copy(
                allStopPositions = floatArrayOf(0f),
            ),
            base.copy(
                allStopPositions = floatArrayOf(-0f),
            ),
        )
        assertSignedZeroHashContract(
            "stop colors",
            base.copy(allStopColors = floatArrayOf(0f)),
            base.copy(allStopColors = floatArrayOf(-0f)),
        )
    }

    @Test
    fun `sweep descriptor signed zero semantics agree across every scalar and array element`() {
        val base = sweepSignedZeroBase()
        val scalarVariants: List<Pair<String, (Float) -> GPUMaterialDescriptor.SweepGradient>> = listOf(
            "centerX" to { value -> base.copy(centerX = value) },
            "centerY" to { value -> base.copy(centerY = value) },
            "startAngle" to { value -> base.copy(startAngle = value) },
            "endAngle" to { value -> base.copy(endAngle = value) },
            "startR" to { value -> base.copy(startR = value) },
            "startG" to { value -> base.copy(startG = value) },
            "startB" to { value -> base.copy(startB = value) },
            "startA" to { value -> base.copy(startA = value) },
            "endR" to { value -> base.copy(endR = value) },
            "endG" to { value -> base.copy(endG = value) },
            "endB" to { value -> base.copy(endB = value) },
            "endA" to { value -> base.copy(endA = value) },
        )

        scalarVariants.forEach { (name, variant) ->
            assertSignedZeroHashContract(name, variant(0f), variant(-0f))
        }

        assertSignedZeroHashContract(
            "stop positions",
            base.copy(
                allStopPositions = floatArrayOf(0f),
            ),
            base.copy(
                allStopPositions = floatArrayOf(-0f),
            ),
        )
        assertSignedZeroHashContract(
            "stop colors",
            base.copy(allStopColors = floatArrayOf(0f)),
            base.copy(allStopColors = floatArrayOf(-0f)),
        )
    }

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
    fun `blend shader public constructor recursively snapshots caller mutations`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 0.25f,
            0f, 0f, 1f, 0.75f,
        )
        val pixels = byteArrayOf(1, 2, 3, 4)
        val blendBytes = byteArrayOf(5, 6, 7, 8)
        val descriptor = nestedBlend(positions, colors, pixels, blendBytes)
        val expected = nestedBlend(
            floatArrayOf(0f, 1f),
            floatArrayOf(
                1f, 0f, 0f, 0.25f,
                0f, 0f, 1f, 0.75f,
            ),
            byteArrayOf(1, 2, 3, 4),
            byteArrayOf(5, 6, 7, 8),
        )
        val initialHash = descriptor.hashCode()
        val initialText = descriptor.toString()

        positions.fill(99f)
        colors.fill(99f)
        pixels.fill(99)
        blendBytes.fill(99)

        assertEquals(expected, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialText, descriptor.toString())

        assertIs<GPUMaterialDescriptor.LinearGradient>(descriptor.dst)
            .allStopPositions!!
            .fill(88f)
        assertIs<GPUMaterialDescriptor.LinearGradient>(descriptor.dst)
            .allStopColors!!
            .fill(88f)
        assertIs<GPUMaterialDescriptor.ImageDraw>(descriptor.src)
            .rgbaPixels
            .fill(88)
        descriptor.uniformBytes.fill(88)

        assertEquals(expected, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialText, descriptor.toString())
        assertEquals(descriptor, descriptor.copy())
        assertEquals("SRC_OVER", descriptor.component1())
        assertContentEquals(
            floatArrayOf(0f, 1f),
            assertIs<GPUMaterialDescriptor.LinearGradient>(
                descriptor.component2(),
            ).allStopPositions,
        )
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4),
            assertIs<GPUMaterialDescriptor.ImageDraw>(
                descriptor.component3(),
            ).rgbaPixels,
        )
        assertEquals("nested-wgsl", descriptor.component4())
        assertContentEquals(byteArrayOf(5, 6, 7, 8), descriptor.component5())
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
    fun `runtime color filter evidence string distinguishes separator content`() {
        val splitFields =
            GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                effectId = "runtime.filter",
                uniforms = linkedMapOf(
                    "a" to GPURuntimeEffectUniformValue.Float1(1f),
                    "b" to GPURuntimeEffectUniformValue.Float1(2f),
                ),
                childIdentities = emptyMap(),
            )
        val embeddedSeparator =
            GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
                effectId = "runtime.filter",
                uniforms = mapOf(
                    "a=Float1(1.0), b" to
                        GPURuntimeEffectUniformValue.Float1(2f),
                ),
                childIdentities = emptyMap(),
            )

        assertNotEquals(splitFields, embeddedSeparator)
        assertNotEquals(splitFields.toString(), embeddedSeparator.toString())
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

    @Test
    fun `prepared assembly snapshots each layered dag source identity once`() {
        val session = GPUMaterialDescriptorAssemblySession()
        val graph = layeredRuntimeDag { effectId, children ->
            session.runtimeEffect(
                effectId = effectId,
                children = children,
            )
        }

        assertIs<GPUMaterialDescriptor.RuntimeEffect>(graph.root)
        assertEquals(
            GPUMaterialDescriptorSnapshotStatistics(
                assembledDescriptorCount = 13,
                assembledChildEdgeCount = 33,
                sourceSnapshotCount = 1,
                evidenceSnapshotCount = 0,
            ),
            session.snapshotStatistics,
        )
    }

    @Test
    fun `prepared assembly snapshots runtime blend unsupported and evidence defensively`() {
        val positions = floatArrayOf(0f, 1f)
        val colors = floatArrayOf(
            1f, 0f, 0f, 0.25f,
            0f, 0f, 1f, 0.75f,
        )
        val pixels = byteArrayOf(1, 2, 3, 4)
        val blendBytes = byteArrayOf(5, 6, 7, 8)
        val runtimeUniforms = linkedMapOf<String, GPURuntimeEffectUniformValue>(
            "amount" to GPURuntimeEffectUniformValue.Float1(0.75f),
        )
        val evidenceUniforms = linkedMapOf<String, GPURuntimeEffectUniformValue>(
            "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
        )
        val evidenceChildren = linkedMapOf(
            "input" to "sha256:${"a".repeat(64)}",
        )
        val session = GPUMaterialDescriptorAssemblySession()
        val runtime = session.runtimeEffect(
            effectId = "runtime.session",
            uniforms = runtimeUniforms,
            children = mapOf(
                "image" to GPUMaterialDescriptor.ImageDraw(
                    imageSourceId = "nested",
                    imageWidth = 1,
                    imageHeight = 1,
                    rgbaPixels = pixels,
                ),
            ),
        )
        val blend = session.blendShader(
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
            src = runtime,
            wgslCombined = "session-wgsl",
            uniformBytes = blendBytes,
        )
        val evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
            effectId = "runtime.filter",
            uniforms = evidenceUniforms,
            childIdentities = evidenceChildren,
        )
        val descriptor = session.unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            originalKind = GPUMaterialKind.ShaderBlend,
            source = blend,
            evidence = evidence,
        )
        val expected = GPUMaterialDescriptor.Unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            originalKind = GPUMaterialKind.ShaderBlend,
            source = GPUMaterialDescriptor.BlendShader(
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
                    allStopPositions = floatArrayOf(0f, 1f),
                    allStopColors = floatArrayOf(
                        1f, 0f, 0f, 0.25f,
                        0f, 0f, 1f, 0.75f,
                    ),
                ),
                src = GPUMaterialDescriptor.RuntimeEffect(
                    effectId = "runtime.session",
                    uniforms = mapOf(
                        "amount" to GPURuntimeEffectUniformValue.Float1(0.75f),
                    ),
                    children = mapOf(
                        "image" to GPUMaterialDescriptor.ImageDraw(
                            imageSourceId = "nested",
                            imageWidth = 1,
                            imageHeight = 1,
                            rgbaPixels = byteArrayOf(1, 2, 3, 4),
                        ),
                    ),
                ),
                wgslCombined = "session-wgsl",
                uniformBytes = byteArrayOf(5, 6, 7, 8),
            ),
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
        val initialHash = descriptor.hashCode()
        val initialText = descriptor.toString()

        positions.fill(99f)
        colors.fill(99f)
        pixels.fill(99)
        blendBytes.fill(99)
        runtimeUniforms.clear()
        evidenceUniforms.clear()
        evidenceChildren.clear()

        assertEquals(expected, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialText, descriptor.toString())
        assertEquals(
            GPUMaterialDescriptorSnapshotStatistics(
                assembledDescriptorCount = 3,
                assembledChildEdgeCount = 4,
                sourceSnapshotCount = 2,
                evidenceSnapshotCount = 1,
            ),
            session.snapshotStatistics,
        )

        val escapedBlend = assertIs<GPUMaterialDescriptor.BlendShader>(descriptor.source)
        assertIs<GPUMaterialDescriptor.LinearGradient>(escapedBlend.dst)
            .allStopPositions!!
            .fill(88f)
        assertIs<GPUMaterialDescriptor.RuntimeEffect>(escapedBlend.src)
            .children
            .values
            .forEach { child ->
                assertIs<GPUMaterialDescriptor.ImageDraw>(child).rgbaPixels.fill(88)
            }
        escapedBlend.uniformBytes.fill(88)

        assertEquals(expected, descriptor)
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialText, descriptor.toString())
    }

    @Test
    fun `prepared assembly snapshots shared foreign session evidence once`() {
        val evidence = GPUPreparedMaterialUnsupportedEvidence.RuntimeColorFilter(
            effectId = "runtime.foreign-evidence",
            uniforms = mapOf(
                "amount" to GPURuntimeEffectUniformValue.Float1(0.5f),
            ),
            childIdentities = mapOf(
                "input" to "sha256:${"b".repeat(64)}",
            ),
        )
        val foreignSession = GPUMaterialDescriptorAssemblySession()
        val first = foreignSession.unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER,
            originalKind = GPUMaterialKind.SolidColor,
            evidence = evidence,
        )
        val second = foreignSession.unsupported(
            reason = GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
            originalKind = GPUMaterialKind.SolidColor,
            evidence = evidence,
        )

        val receivingSession = GPUMaterialDescriptorAssemblySession()
        val received = receivingSession.runtimeEffect(
            effectId = "runtime.receiving",
            children = linkedMapOf(
                "first" to first,
                "second" to second,
            ),
        )

        assertEquals(
            GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.receiving",
                children = linkedMapOf(
                    "first" to GPUMaterialDescriptor.Unsupported(
                        reason = GPUPreparedMaterialUnsupportedReason.COLOR_FILTER,
                        originalKind = GPUMaterialKind.SolidColor,
                        evidence = evidence,
                    ),
                    "second" to GPUMaterialDescriptor.Unsupported(
                        reason =
                            GPUPreparedMaterialUnsupportedReason.RUNTIME_COLOR_FILTER_PLACEMENT,
                        originalKind = GPUMaterialKind.SolidColor,
                        evidence = evidence,
                    ),
                ),
            ),
            received,
        )
        assertEquals(
            GPUMaterialDescriptorSnapshotStatistics(
                assembledDescriptorCount = 1,
                assembledChildEdgeCount = 2,
                sourceSnapshotCount = 2,
                evidenceSnapshotCount = 1,
            ),
            receivingSession.snapshotStatistics,
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

    private fun layeredRuntimeDag(
        runtimeEffect: (
            effectId: String,
            children: Map<String, GPUMaterialDescriptor>,
        ) -> GPUMaterialDescriptor.RuntimeEffect,
    ): LayeredRuntimeDag {
        var previous = listOf<GPUMaterialDescriptor>(
            GPUMaterialDescriptor.SolidColor(0.25f, 0.5f, 0.75f, 1f),
        )
        val width = 3
        val layerCount = 4
        repeat(layerCount) { layer ->
            previous = List(width) { node ->
                runtimeEffect(
                    "runtime.layer.$layer.$node",
                    previous.mapIndexed { childIndex, child ->
                        "child-$childIndex" to child
                    }.toMap(LinkedHashMap()),
                )
            }
        }
        val root = runtimeEffect(
            "runtime.layer.root",
            previous.mapIndexed { index, child -> "root-$index" to child }
                .toMap(LinkedHashMap()),
        )
        return LayeredRuntimeDag(
            root = root,
            logicalDescriptorCount = 1 + width * layerCount + 1,
        )
    }

    private data class LayeredRuntimeDag(
        val root: GPUMaterialDescriptor.RuntimeEffect,
        val logicalDescriptorCount: Int,
    )

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

    private fun radialWithStops(
        positions: FloatArray,
        colors: FloatArray,
    ): GPUMaterialDescriptor.RadialGradient =
        GPUMaterialDescriptor.RadialGradient(
            centerX = 1f,
            centerY = 2f,
            radius = 3f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = positions,
            allStopColors = colors,
        )

    private fun sweepWithStops(
        positions: FloatArray,
        colors: FloatArray,
    ): GPUMaterialDescriptor.SweepGradient =
        GPUMaterialDescriptor.SweepGradient(
            centerX = 1f,
            centerY = 2f,
            startAngle = 0f,
            endAngle = 360f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = positions,
            allStopColors = colors,
        )

    private fun radialSignedZeroBase(): GPUMaterialDescriptor.RadialGradient =
        GPUMaterialDescriptor.RadialGradient(
            centerX = 1f,
            centerY = 1f,
            radius = 1f,
            startR = 1f,
            startG = 1f,
            startB = 1f,
            startA = 1f,
            endR = 1f,
            endG = 1f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(1f, 1f),
            allStopColors = floatArrayOf(1f, 1f),
        )

    private fun sweepSignedZeroBase(): GPUMaterialDescriptor.SweepGradient =
        GPUMaterialDescriptor.SweepGradient(
            centerX = 1f,
            centerY = 1f,
            startAngle = 1f,
            endAngle = 1f,
            startR = 1f,
            startG = 1f,
            startB = 1f,
            startA = 1f,
            endR = 1f,
            endG = 1f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(1f, 1f),
            allStopColors = floatArrayOf(1f, 1f),
        )

    private fun assertSignedZeroHashContract(label: String, positive: Any, negative: Any) {
        assertNotEquals(positive, negative, "$label equality")
    }
}
