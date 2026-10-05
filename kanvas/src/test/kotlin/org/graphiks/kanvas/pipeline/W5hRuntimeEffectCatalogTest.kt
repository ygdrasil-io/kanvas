package org.graphiks.kanvas.pipeline

import kotlin.test.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.render.ir.*

class W5hRuntimeEffectCatalogTest {
    @Test fun exactVersionSelectsBuiltin() {
        val first = assertNotNull(RuntimeEffect.registered(ID, 1))
        val second = assertNotNull(RuntimeEffect.registered(ID, 1))
        val descriptor = assertNotNull(first.descriptor)
        assertEquals(descriptor, second.descriptor)
        assertEquals(ID, first.id)
        assertEquals(1, first.semanticVersionI32)
        assertEquals(RuntimeEffectAbi.SHADER, first.kind)
        assertEquals(3, descriptor.versionI32)
        assertEquals("2c7646732d3484bdc2d99a813af1ae721872bbfeb3b2030e2ee4eb39be53d6c8", first.abiHash)
        assertEquals(listOf(RuntimeChildSlotV2("child", RuntimeChildType.SHADER, false)), descriptor.childSlots)
        assertEquals(listOf(RuntimeUniformSlotV2("alpha", RuntimeUniformType.FLOAT, 0, 4, 4, 1, 0)), descriptor.uniformBlock.slots)
        assertEquals(16, descriptor.uniformBlock.sizeBytesI32)
        assertTrue(descriptor.logicalResources.isEmpty())
        assertNull(descriptor.legacyV0)
        assertNull(first.moduleOrNull)
    }
    @Test fun lookupNeverSelectsAnotherVersion() {
        assertNull(RuntimeEffect.registered("unknown", 1))
        for (version in listOf(-1, 0, 2)) assertNull(RuntimeEffect.registered(ID, version))
        assertTrue(RuntimeEffect.registered(ID)?.semanticVersionI32 != 1)
    }
    @Test fun positiveEffectRefusesLegacyAccessAndWrongRoles() {
        val effect = assertNotNull(RuntimeEffect.registered(ID, 1))
        assertEquals("Registered runtime effects have no WGSL module", assertFailsWith<UnsupportedOperationException> { effect.module }.message)
        assertEquals("Registered runtime effects have no legacy uniform layout", assertFailsWith<UnsupportedOperationException> { effect.uniformLayout }.message)
        assertEquals("Runtime effect kind mismatch", assertFailsWith<IllegalArgumentException> { effect.makeColorFilter(UniformBlock.EMPTY) }.message)
        assertEquals("Runtime effect kind mismatch", assertFailsWith<IllegalArgumentException> { effect.makeBlender(UniformBlock.EMPTY) }.message)
        assertFailsWith<IllegalArgumentException> { RuntimeEffect.register(effect) }
    }
    @Test fun extraResourceKindsAreRefused() {
        val effect = assertNotNull(RuntimeEffect.registered(ID, 1))
        val bindings = listOf(
            RuntimeEffectResourceBinding.StorageRead.copyOf(byteArrayOf(1)),
            RuntimeEffectResourceBinding.SampledTexture(Image.fromPixels(1, 1, byteArrayOf(0, 0, 0, 0))),
            RuntimeEffectResourceBinding.Sampler(RuntimeSamplerTypeV1.FILTERING),
        )
        for (binding in bindings) {
            assertEquals("invalid.material.runtime_effect.resource_extra", assertFailsWith<IllegalArgumentException> {
                effect.makeShader(UniformBlock { float1("alpha", .5f) }, emptyMap(), RuntimeEffectResourceBindings.of(mapOf("extra" to binding)))
            }.message)
        }
    }
    @Test fun compilationRemainsLegacy() {
        RuntimeEffectWgsl4kWiring.install()
        val effect = RuntimeEffect.compile("@fragment fn main() -> @location(0) vec4f { return vec4f(1.0); }").getOrThrow()
        assertEquals(0, effect.semanticVersionI32)
        assertNull(effect.descriptor)
        assertNull(effect.kind)
        assertNull(effect.abiHash)
        assertNotNull(effect.moduleOrNull)
        assertNull(RuntimeEffect.registered(effect.id, 1))
    }
    @Test fun legacyArchiveIdCannotShadowPositiveLookup() {
        val before = assertNotNull(RuntimeEffect.registered(ID, 1)).descriptor
        val picture = assertNotNull(Picture.fromByteArray(legacyCollisionPicture()))
        val legacy = assertIs<Shader.RuntimeEffect>(assertIs<DisplayOp.DrawRect>(picture.ops.single()).paint.shader).effect
        assertEquals(ID, legacy.id)
        assertEquals(0, legacy.semanticVersionI32)
        assertEquals(0, assertNotNull(RuntimeEffect.registered(ID)).semanticVersionI32)
        assertEquals(before, assertNotNull(RuntimeEffect.registered(ID, 1)).descriptor)
    }
    /** Literal v7 compatibility fixture, independent of the current Picture writer. */
    private fun legacyCollisionPicture(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.write(byteArrayOf(0x4b, 0x50, 0x49, 0x43))
            output.writeInt(7)
            listOf(0f, 0f, 8f, 8f).forEach(output::writeFloat)
            output.writeInt(1)
            output.writeByte(0) // DrawRect
            listOf(1f, 1f, 7f, 7f).forEach(output::writeFloat)
            output.writeInt(0xffff0000.toInt())
            output.writeByte(7) // RuntimeEffect shader
            output.writeUTF(ID)
            output.writeUTF("legacy-runtime-source")
            output.writeUTF("main")
            repeat(4) { output.writeInt(0) } // Module declarations and stride
            output.writeByte(0) // Vertex step
            repeat(4) { output.writeInt(0) } // Layout, children, uniforms, child bindings
            output.writeByte(3) // SRC_OVER
            repeat(5) { output.writeByte(0xff) } // Absent paint effects
            output.writeByte(0) // Fill
            output.writeFloat(0f)
            output.writeByte(0) // Butt
            output.writeByte(0) // Miter
            output.writeFloat(4f)
            output.writeBoolean(true)
            repeat(9) { output.writeFloat(if (it % 4 == 0) 1f else 0f) }
            output.writeByte(0) // Wide-open clip
        }
        return bytes.toByteArray()
    }
    private companion object { const val ID = "kanvas.runtime.child-opacity" }
}
