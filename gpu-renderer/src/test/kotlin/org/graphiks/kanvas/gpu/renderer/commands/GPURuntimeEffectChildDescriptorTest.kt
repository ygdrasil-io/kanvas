package org.graphiks.kanvas.gpu.renderer.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

class GPURuntimeEffectChildDescriptorTest {
    @Test
    fun `runtime effect child names preserve declared order and snapshots are immutable`() {
        val matrixValues = MutableList(20) { index -> index.toFloat() }
        val children = linkedMapOf<String, GPURuntimeEffectChildDescriptor>(
            "shader" to GPURuntimeEffectChildDescriptor.Shader(
                GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
            ),
            "filter" to GPURuntimeEffectChildDescriptor.ColorFilter(
                GPUPreparedColorFilterChildDescriptor.Matrix(matrixValues),
            ),
            "blender" to GPURuntimeEffectChildDescriptor.Blender(
                GPUPreparedBlenderChildDescriptor.Mode(GPUBlendMode.SRC_OVER),
            ),
        )
        val descriptor = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = "mesh.ordered",
            descriptorVersion = 7,
            childDescriptors = children,
        )
        val initialHash = descriptor.hashCode()
        val initialText = descriptor.toString()

        matrixValues.fill(99f)
        children.clear()

        assertEquals(listOf("shader", "filter", "blender"), descriptor.childDescriptors.keys.toList())
        assertEquals(
            (0 until 20).map(Int::toFloat),
            assertIs<GPUPreparedColorFilterChildDescriptor.Matrix>(
                assertIs<GPURuntimeEffectChildDescriptor.ColorFilter>(
                    descriptor.childDescriptors.getValue("filter"),
                ).filter,
            ).values,
        )
        assertEquals(descriptor, descriptor.copy())
        assertEquals(
            listOf("shader", "filter", "blender"),
            descriptor.copy().childDescriptors.keys.toList(),
        )
        assertEquals(initialHash, descriptor.hashCode())
        assertEquals(initialText, descriptor.toString())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (descriptor.childDescriptors as MutableMap<String, GPURuntimeEffectChildDescriptor>).clear()
        }
    }

    @Test
    fun `child role and ordered name sequence participate in runtime identity`() {
        val material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f)
        val shader = runtime(
            linkedMapOf(
                "child" to GPURuntimeEffectChildDescriptor.Shader(material),
            ),
        )
        val filter = runtime(
            linkedMapOf(
                "child" to GPURuntimeEffectChildDescriptor.ColorFilter(
                    GPUPreparedColorFilterChildDescriptor.Blend(
                        rgba = listOf(1f, 0f, 0f, 1f),
                        mode = GPUBlendMode.SRC,
                    ),
                ),
            ),
        )
        val firstOrder = runtime(
            linkedMapOf(
                "a" to GPURuntimeEffectChildDescriptor.Shader(material),
                "b" to GPURuntimeEffectChildDescriptor.Shader(material),
            ),
        )
        val secondOrder = runtime(
            linkedMapOf(
                "b" to GPURuntimeEffectChildDescriptor.Shader(material),
                "a" to GPURuntimeEffectChildDescriptor.Shader(material),
            ),
        )

        assertNotEquals(shader, filter)
        assertNotEquals(shader.toString(), filter.toString())
        assertNotEquals(firstOrder, secondOrder)
        assertNotEquals(firstOrder.toString(), secondOrder.toString())
        assertEquals(firstOrder, firstOrder.copy())
        assertEquals(firstOrder.childDescriptors, firstOrder.copy().childDescriptors)
    }

    @Test
    fun `closed filter and blender descriptors retain exact nested values`() {
        val registered = GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = "filter.registered",
            descriptorVersion = 3,
            uniforms = mapOf(
                "amount" to GPURuntimeEffectUniformValue.Float1(0.25f),
            ),
            childDescriptors = mapOf(
                "input" to GPURuntimeEffectChildDescriptor.ColorFilter(
                    GPUPreparedColorFilterChildDescriptor.Matrix(List(20) { it.toFloat() }),
                ),
            ),
        )
        val filter = GPUPreparedColorFilterChildDescriptor.Compose(
            outer = GPUPreparedColorFilterChildDescriptor.Blend(
                rgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                mode = GPUBlendMode.PLUS,
            ),
            inner = GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect(registered),
        )
        val arithmetic = GPUPreparedBlenderChildDescriptor.Arithmetic(
            k1 = 0.5f,
            k2 = 0.25f,
            k3 = 0.125f,
            k4 = 0.0625f,
        )

        assertEquals(
            filter,
            GPUPreparedColorFilterChildDescriptor.Compose(
                outer = GPUPreparedColorFilterChildDescriptor.Blend(
                    rgba = listOf(0.25f, 0.5f, 0.75f, 1f),
                    mode = GPUBlendMode.PLUS,
                ),
                inner = GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect(registered),
            ),
        )
        assertEquals(
            arithmetic,
            GPUPreparedBlenderChildDescriptor.Arithmetic(0.5f, 0.25f, 0.125f, 0.0625f),
        )
    }

    @Test
    fun `legacy runtime shader children remain source compatible and gain shader roles`() {
        val descriptor = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "runtime.legacy",
            children = linkedMapOf(
                "input" to GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
            ),
        )

        assertIs<GPUMaterialDescriptor.SolidColor>(descriptor.children.getValue("input"))
        assertEquals(
            GPURuntimeEffectChildRole.Shader,
            descriptor.childDescriptors.getValue("input").role,
        )
    }

    @Test
    fun `legacy shader child maps remain order insensitive while typed maps retain order`() {
        val red = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f)
        val blue = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f)
        val legacyAB = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "legacy.order",
            children = linkedMapOf("a" to red, "b" to blue),
        )
        val legacyBA = GPUMaterialDescriptor.RuntimeEffect(
            effectId = "legacy.order",
            children = linkedMapOf("b" to blue, "a" to red),
        )
        val typedAB = runtime(
            linkedMapOf(
                "a" to GPURuntimeEffectChildDescriptor.Shader(red),
                "b" to GPURuntimeEffectChildDescriptor.Shader(blue),
            ),
        )
        val typedBA = runtime(
            linkedMapOf(
                "b" to GPURuntimeEffectChildDescriptor.Shader(blue),
                "a" to GPURuntimeEffectChildDescriptor.Shader(red),
            ),
        )

        assertEquals(legacyAB, legacyBA)
        assertEquals(legacyAB.hashCode(), legacyBA.hashCode())
        assertEquals(legacyAB.toString(), legacyBA.toString())
        assertNotEquals(typedAB, typedBA)
    }

    @Test
    fun `typed color filter depth is rejected safely by all value boundaries`() {
        var left: GPUPreparedColorFilterChildDescriptor =
            GPUPreparedColorFilterChildDescriptor.Matrix(List(20) { it.toFloat() })
        var right: GPUPreparedColorFilterChildDescriptor =
            GPUPreparedColorFilterChildDescriptor.Matrix(List(20) { it.toFloat() })
        repeat(10_000) {
            left = GPUPreparedColorFilterChildDescriptor.Compose(
                GPUPreparedColorFilterChildDescriptor.Matrix(List(20) { it.toFloat() }),
                left,
            )
            right = GPUPreparedColorFilterChildDescriptor.Compose(
                GPUPreparedColorFilterChildDescriptor.Matrix(List(20) { it.toFloat() }),
                right,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            runtime(
                mapOf(
                    "deep" to GPURuntimeEffectChildDescriptor.ColorFilter(left),
                ),
            )
        }
        assertFailsWith<IllegalStateException> { left == right }
        assertFailsWith<IllegalStateException> { left.hashCode() }
        assertFailsWith<IllegalStateException> { left.toString() }
    }

    private fun runtime(
        children: Map<String, GPURuntimeEffectChildDescriptor>,
    ): GPUMaterialDescriptor.RuntimeEffect =
        GPUMaterialDescriptor.RuntimeEffect.withChildDescriptors(
            effectId = "mesh.identity",
            childDescriptors = children,
        )
}
