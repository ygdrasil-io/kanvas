package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuntimeEffectAbiW52Test {
    @Test
    fun `std140 layout covers scalar vector matrix and array alignment`() {
        val layout = RuntimeEffectUniformAbiLayout(
            listOf(
                RuntimeEffectUniformAbiField("scalar", RuntimeEffectUniformAbiType.Scalar),
                RuntimeEffectUniformAbiField("vector", RuntimeEffectUniformAbiType.Vector(3)),
                RuntimeEffectUniformAbiField("matrix", RuntimeEffectUniformAbiType.Matrix(4, 4)),
                RuntimeEffectUniformAbiField("samples", RuntimeEffectUniformAbiType.Array(RuntimeEffectUniformAbiType.Scalar, 2)),
            ),
        ).compute()
        assertEquals(0, layout.fields["scalar"]?.offset)
        assertEquals(16, layout.fields["vector"]?.offset)
        assertEquals(32, layout.fields["matrix"]?.offset)
        assertEquals(96, layout.fields["samples"]?.offset)
        assertEquals(128, layout.byteSize)
        assertEquals(16, layout.fields["samples"]?.stride)
    }

    @Test
    fun `binding group and index mismatch is refused before pipeline creation`() {
        val result = RuntimeEffectAbiValidator.validateBinding(
            expectedGroup = 1, expectedBinding = 0, actualGroup = 0, actualBinding = 2,
        )
        assertIs<RuntimeEffectAbiValidation.Refused>(result)
        assertEquals("unsupported.runtime_effect.binding_layout_mismatch", result.code)
    }

    @Test
    fun `material cache key includes descriptor version uniform slab and children`() {
        val a = runtimeEffectMaterialCacheKey("runtime.x", 1, byteArrayOf(1, 2), listOf("child:a"))
        val b = runtimeEffectMaterialCacheKey("runtime.x", 2, byteArrayOf(1, 2), listOf("child:a"))
        val c = runtimeEffectMaterialCacheKey("runtime.x", 1, byteArrayOf(1, 3), listOf("child:a"))
        assertTrue(a != b && a != c)
    }

    @Test
    fun `descriptor WGSL is accepted only through parser-backed registry validation`() {
        assertTrue(KanvasRuntimeEffectRegistry().validateWgsl(GPURuntimeEffectID("runtime.simple_rt")).accepted)
    }

    @Test
    fun `uniform slab initializes padding deterministically`() {
        val slab = RuntimeEffectUniformSlab(16)
        assertTrue(slab.bytes.all { it == 0.toByte() })
    }

    @Test
    fun `computed layout snapshots and protects its member map`() {
        val source = linkedMapOf(
            "scalar" to RuntimeEffectUniformAbiMember(offset = 0, size = 4, alignment = 4),
        )
        val layout = RuntimeEffectUniformAbiComputedLayout(source, byteSize = 16)
        source["scalar"] = RuntimeEffectUniformAbiMember(offset = 8, size = 4, alignment = 4)
        assertEquals(0, layout.fields.getValue("scalar").offset)
        assertTrue(
            runCatching {
                @Suppress("UNCHECKED_CAST")
                (layout.fields as MutableMap<String, RuntimeEffectUniformAbiMember>)["other"] =
                    RuntimeEffectUniformAbiMember(offset = 4, size = 4, alignment = 4)
            }.isFailure,
        )
    }
}
