package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.kanvas.color.icc.IccSignature
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorTransformContractTest {

    @Test
    fun `compile rejects unsupported source profile`() {
        val result = ColorTransform.compile(
            source = ColorProfile.unsupported("icc.lut.missing"),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("icc.lut.missing", result.failureOrNull()!!.code)
    }

    @Test
    fun `compile rejects gray profiles`() {
        val result = ColorTransform.compile(
            source = ColorProfile(colorModel = ColorModel.GRAY),
            destination = ColorProfile(colorModel = ColorModel.GRAY),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("color.profile.unsupported", result.failureOrNull()!!.code)
    }

    @Test
    fun `compile rejects incomplete rgb profiles`() {
        val result = ColorTransform.compile(
            source = ColorProfile(
                colorModel = ColorModel.RGB,
                transferFunction = ColorProfiles.sRGB().transferFunction,
            ),
            destination = ColorProfile(
                colorModel = ColorModel.RGB,
                transferFunction = ColorProfiles.sRGB().transferFunction,
            ),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("color.profile.unsupported", result.failureOrNull()!!.code)
    }

    @Test
    fun `identical spaces use no op transform`() {
        val result = ColorTransform.compile(
            source = ColorProfiles.sRGB(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixels = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)

        result.apply(pixels, 1)

        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f), pixels)
    }

    @Test
    fun `compiled transforms retain a copy of caller supplied matrix`() {
        val matrix = SkcmsMatrix3x3.IDENTITY
        val profile = ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = matrix,
            transferFunction = ColorProfiles.sRGB().transferFunction,
        )
        val transform = ColorTransform.compile(
            source = profile,
            destination = profile,
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()

        matrix.vals[0][0] = 0f
        val pixels = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)
        transform.apply(pixels, 1)

        assertEquals(1f, profile.toXyzD50!![0, 0])
        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f), pixels)
    }

    @Test
    fun `matrix profiles compile into a nonidentity golden transform`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.displayP3(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.5f, 0.2f, 0.1f, 0.4f)

        transform.apply(pixel, 1)

        assertEquals(0.54172945f, pixel[0], 2e-5f)
        assertEquals(0.17373921f, pixel[1], 2e-5f)
        assertEquals(0.052882f, pixel[2], 2e-5f)
        assertEquals(0.4f, pixel[3], 0f)
    }

    @Test
    fun `mft2 LUT source transforms through PCS into matrix destination`() {
        val lut = parseResource("rgb-lut-a2b-b2a.icc")
        val transform = ColorTransform.compile(
            source = lut,
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.25f, 0.5f, 0.75f, 0.6f)

        transform.apply(pixel, 1)

        assertEquals(0.75f, pixel[0], 0.002f)
        assertEquals(0.5f, pixel[1], 0.002f)
        assertEquals(0.25f, pixel[2], 0.002f)
        assertEquals(0.6f, pixel[3], 0f)
    }

    @Test
    fun `mft2 A2B matches LittleCMS relative colorimetric PCS golden`() {
        val lut = parseResource("rgb-lut-a2b-b2a.icc")
        val pcs = floatArrayOf(0.25f, 0.5f, 0.75f)

        lut.toPcs!!.apply(pcs, 0)

        assertEquals(0.317566f, pcs[0], 3e-5f)
        assertEquals(0.272797f, pcs[1], 3e-5f)
        assertEquals(0.064392f, pcs[2], 3e-5f)
    }

    @Test
    fun `matrix source transforms through PCS into mft2 LUT destination`() {
        val lut = parseResource("rgb-lut-a2b-b2a.icc")
        val transform = ColorTransform.compile(
            source = ColorProfiles.sRGB(),
            destination = lut,
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.25f, 0.5f, 0.75f, 0.8f)

        transform.apply(pixel, 1)

        assertEquals(0.75f, pixel[0], 0.002f)
        assertEquals(0.5f, pixel[1], 0.002f)
        assertEquals(0.25f, pixel[2], 0.002f)
        assertEquals(0.8f, pixel[3], 0f)
    }

    @Test
    fun `opaque and unpremultiplied LUT transforms preserve alpha storage`() {
        val lut = parseResource("rgb-lut-a2b-b2a.icc")
        listOf(AlphaType.OPAQUE, AlphaType.UNPREMULTIPLIED).forEach { alphaType ->
            val transform = ColorTransform.compile(lut, ColorProfiles.sRGB(), alphaType).getOrThrow()
            val pixel = floatArrayOf(0.25f, 0.5f, 0.75f, 0.37f)

            transform.apply(pixel, 1)

            assertEquals(0.37f, pixel[3], 0f, alphaType.name)
        }
    }

    @Test
    fun `opaque and unpremultiplied matrix transforms preserve alpha storage`() {
        listOf(AlphaType.OPAQUE, AlphaType.UNPREMULTIPLIED).forEach { alphaType ->
            val transform = ColorTransform.compile(
                ColorProfiles.displayP3(),
                ColorProfiles.sRGB(),
                alphaType,
            ).getOrThrow()
            val pixel = floatArrayOf(0.5f, 0.2f, 0.1f, 0.37f)

            transform.apply(pixel, 1)

            assertEquals(0.37f, pixel[3], 0f, alphaType.name)
        }
    }

    @Test
    fun `premultiplied matrix pixels transform in unpremultiplied color space`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.displayP3(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.25f, 0.1f, 0.05f, 0.5f)

        transform.apply(pixel, 1)

        assertEquals(0.27086473f, pixel[0], 2e-5f)
        assertEquals(0.086869605f, pixel[1], 2e-5f)
        assertEquals(0.026441f, pixel[2], 2e-5f)
        assertEquals(0.5f, pixel[3], 0f)
    }

    @Test
    fun `premultiplied matrix pixels with zero alpha do not divide by zero`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.displayP3(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0f, 0f, 0f, 0f)

        transform.apply(pixel, 1)

        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0f), pixel)
    }

    @Test
    fun `premultiplied matrix pixels retain subnormal alpha colors`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.displayP3(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).getOrThrow()
        val alpha = Float.MIN_VALUE
        val pixel = floatArrayOf(alpha, alpha, alpha, alpha)

        transform.apply(pixel, 1)

        assertContentEquals(floatArrayOf(alpha, alpha, alpha, alpha), pixel)
    }

    @Test
    fun `premultiplied matrix pixels with nonfinite alpha store transparent black`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.displayP3(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).getOrThrow()
        listOf(
            Float.fromBits(0x7fc00042),
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
        ).forEach { alpha ->
            val pixel = floatArrayOf(0.25f, 0.1f, 0.05f, alpha)

            transform.apply(pixel, 1)

            assertContentEquals(floatArrayOf(0f, 0f, 0f), pixel.copyOfRange(0, 3))
            assertEquals(alpha.toRawBits(), pixel[3].toRawBits())
        }
    }

    @Test
    fun `matrix plan retains copies of nonidentity matrices`() {
        val sourceToXyzD50 = floatArrayOf(
            0.5f, 0f, 0f,
            0f, 0.5f, 0f,
            0f, 0f, 0.5f,
        )
        val destinationFromXyzD50 = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val plan = MatrixColorTransform(
            sourceToXyzD50,
            destinationFromXyzD50,
            ColorProfiles.sRGB().transferFunction!!,
            ColorProfiles.sRGB().transferFunction!!,
            AlphaType.UNPREMULTIPLIED,
        )
        sourceToXyzD50.fill(0f)
        destinationFromXyzD50.fill(0f)
        val pixel = floatArrayOf(0.5f, 0.2f, 0.1f, 0.7f)

        plan.apply(pixel, 0)

        assertTrue(pixel[0] > 0f)
        assertTrue(pixel[1] > 0f)
        assertTrue(pixel[2] > 0f)
        assertEquals(0.7f, pixel[3], 0f)
    }

    @Test
    fun `identical premultiplied matrix profiles use no op transform`() {
        val transform = ColorTransform.compile(
            source = ColorProfiles.sRGB(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.125f, 0.25f, 0.375f, 0.5f)

        transform.apply(pixel, 1)

        assertContentEquals(floatArrayOf(0.125f, 0.25f, 0.375f, 0.5f), pixel)
    }

    @Test
    fun `premultiplied LUT composition remains a typed refusal`() {
        val failure = ColorTransform.compile(
            source = parseResource("rgb-lut-a2b-b2a.icc"),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.PREMULTIPLIED,
        ).failureOrNull()

        assertEquals("color.alpha.premultiplied.unsupported", assertNotNull(failure).code)
    }

    @Test
    fun `one way input LUT compiles as source and refuses destination use`() {
        val input = parseOneWayInputResource("rgb-lut-a2b-b2a.icc")
        val transform = ColorTransform.compile(
            source = input,
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixel = floatArrayOf(0.25f, 0.5f, 0.75f, 1f)

        transform.apply(pixel, 1)

        assertEquals(0.75f, pixel[0], 0.002f)
        assertEquals(0.5f, pixel[1], 0.002f)
        assertEquals(0.25f, pixel[2], 0.002f)
        val failure = ColorTransform.compile(
            source = ColorProfiles.sRGB(),
            destination = input,
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).failureOrNull()
        assertEquals("icc.lut.b2a.missing", assertNotNull(failure).code)

        val noOp = ColorTransform.compile(input, input, AlphaType.UNPREMULTIPLIED).getOrThrow()
        val unchanged = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        noOp.apply(unchanged, 1)
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), unchanged)
    }

    @Test
    fun `compile rejects direct profiles with nonmonotonic transfer functions`() {
        val source = ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = SkcmsMatrix3x3.IDENTITY,
            transferFunction = SkcmsTransferFunction(
                g = 1f,
                a = 1f,
                b = -0.5f,
                c = 1f,
                d = 0.5f,
                e = 0f,
                f = 0f,
            ),
        )

        val failure = ColorTransform.compile(
            source,
            ColorProfiles.sRGB(),
            AlphaType.UNPREMULTIPLIED,
        ).failureOrNull()

        assertEquals("color.profile.transfer", assertNotNull(failure).code)
    }

    @Test
    fun `pq rec2020 to srgb compiles bt2390 tone mapping to 100 nit sdr`() {
        val source = CicpColorInfo(9, 16, 0, true).toColorProfile().getOrThrow()
        val transform = ColorTransform.compile(
            source,
            ColorProfiles.sRGB(),
            AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pq1000Nits = 0.7518271f
        val pixel = floatArrayOf(pq1000Nits, pq1000Nits, pq1000Nits, 0.37f)

        transform.apply(pixel, 1)

        assertEquals(0.9596758f, pixel[0], 3e-4f)
        assertEquals(pixel[0], pixel[1], 3e-4f)
        assertEquals(pixel[1], pixel[2], 3e-4f)
        assertEquals(0.37f, pixel[3], 0f)
    }

    @Test
    fun `hdr to hdr profile conversion does not tone map`() {
        val pq = CicpColorInfo(9, 16, 0, true).toColorProfile().getOrThrow()
        val hlg = CicpColorInfo(9, 18, 0, true).toColorProfile().getOrThrow()
        val toHlg = ColorTransform.compile(pq, hlg, AlphaType.UNPREMULTIPLIED).getOrThrow()
        val backToPq = ColorTransform.compile(hlg, pq, AlphaType.UNPREMULTIPLIED).getOrThrow()
        val pixel = floatArrayOf(0.7518271f, 0.7518271f, 0.7518271f, 1f)

        toHlg.apply(pixel, 1)
        backToPq.apply(pixel, 1)

        assertEquals(0.7518271f, pixel[0], 4e-5f)
        assertEquals(pixel[0], pixel[1], 2e-5f)
        assertEquals(pixel[1], pixel[2], 2e-5f)
    }

    @Test
    fun `premultiplied hdr to sdr conversion preserves alpha convention`() {
        val pq = CicpColorInfo(9, 16, 0, true).toColorProfile().getOrThrow()
        val transform = ColorTransform.compile(pq, ColorProfiles.sRGB(), AlphaType.PREMULTIPLIED).getOrThrow()
        val alpha = 0.5f
        val pixel = floatArrayOf(0.7518271f * alpha, 0.7518271f * alpha, 0.7518271f * alpha, alpha)

        transform.apply(pixel, 1)

        assertEquals(0.9596758f * alpha, pixel[0], 3e-4f)
        assertEquals(pixel[0], pixel[1], 2e-4f)
        assertEquals(pixel[1], pixel[2], 2e-4f)
        assertEquals(alpha, pixel[3], 0f)
    }

    @Test
    fun `hdr and lut composition remains a typed refusal`() {
        val pq = CicpColorInfo(9, 16, 0, true).toColorProfile().getOrThrow()
        val failure = ColorTransform.compile(
            pq,
            parseResource("rgb-lut-a2b-b2a.icc"),
            AlphaType.UNPREMULTIPLIED,
        ).failureOrNull()

        assertEquals("color.hdr.lut.unsupported", assertNotNull(failure).code)
    }

    private fun parseResource(name: String): ColorProfile {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("icc/$name"), "missing icc/$name")
        return stream.use { IccProfileParser.parse(it.readBytes(), IccParseLimits()).getOrThrow() }
    }

    private fun parseOneWayInputResource(name: String): ColorProfile {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("icc/$name"), "missing icc/$name")
        val bytes = stream.use { it.readBytes() }
        writeU32(bytes, 12, IccSignature.INPUT_CLASS.value)
        repeat(readU32(bytes, 128)) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == IccSignature.B_TO_A_0.value) writeU32(bytes, entry, 0x7a7a7a7a)
        }
        repeat(16) { bytes[84 + it] = 0 }
        return IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
