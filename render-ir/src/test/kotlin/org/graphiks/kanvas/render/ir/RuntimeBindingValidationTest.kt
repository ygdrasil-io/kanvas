package org.graphiks.kanvas.render.ir

import org.graphiks.math.color.ColorARGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class RuntimeBindingValidationTest {
    @Test
    fun `shader module descriptor owns every module ABI field and distinguishes each mismatch`() {
        val uniforms = mutableListOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16))
        val textures = mutableListOf(RuntimeTextureSlot("image", 3))
        val descriptor = ShaderModuleDescriptor.of("source-a", "fragment", uniforms, textures)
        val identity = descriptor.canonicalId

        uniforms.clear()
        textures.clear()

        assertEquals("source-a", descriptor.source)
        assertEquals("fragment", descriptor.entryPoint)
        assertEquals(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16), descriptor.uniformAt(0))
        assertEquals(RuntimeTextureSlot("image", 3), descriptor.textureAt(0))
        assertEquals(identity, descriptor.canonicalId)
        assertFailsWith<UnsupportedOperationException> { (descriptor.uniforms() as MutableList<RuntimeUniformSlot>).clear() }
        assertFailsWith<UnsupportedOperationException> { (descriptor.textures() as MutableList<RuntimeTextureSlot>).clear() }

        listOf(
            ShaderModuleDescriptor.of("source-b", "fragment", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "vertex", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("other", 2, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("tint", 4, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT3, 16)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 4)), listOf(RuntimeTextureSlot("image", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("other", 3))),
            ShaderModuleDescriptor.of("source-a", "fragment", listOf(RuntimeUniformSlot("tint", 2, RuntimeUniformType.FLOAT4, 16)), listOf(RuntimeTextureSlot("image", 5))),
        ).forEach { mismatch ->
            assertNotEquals(descriptor, mismatch)
            assertNotEquals(identity, mismatch.canonicalId)
        }
    }

    @Test
    fun `shader module identity preserves distinct isolated UTF 16 surrogates`() {
        val first = ShaderModuleDescriptor.of("shader-\uD800", "fragment")
        val second = ShaderModuleDescriptor.of("shader-\uD801", "fragment")

        assertNotEquals(first.canonicalId, second.canonicalId)
        assertNotEquals(first, second)
    }

    @Test
    fun `runtime binding validation reports missing extra and mistyped values before construction`() {
        val descriptor = shaderDescriptor(
            slots = listOf(RuntimeUniformSlot("value", 0, RuntimeUniformType.FLOAT, 0)),
            children = emptyList(),
        )
        assertEquals(0, descriptor.uniformLayout.slotAt(0).size)

        assertIs<RuntimeBindingValidationResult.MissingUniform>(
            RuntimeBindingValidator.validate(descriptor, emptyMap(), emptyList()),
        )
        assertIs<RuntimeBindingValidationResult.UnexpectedUniform>(
            RuntimeBindingValidator.validate(shaderDescriptor(emptyList(), emptyList()), mapOf("other" to RuntimeUniformValue.F1(1f)), emptyList()),
        )
        val mismatch = assertIs<RuntimeBindingValidationResult.UniformTypeMismatch>(
            RuntimeBindingValidator.validate(descriptor, mapOf("value" to RuntimeUniformValue.F2(1f, 2f)), emptyList()),
        )
        assertEquals(RuntimeUniformType.FLOAT, mismatch.expected)
        assertEquals(RuntimeUniformType.FLOAT2, mismatch.actual)
        assertFailsWith<IllegalArgumentException> {
            MaterialNode.RuntimeEffect.of(descriptor, mapOf("value" to RuntimeUniformValue.F2(1f, 2f)), emptyList())
        }
    }

    @Test
    fun `runtime child bindings are exact and variant factories reject mismatched ABI children`() {
        val shaderChild = RuntimeChildBinding("child", RuntimeChildType.SHADER)
        val expectedShader = shaderDescriptor(emptyList(), listOf(RuntimeChildSlot("child", RuntimeChildType.SHADER)))

        assertIs<RuntimeBindingValidationResult.MissingChild>(
            RuntimeBindingValidator.validate(expectedShader, emptyMap(), emptyList()),
        )
        assertIs<RuntimeBindingValidationResult.UnexpectedChild>(
            RuntimeBindingValidator.validate(shaderDescriptor(emptyList(), emptyList()), emptyMap(), listOf(shaderChild)),
        )
        assertIs<RuntimeBindingValidationResult.DuplicateChild>(
            RuntimeBindingValidator.validate(expectedShader, emptyMap(), listOf(shaderChild, shaderChild)),
        )
        val childMismatch = assertIs<RuntimeBindingValidationResult.ChildTypeMismatch>(
            RuntimeBindingValidator.validate(
                shaderDescriptor(emptyList(), listOf(RuntimeChildSlot("child", RuntimeChildType.COLOR_FILTER))),
                emptyMap(),
                listOf(shaderChild),
            ),
        )
        assertEquals(RuntimeChildType.COLOR_FILTER, childMismatch.expected)
        assertEquals(RuntimeChildType.SHADER, childMismatch.actual)

        val colorDescriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("color-child"),
            RuntimeEffectAbi.COLOR_FILTER,
            RuntimeUniformLayout.of(emptyList()),
            listOf(RuntimeChildSlot("child", RuntimeChildType.SHADER)),
        )
        assertFailsWith<IllegalArgumentException> {
            ColorFilterNode.RuntimeEffect.of(
                colorDescriptor,
                emptyMap(),
                listOf(RuntimeColorFilterChild("child", ColorFilterNode.Luma)),
            )
        }
        val imageDescriptor = RuntimeEffectDescriptor.of(
            RuntimeEffectId("image-child"),
            RuntimeEffectAbi.IMAGE_FILTER,
            RuntimeUniformLayout.of(emptyList()),
            listOf(RuntimeChildSlot("child", RuntimeChildType.SHADER)),
        )
        assertFailsWith<IllegalArgumentException> {
            ImageFilterNode.RuntimeEffect.of(
                imageDescriptor,
                emptyMap(),
                null,
                listOf(RuntimeImageFilterChild("child", ImageFilterNode.Blur(1f, 1f))),
            )
        }
        val imageRuntime = ImageFilterNode.RuntimeEffect.of(imageDescriptor, emptyMap(), "child", emptyList())
        assertEquals("child", imageRuntime.childShaderName)
    }

    @Test
    fun `runtime layouts reject duplicate ABI locations and accept exact vertex boundaries`() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeUniformLayout.of(
                listOf(
                    RuntimeUniformSlot("first", 0, RuntimeUniformType.FLOAT, 0),
                    RuntimeUniformSlot("second", 0, RuntimeUniformType.FLOAT2, 0),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeVertexLayout.of(
                8,
                listOf(
                    RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32, 0, 1),
                    RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32, 4, 1),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeVertexLayout.of(8, listOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32X2, 1, 0)))
        }
        val boundary = RuntimeVertexLayout.of(8, listOf(RuntimeVertexAttribute(RuntimeVertexFormat.FLOAT32X2, 0, 0)))
        assertEquals(8, boundary.stride)
        assertFailsWith<IllegalArgumentException> { RuntimeUniformValue.M4(FloatArray(15)) }
        assertEquals(16, RuntimeUniformValue.M4(FloatArray(16)).copyValues().size)
    }

    @Test
    fun `uniform maps preserve source insertion order and make it semantic`() {
        val descriptor = shaderDescriptor(
            listOf(
                RuntimeUniformSlot("z", 0, RuntimeUniformType.FLOAT, 0),
                RuntimeUniformSlot("a", 1, RuntimeUniformType.FLOAT, 0),
            ),
            emptyList(),
        )
        val zThenA = MaterialNode.RuntimeEffect.of(
            descriptor,
            linkedMapOf("z" to RuntimeUniformValue.F1(1f), "a" to RuntimeUniformValue.F1(2f)),
            emptyList(),
        )
        val aThenZ = MaterialNode.RuntimeEffect.of(
            descriptor,
            linkedMapOf("a" to RuntimeUniformValue.F1(2f), "z" to RuntimeUniformValue.F1(1f)),
            emptyList(),
        )

        assertEquals(listOf("z", "a"), zThenA.uniforms().keys.toList())
        assertNotEquals(zThenA.canonicalId, aThenZ.canonicalId)
        assertFailsWith<UnsupportedOperationException> {
            (zThenA.uniforms() as MutableMap<String, RuntimeUniformValue>).clear()
        }
    }

    @Test
    fun `runtime descriptor wrappers compare structurally including field differences`() {
        val first = shaderDescriptor(
            listOf(RuntimeUniformSlot("value", 0, RuntimeUniformType.FLOAT, 0)),
            emptyList(),
        )
        val equivalent = shaderDescriptor(
            listOf(RuntimeUniformSlot("value", 0, RuntimeUniformType.FLOAT, 0)),
            emptyList(),
        )
        val different = shaderDescriptor(
            listOf(RuntimeUniformSlot("value", 1, RuntimeUniformType.FLOAT, 0)),
            emptyList(),
        )

        assertEquals(first, equivalent)
        assertEquals(first.hashCode(), equivalent.hashCode())
        assertEquals(first.canonicalId, equivalent.canonicalId)
        assertNotEquals(first, different)
        assertNotEquals(first.canonicalId, different.canonicalId)
    }

    private fun shaderDescriptor(
        slots: List<RuntimeUniformSlot>,
        children: List<RuntimeChildSlot>,
    ): RuntimeEffectDescriptor = RuntimeEffectDescriptor.of(
        RuntimeEffectId("shader-${slots.joinToString { it.name }}-${children.joinToString { it.name }}"),
        RuntimeEffectAbi.SHADER,
        RuntimeUniformLayout.of(slots),
        children,
    )
}
