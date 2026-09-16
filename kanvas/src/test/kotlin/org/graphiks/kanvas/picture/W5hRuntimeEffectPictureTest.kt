package org.graphiks.kanvas.picture

import java.nio.ByteBuffer
import java.util.Base64
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformValue
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import kotlin.test.*

class W5hRuntimeEffectPictureTest {
    @OptIn(ExperimentalUnsignedTypes::class)
    @Test fun positivePictureReplaysRuntimePixels() {
        val picture = assertNotNull(Picture.fromByteArray(positivePicture().toByteArray()))
        val surface = org.graphiks.kanvas.surface.Surface(2, 1)
        surface.canvas { picture.playback(this) }
        repeat(2) {
            val pixels = surface.render().pixels
            for (pixel in 0..1) {
                assertTrue(pixels[pixel * 4].toInt() in 187..188)
                assertEquals(0, pixels[pixel * 4 + 1].toInt())
                assertEquals(0, pixels[pixel * 4 + 2].toInt())
                assertTrue(pixels[pixel * 4 + 3].toInt() in 127..128)
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test fun historicalV0RenderRefusesAndSameSurfaceRecovers() {
        val bytes=historicalFixture()
        val original="compiled-d949d49d".encodeToByteArray()
        val taskId="task3-v0-history1"
        val replacement=taskId.encodeToByteArray()
        assertEquals(original.size,replacement.size)
        bytes.indices.filter { offset -> offset+original.size <= bytes.size &&
            bytes.copyOfRange(offset,offset+original.size).contentEquals(original) }.forEach { offset ->
            replacement.copyInto(bytes,offset)
        }
        val positive=assertNotNull(RuntimeEffect.registered(ID,1))
        val historical = assertNotNull(Picture.fromByteArray(bytes))
        assertNull(RuntimeEffect.registered(taskId,1))
        assertEquals(positive.abiHash,assertNotNull(RuntimeEffect.registered(ID,1)).abiHash)
        val surface = org.graphiks.kanvas.surface.Surface(2, 1)
        surface.canvas { historical.playback(this) }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.runtime_effect.unregistered_semantics", failure.message.orEmpty().substringBefore(':'))
        surface.discardRecordedOperations()
        surface.canvas { positivePicture().playback(this) }
        repeat(2) {
            val pixels = surface.render().pixels
            for (pixel in 0..1) {
                assertTrue(pixels[pixel * 4].toInt() in 187..188)
                assertEquals(0, pixels[pixel * 4 + 1].toInt())
                assertEquals(0, pixels[pixel * 4 + 2].toInt())
                assertTrue(pixels[pixel * 4 + 3].toInt() in 127..128)
            }
        }
    }
    @Test fun positiveDescriptorAndValuesRoundTrip() {
        val picture = positivePicture()
        val bytes = picture.toByteArray()
        assertEquals(13, ByteBuffer.wrap(bytes).getInt(4))
        assertEquals(7, ByteBuffer.wrap(bytes).getInt(28))
        val decoded = assertNotNull(Picture.fromByteArray(bytes))
        assertContentEquals(bytes, decoded.toByteArray())
        val shader = runtime(decoded)
        assertEquals(ID, shader.effect.id)
        assertEquals(1, shader.effect.semanticVersionI32)
        assertEquals(assertNotNull(RuntimeEffect.registered(ID, 1)).abiHash, shader.effect.abiHash)
        assertEquals(UniformValue.F1(.5f), shader.uniforms.entries["alpha"])
        assertEquals(listOf("child"), shader.children.keys.toList())
        assertNull(shader.effect.moduleOrNull)
        val descriptor = assertNotNull(shader.effect.descriptor)
        assertEquals(16, descriptor.uniformBlock.sizeBytesI32)
        assertEquals(0, descriptor.uniformBlock.slots.single().offsetBytesI32)
        assertEquals(false, descriptor.childSlots.single().nullable)
        assertTrue(descriptor.logicalResources.isEmpty())
    }

    @Test fun capturePreservesChildOrderAndMutationAcrossRepeatedPlayback() {
        val matrix = org.graphiks.math.color.ColorMatrixF32.ofIdentity().apply { setScale(.5f, 1f, 1f, 1f) }
        val sourceChildren = linkedMapOf("child" to Shader.Blend(
            org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
            Shader.WithColorFilter(solid(0xffff0000.toInt()), org.graphiks.kanvas.paint.ColorFilter.Matrix(matrix)), solid(0xff0000ff.toInt())))
        val values = linkedMapOf<String, UniformValue>("alpha" to UniformValue.F1(.5f))
        val uniforms = UniformBlock { entries.putAll(values) }
        val picture = record(assertNotNull(RuntimeEffect.registered(ID, 1)).makeShader(uniforms, sourceChildren))
        val bytes = picture.toByteArray()
        matrix.setScale(0f, 0f, 0f, 0f)
        values["alpha"] = UniformValue.F1(1f)
        sourceChildren.clear()
        assertContentEquals(bytes, picture.toByteArray())
        val restored = runtime(assertNotNull(Picture.fromByteArray(bytes)))
        val blend = assertIs<Shader.Blend>(restored.children["child"])
        assertEquals(.5f, assertIs<org.graphiks.kanvas.paint.ColorFilter.Matrix>(assertIs<Shader.WithColorFilter>(blend.dst).filter).matrix.toFloatArray()[0])
        assertEquals(ColorARGB.fromPackedInt(0xff0000ff.toInt()), assertIs<Shader.SolidColor>(blend.src).color)
        val replayBytes = (0..1).map {
            val recorder = PictureRecorder()
            val canvas = recorder.beginRecording(BOUNDS)
            picture.playback(canvas)
            picture.playback(canvas)
            recorder.finishRecordingAsPicture().toByteArray()
        }
        assertContentEquals(replayBytes[0], replayBytes[1])
        assertContentEquals(replayBytes[0], assertNotNull(Picture.fromByteArray(replayBytes[0])).toByteArray())
    }

    @Suppress("DEPRECATION")
    @Test fun historicalV11InstallsV0OnlyAfterCompleteValidation() {
        val fixture = historicalFixture()
        assertEquals("a05c5ca6b35c6897c2ee3b64c73a122c090b4b5d7151bdf21e34390b1bb835d5",
            java.security.MessageDigest.getInstance("SHA-256").digest(fixture).joinToString("") { "%02x".format(it) })
        val positive = assertNotNull(RuntimeEffect.registered(ID, 1))
        assertNull(RuntimeEffect.registered("compiled-d949d49d"))
        // Reject after a complete descriptor before any valid archive can install it.
        assertNull(Picture.fromByteArray(fixture.copyOf(fixture.size - 1)))
        assertNull(RuntimeEffect.registered("compiled-d949d49d"))
        val first = assertNotNull(Picture.fromByteArray(fixture))
        val value = runtime(first)
        assertEquals("compiled-d949d49d", value.effect.id)
        assertEquals(0, value.effect.semanticVersionI32)
        assertEquals(UniformValue.F1(.5f), value.uniforms.entries["alpha"])
        assertEquals(listOf("child"), value.children.keys.toList())
        assertNull(RuntimeEffect.registered(value.effect.id, 1))
        val installed = RuntimeEffect.registered(value.effect.id)
        assertSame(value.effect, installed, "A valid archive installs its reconstructed v0 in the deprecated API map")
        assertEquals(positive.abiHash, assertNotNull(RuntimeEffect.registered(ID, 1)).abiHash)
        val second = assertNotNull(Picture.fromByteArray(fixture))
        val bytes = first.toByteArray()
        // Literal SHA-256 from the normative v0 preimage, independently calculated
        // from the original writer's declarations and WGSL, not an IR hash helper.
        val idBytes = value.effect.id.encodeToByteArray()
        val descriptorStart = bytes.indices.first { index -> index + idBytes.size + 4 <= bytes.size &&
            ByteBuffer.wrap(bytes).getInt(index) == idBytes.size &&
            bytes.copyOfRange(index + 4, index + 4 + idBytes.size).contentEquals(idBytes) }
        val hashOffset = descriptorStart + 4 + idBytes.size + 4 + "SHADER".length + 4
        assertEquals("105fe1739e6c82994385178fe59f4affc2fcf3ccbbaafce261d68fa34c85767d",
            bytes.copyOfRange(hashOffset + 4, hashOffset + 68).decodeToString())
        assertContentEquals(bytes, second.toByteArray())
        assertContentEquals(bytes, assertNotNull(Picture.fromByteArray(bytes)).toByteArray())
    }

    @Suppress("DEPRECATION")
    @Test fun incompatibleSceneArchiveRuntimeIdsRollBackTogether() {
        val firstId = "round1-first-0000"
        val collisionId = "round1-conflict-0"
        val fixture = HistoricalRuntimeWire(historicalFixture())
        val firstDraw = fixture.draw(firstId)
        val originalDraw = fixture.draw(collisionId)
        val conflictingDraw = fixture.draw(collisionId, changeModule = true)
        assertNull(RuntimeEffect.registered(firstId))
        assertNull(RuntimeEffect.registered(collisionId))
        val conflicting = fixture.archive(firstDraw, originalDraw, conflictingDraw)
        assertNull(Picture.fromByteArray(conflicting))
        assertNull(RuntimeEffect.registered(firstId), "Earlier valid entries must not leak from a rejected archive")
        assertNull(RuntimeEffect.registered(collisionId))
        val valid = assertNotNull(Picture.fromByteArray(fixture.archive(firstDraw, originalDraw)))
        val effects = mutableListOf<RuntimeEffect>()
        valid.forEachOp { op -> if (op is DisplayOp.DrawRect) effects += assertIs<Shader.RuntimeEffect>(op.paint.shader).effect }
        assertEquals(listOf(firstId, collisionId), effects.map { it.id })
        effects.forEach { effect ->
            assertSame(effect, RuntimeEffect.registered(effect.id))
            assertNull(RuntimeEffect.registered(effect.id, 1))
        }
        // A collision with an already installed entry also leaves the whole archive unchanged.
        assertNull(Picture.fromByteArray(conflicting))
        effects.forEach { assertSame(it, RuntimeEffect.registered(it.id)) }
        assertNotNull(RuntimeEffect.registered(ID, 1))
    }

    @Test fun decodeRejectsUnknownRuntimeTriplet() {
        val bytes = positivePicture().toByteArray()
        val wire = RuntimeWire(bytes)
        assertRejected(bytes, wire.replaceText(wire.idOffset, "kanvas.runtime.other-opacity"))
        assertRejected(bytes, wire.withInt(wire.versionOffset, 2))
        // Hash-consistent unknown identities reach exact catalogue resolution.
        assertRejected(bytes, wire.withIdentity("kanvas.runtime.other-opacity", 1))
        assertRejected(bytes, wire.withIdentity(ID, 2))
    }

    @Test fun decodeRejectsRuntimeAbiHashMismatch() {
        val bytes = positivePicture().toByteArray()
        val wire = RuntimeWire(bytes)
        assertRejected(bytes, wire.replaceText(wire.hashOffset, "0".repeat(64)))
    }

    @Test fun decodeRejectsMalformedRuntimeDescriptor() {
        val bytes = positivePicture().toByteArray()
        val wire = RuntimeWire(bytes)
        val malformed = listOf(
            wire.withInt(wire.versionOffset, -1),
            wire.withInt(wire.versionOffset, 0),
            wire.replaceText(wire.hashOffset, "A".repeat(64)),
            wire.replaceText(wire.hashOffset, "a".repeat(63)),
            bytes.copyOf().also { it[wire.legacyOffset] = 1 },
            wire.withInt(wire.uniformCountOffset, Int.MAX_VALUE),
            wire.withInt(wire.resourceCountOffset, Int.MAX_VALUE),
            wire.withInt(wire.resourceCountOffset, -1),
            wire.withInt(wire.uniformSizeOffset, Int.MAX_VALUE),
            bytes.copyOf(wire.uniformCountOffset + 4),
            bytes.copyOf(wire.resourceCountOffset + 4),
            wire.withResourceDeclaration(),
        )
        malformed.forEachIndexed { index, invalid -> assertRejected(bytes, invalid, "case $index") }
    }

    private fun assertRejected(valid: ByteArray, invalid: ByteArray, reason: String = "") {
        val hash = assertNotNull(RuntimeEffect.registered(ID, 1)).abiHash
        assertNull(Picture.fromByteArray(invalid), reason)
        assertContentEquals(valid, assertNotNull(Picture.fromByteArray(valid)).toByteArray())
        assertEquals(hash, assertNotNull(RuntimeEffect.registered(ID, 1)).abiHash)
    }
    private fun positivePicture(): Picture = record(assertNotNull(RuntimeEffect.registered(ID, 1)).makeShader(
        UniformBlock { float1("alpha", .5f) }, mapOf("child" to solid(0xffff0000.toInt()))))
    private fun solid(color: Int) = Shader.SolidColor(ColorARGB.fromPackedInt(color))
    private fun record(shader: Shader): Picture {
        val recorder = PictureRecorder()
        recorder.beginRecording(BOUNDS).drawRect(BOUNDS, Paint(shader = shader))
        return recorder.finishRecordingAsPicture()
    }
    private fun runtime(picture: Picture): Shader.RuntimeEffect {
        val shaders = mutableListOf<Shader.RuntimeEffect>()
        picture.forEachOp { op -> if (op is DisplayOp.DrawRect) shaders += assertIs<Shader.RuntimeEffect>(op.paint.shader) }
        return shaders.single()
    }

    private fun historicalFixture(): ByteArray = Base64.getDecoder().decode(requireNotNull(javaClass.getResourceAsStream(
        "/picture/format-11-runtime-effect-v0.base64")).bufferedReader().use { it.readText().trim() })

    /** Reads the genuine v11 header and slices its existing Draw command for malformed duplicate-ID cases. */
    private class HistoricalRuntimeWire(private val bytes: ByteArray) {
        private val buffer = ByteBuffer.wrap(bytes)
        private val commandCountOffset: Int
        private val drawOffset: Int
        init {
            assertEquals(11, buffer.getInt(4))
            assertEquals(5, buffer.getInt(28))
            var cursor = 40 // KPIC header, extent; then three color-space strings.
            repeat(3) { cursor += 4 + buffer.getInt(cursor) }
            commandCountOffset = cursor
            assertEquals(2, buffer.getInt(cursor)); cursor += 4
            assertEquals(5, buffer.getInt(cursor)); cursor += 4 // SetClip
            assertEquals(2, buffer.getInt(cursor)); cursor += 4 + 16 + 1 // DeviceRect, rectangle, AA
            drawOffset = cursor
            assertEquals(1, buffer.getInt(cursor)) // Draw
        }
        fun draw(id: String, changeModule: Boolean = false): ByteArray {
            var draw = bytes.copyOfRange(drawOffset, bytes.size)
            fun replaceText(old: String, replacement: String) {
                val encoded = old.encodeToByteArray()
                val positions = draw.indices.filter { offset -> offset + 4 + encoded.size <= draw.size &&
                    ByteBuffer.wrap(draw).getInt(offset) == encoded.size &&
                    draw.copyOfRange(offset + 4, offset + 4 + encoded.size).contentEquals(encoded) }
                assertTrue(positions.isNotEmpty())
                positions.asReversed().forEach { offset ->
                    val value = replacement.encodeToByteArray()
                    draw = draw.copyOfRange(0, offset) + ByteBuffer.allocate(4).putInt(value.size).array() + value +
                        draw.copyOfRange(offset + 4 + encoded.size, draw.size)
                }
            }
            replaceText("compiled-d949d49d", id)
            if (changeModule) {
                val marker = "struct Params".encodeToByteArray()
                val offset = draw.indices.first { index -> index + marker.size <= draw.size &&
                    draw.copyOfRange(index, index + marker.size).contentEquals(marker) }
                val size = ByteBuffer.wrap(draw).getInt(offset - 4)
                val source = draw.copyOfRange(offset, offset + size).decodeToString()
                replaceText(source, source.replace("params.alpha;", "params.alpha * 0.5;"))
            }
            return draw
        }
        fun archive(vararg draws: ByteArray): ByteArray =
            bytes.copyOfRange(0, drawOffset).also { ByteBuffer.wrap(it).putInt(commandCountOffset, draws.size + 1) } +
                draws.fold(byteArrayOf()) { result, draw -> result + draw }
    }

    /** Small parser for the documented v3 descriptor embedded in public writer bytes. */
    private class RuntimeWire(private val bytes: ByteArray) {
        private val buffer = ByteBuffer.wrap(bytes)
        val idOffset = bytes.indices.first { index ->
            index + 4 + ID.length <= bytes.size && buffer.getInt(index) == ID.length &&
                bytes.copyOfRange(index + 4, index + 4 + ID.length).decodeToString() == ID
        }
        private var cursor = idOffset
        private fun text() { cursor += 4 + buffer.getInt(cursor) }
        private fun count() = buffer.getInt(cursor).also { cursor += 4 }
        val versionOffset: Int
        val hashOffset: Int
        val uniformSizeOffset: Int
        val uniformCountOffset: Int
        val resourceCountOffset: Int
        val legacyOffset: Int
        init {
            text(); text() // ID, ABI
            versionOffset = cursor; cursor += 4
            hashOffset = cursor; text()
            uniformSizeOffset = cursor; cursor += 4
            uniformCountOffset = cursor
            repeat(count()) { text(); text(); cursor += 20 }
            repeat(count()) { text(); text(); cursor += 1 }
            resourceCountOffset = cursor
            assertEquals(0, count())
            legacyOffset = cursor
            assertEquals(0, bytes[cursor].toInt())
        }
        fun withInt(offset: Int, value: Int): ByteArray = bytes.copyOf().also { ByteBuffer.wrap(it).putInt(offset, value) }
        fun replaceText(offset: Int, value: String): ByteArray {
            val encoded = value.encodeToByteArray()
            return bytes.copyOfRange(0, offset) + ByteBuffer.allocate(4).putInt(encoded.size).array() + encoded +
                bytes.copyOfRange(offset + 4 + buffer.getInt(offset), bytes.size)
        }
        fun withResourceDeclaration(): ByteArray {
            fun text(value: String) = ByteBuffer.allocate(4).putInt(value.length).array() + value.encodeToByteArray()
            val declaration = text("buffer") + ByteBuffer.allocate(4).putInt(0).array() + text("STORAGE_BUFFER") +
                ByteBuffer.allocate(8).putLong(4L).array()
            return bytes.copyOfRange(0, resourceCountOffset) + ByteBuffer.allocate(4).putInt(1).array() + declaration +
                bytes.copyOfRange(resourceCountOffset + 4, bytes.size)
        }
        fun withIdentity(id: String, version: Int): ByteArray {
            // Fixed child-opacity ABI, encoded independently using the spec's numeric
            // tags and little-endian integers. Only the logical ID/version vary.
            val canonical = java.io.ByteArrayOutputStream()
            fun i32(value: Int) { canonical.write(ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array()) }
            fun text(value: String) { i32(value.length); canonical.write(value.encodeToByteArray()) }
            canonical.write("kanvas-runtime-effect-abi-v1\u0000".encodeToByteArray())
            i32(1); i32(version); text(id); i32(1); i32(1); text("WgslFloatEnvelopeV1")
            i32(1); text("child"); i32(1); canonical.write(0)
            i32(16); i32(1); text("alpha"); i32(1); i32(0); i32(4); i32(4); i32(1); i32(0); i32(0)
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }.encodeToByteArray()
            require(id.length == ID.length)
            val result = bytes.copyOf()
            // The scene carries the effective material and the retained paint. Change
            // both copies, preserving their agreement, to isolate triplet lookup.
            bytes.indices.filter { index -> index + 4 + ID.length <= bytes.size &&
                buffer.getInt(index) == ID.length && bytes.copyOfRange(index + 4, index + 4 + ID.length).decodeToString() == ID
            }.forEach { start ->
                id.encodeToByteArray().copyInto(result, start + 4)
                ByteBuffer.wrap(result).putInt(start + versionOffset - idOffset, version)
                hash.copyInto(result, start + hashOffset - idOffset + 4)
            }
            return result
        }
    }
    companion object {
        private const val ID = "kanvas.runtime.child-opacity"
        private val BOUNDS = RectF32.ofLTRB(0f, 0f, 2f, 1f)
    }
}
