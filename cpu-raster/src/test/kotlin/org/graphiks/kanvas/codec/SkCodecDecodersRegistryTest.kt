package org.graphiks.kanvas.codec

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkBitmap
import org.skia.foundation.skcms.SkcmsICCProfile

/**
 * R-suivi.47 verification — [Codec.Decoders] is now a public registry
 * that supports late registration of additional formats. Verifies :
 *  - the built-in decoders are present at startup,
 *  - the four extended-format stubs (AVIF / JPEG-XL / RAW / ICO) self-
 *    register at class-init time,
 *  - a third-party decoder can be registered and dispatch routes to it,
 *  - re-registering the same name replaces the prior entry,
 *  - [Codec.Decoders.unregister] removes an entry cleanly.
 */
class CodecDecodersRegistryTest {

    @AfterEach
    fun cleanup() {
        // Drop any test fixtures we registered to avoid leaking into
        // sibling test cases.
        Codec.Decoders.unregister(FAKE_NAME)
        Codec.Decoders.unregister(STEAL_AVIF_NAME)
    }

    @Test
    fun `built-in decoders are registered at startup`() {
        // Touch Decoders to trigger init.
        val names = Codec.Decoders.all().map { it.name }
        // Built-ins.
        assertTrue("png" in names, "png decoder missing")
        assertTrue("jpeg" in names, "jpeg decoder missing")
        assertTrue("gif" in names, "gif decoder missing")
        assertTrue("bmp" in names, "bmp decoder missing")
        assertTrue("webp" in names, "webp decoder missing")
        assertTrue("wbmp" in names, "wbmp decoder missing")
    }

    @Test
    fun `extended-format stubs self-register`() {
        val names = Codec.Decoders.all().map { it.name }
        assertTrue("avif" in names, "avif stub missing")
        assertTrue("jpegxl" in names, "jpegxl stub missing")
        assertTrue("ico" in names, "ico stub missing")
        assertTrue("raw" in names, "raw stub missing")
    }

    @Test
    fun `contains returns true for registered names`() {
        assertTrue(Codec.Decoders.contains("png"))
        assertTrue(Codec.Decoders.contains("avif"))
        assertFalse(Codec.Decoders.contains("definitely-not-a-decoder"))
    }

    @Test
    fun `register routes dispatch to the new decoder`() {
        val fake = FakeDecoder(FAKE_NAME, signature = byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Codec.Decoders.register(fake)
        assertTrue(Codec.Decoders.contains(FAKE_NAME))
        // Pass bytes carrying the fake signature : dispatch must call
        // into the fake's `make`.
        val codec = Codec.Decoders.dispatch(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0x00, 0x00))
        assertNotNull(codec)
        assertEquals(SkEncodedImageFormat.kPNG, codec!!.getEncodedFormat())
        // The fake's makeCount should be 1.
        assertEquals(1, fake.makeCount)
    }

    @Test
    fun `register replaces an existing entry with the same name`() {
        val v1 = FakeDecoder(FAKE_NAME, signature = byteArrayOf(0xAA.toByte()))
        val v2 = FakeDecoder(FAKE_NAME, signature = byteArrayOf(0xBB.toByte()))
        Codec.Decoders.register(v1)
        Codec.Decoders.register(v2)
        // Only one entry with that name.
        val count = Codec.Decoders.all().count { it.name == FAKE_NAME }
        assertEquals(1, count)
        // v2's signature is the one consulted now.
        assertNotNull(Codec.Decoders.dispatch(byteArrayOf(0xBB.toByte(), 0x00)))
        assertNull(Codec.Decoders.dispatch(byteArrayOf(0xAA.toByte(), 0x00)))
    }

    @Test
    fun `unregister removes the entry`() {
        Codec.Decoders.register(FakeDecoder(FAKE_NAME, signature = byteArrayOf(0x42)))
        assertTrue(Codec.Decoders.contains(FAKE_NAME))
        val removed = Codec.Decoders.unregister(FAKE_NAME)
        assertTrue(removed)
        assertFalse(Codec.Decoders.contains(FAKE_NAME))
        // Second unregister is a no-op.
        assertFalse(Codec.Decoders.unregister(FAKE_NAME))
    }

    @Test
    fun `AVIF stub make returns null because back-end is not wired`() {
        // Confirm the registry-routing path : an `ftypavif`-tagged blob
        // matches the AVIF stub, which returns null until R-suivi.28
        // ships a real back-end. The stub MUST NOT steal the dispatch
        // away from a hypothetical future real AVIF decoder.
        val avifMagic = ByteArray(12).apply {
            // 4-byte box size (anything ≥ 12) | 'ftypavif'
            this[4] = 'f'.code.toByte(); this[5] = 't'.code.toByte()
            this[6] = 'y'.code.toByte(); this[7] = 'p'.code.toByte()
            this[8] = 'a'.code.toByte(); this[9] = 'v'.code.toByte()
            this[10] = 'i'.code.toByte(); this[11] = 'f'.code.toByte()
        }
        // Stub returns null — the IS_AVIF check succeeds but Decode is unimplemented.
        assertNull(Codec.Decoders.dispatch(avifMagic))
    }

    @Test
    fun `re-registering AVIF with a real decoder takes over routing`() {
        // Simulate the R-suivi.28 wire-up : a real AVIF decoder shadows
        // the stub. After replacement, dispatch returns the real codec.
        val avifMagic = ByteArray(12).apply {
            this[4] = 'f'.code.toByte(); this[5] = 't'.code.toByte()
            this[6] = 'y'.code.toByte(); this[7] = 'p'.code.toByte()
            this[8] = 'a'.code.toByte(); this[9] = 'v'.code.toByte()
            this[10] = 'i'.code.toByte(); this[11] = 'f'.code.toByte()
        }
        // Initial state : stub returns null.
        assertNull(Codec.Decoders.dispatch(avifMagic))

        try {
            // Register a real AVIF decoder under the same name.
            Codec.Decoders.register(object : Codec.Decoder {
                override val name: String = "avif"
                override fun matches(data: ByteArray): Boolean = AvifDecoder.IsAvif(data)
                override fun make(data: ByteArray): Codec? = FakeCodec()
            })
            val codec = Codec.Decoders.dispatch(avifMagic)
            assertNotNull(codec, "real AVIF decoder must take over routing")
        } finally {
            // Restore stub via the registry's init contract — register the
            // stub's RegistryEntry signature back. Since we override-by-name,
            // re-installing the stub puts the real null-Decode back in place.
            Codec.Decoders.register(object : Codec.Decoder {
                override val name: String = "avif"
                override fun matches(data: ByteArray): Boolean = AvifDecoder.IsAvif(data)
                override fun make(data: ByteArray): Codec? = AvifDecoder.Decode(data)
            })
        }
    }

    private companion object {
        private const val FAKE_NAME = "fake-test-decoder"
        private const val STEAL_AVIF_NAME = "avif-test-shadow"
    }

    private class FakeDecoder(
        override val name: String,
        private val signature: ByteArray,
    ) : Codec.Decoder {
        var makeCount: Int = 0
            private set

        override fun matches(data: ByteArray): Boolean {
            if (data.size < signature.size) return false
            for (i in signature.indices) {
                if (data[i] != signature[i]) return false
            }
            return true
        }

        override fun make(data: ByteArray): Codec? {
            makeCount += 1
            return FakeCodec()
        }
    }

    private class FakeCodec : Codec() {
        private val info: SkImageInfo = SkImageInfo.Make(1, 1)
        override fun getInfo(): SkImageInfo = info
        override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG
        override fun getICCProfile(): SkcmsICCProfile? = null
        override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result = Result.kSuccess
    }
}
