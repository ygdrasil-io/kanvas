package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IccProfileParserTest {

    @Test
    fun `parses srgb matrix trc profile`() {
        val profile = parseResource("srgb-matrix-trc.icc")

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertMatrixNear(ColorProfiles.sRGB().toXyzD50!!, profile.toXyzD50!!)
        assertTransferFunctionNear(ColorProfiles.sRGB().transferFunction!!, profile.transferFunction!!)
    }

    @Test
    fun `parses display p3 matrix trc profile`() {
        val profile = parseResource("display-p3-matrix-trc.icc")

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertEquals(ColorProfiles.displayP3().toXyzD50, profile.toXyzD50)
        assertTransferFunctionNear(ColorProfiles.displayP3().transferFunction!!, profile.transferFunction!!)
    }

    @Test
    fun `parsed type 4 curve stays finite and nondecreasing at its exact threshold`() {
        val bytes = resource("srgb-matrix-trc.icc")
        IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        val parameters = FloatArray(7) { index -> readS15Fixed16(bytes, curveOffset + 12 + index * 4) }
        val curve = ParametricIccCurve(4, parameters)
        val threshold = parameters[4]
        val below = curve.evaluate(threshold - 1f / 65536f)
        val at = curve.evaluate(threshold)

        assertTrue(below.isFinite())
        assertTrue(at.isFinite())
        assertTrue(below <= at)
        assertEquals(parameters[3] * threshold + parameters[6], at, 0f)
    }

    @Test
    fun `parses independent compact display p3 v4 matrix trc profile with upward gap`() {
        val bytes = resource("compact-display-p3-v4.icc")
        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        val trc = tagOffset(bytes, IccSignature.R_TRC.value)
        assertEquals(IccSignature.PARAMETRIC_CURVE_TYPE.value, readU32(bytes, trc))
        assertEquals(3, readU16(bytes, trc + 8))
        assertEquals(32, tagSize(bytes, IccSignature.R_TRC.value))
    }

    @Test
    fun `parses authentic v2 curv with zero aligned legacy surplus`() {
        val profile = parseResource("icc-rec709-v2.icc")

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertMatrixNear(ColorProfiles.sRGB().toXyzD50!!, profile.toXyzD50!!)
        assertEquals(0x0266 / 256f, profile.transferFunction!!.g, 0f)
    }

    @Test
    fun `rejects nonzero or nonaligned legacy curv surplus`() {
        val nonZeroSurplus = resource("icc-rec709-v2.icc")
        nonZeroSurplus[tagOffset(nonZeroSurplus, IccSignature.R_TRC.value) + 14] = 1
        val nonAlignedSurplus = resource("icc-rec709-v2.icc")
        setSharedCurveTagSize(nonAlignedSurplus, 15)
        val v4AlignedSurplus = rewriteSharedCurv(intArrayOf(0x0200))
        setSharedCurveTagSize(v4AlignedSurplus, 16)

        assertFailure("icc.curve.range", nonZeroSurplus)
        assertFailure("icc.curve.range", nonAlignedSurplus)
        assertFailure("icc.curve.range", v4AlignedSurplus)
    }

    @Test
    fun `parses independent compact dci p3 v4 matrix trc profile`() {
        val bytes = resource("compact-dci-p3-v4.icc")
        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertEquals(0x0002999a / 65536f, profile.transferFunction!!.g, 0f)
        assertEquals(4, bytes[8].toInt() and 0xff)
        assertEquals(
            IccSignature.MULTI_LOCALIZED_UNICODE_TYPE.value,
            readU32(bytes, tagOffset(bytes, signature("desc"))),
        )
        assertEquals(
            IccSignature.MULTI_LOCALIZED_UNICODE_TYPE.value,
            readU32(bytes, tagOffset(bytes, signature("cprt"))),
        )
        val trc = tagOffset(bytes, IccSignature.R_TRC.value)
        assertEquals(IccSignature.PARAMETRIC_CURVE_TYPE.value, readU32(bytes, trc))
        assertEquals(0, readU16(bytes, trc + 8))
        assertEquals(16, tagSize(bytes, IccSignature.R_TRC.value))
    }

    @Test
    fun `rejects tag range outside profile bytes`() {
        val failure = IccProfileParser.parse(resource("invalid-tag-offset.icc"), IccParseLimits())
            .failureOrNull()!!

        assertEquals("icc.tag.range", failure.code)
    }

    @Test
    fun `rejects duplicate tag signatures`() {
        val bytes = resource("srgb-matrix-trc.icc")
        copyFourBytes(bytes, sourceOffset = 132, destinationOffset = 144)

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.tag.duplicate", failure.code)
    }

    @Test
    fun `enforces max bytes before parsing the header`() {
        val failure = IccProfileParser.parse(
            bytes = ByteArray(129),
            limits = IccParseLimits(maxBytes = 128),
        ).failureOrNull()!!

        assertEquals("icc.limit.bytes", failure.code)
    }

    @Test
    fun `enforces max tags before allocating tag records`() {
        val failure = IccProfileParser.parse(
            bytes = resource("srgb-matrix-trc.icc"),
            limits = IccParseLimits(maxTags = 1),
        ).failureOrNull()!!

        assertEquals("icc.limit.tags", failure.code)
    }

    @Test
    fun `rejects malformed ICC signature`() {
        val bytes = resource("srgb-matrix-trc.icc")
        bytes[36] = 'X'.code.toByte()

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.header.signature", failure.code)
    }

    @Test
    fun `rejects unsupported parametric curve selector`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        bytes[curveOffset + 8] = 0
        bytes[curveOffset + 9] = 5

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.curve.type", failure.code)
    }

    @Test
    fun `rejects incomplete RGB matrix trc tags`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.R_XYZ.value), signature("junk"))

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.profile.tags", failure.code)
    }

    @Test
    fun `rejects missing common required metadata tag`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.COPYRIGHT.value), signature("junk"))

        assertFailure("icc.profile.tags", bytes)
    }

    @Test
    fun `positive v4 fixtures contain required tags and zero reserved fields`() {
        listOf(
            "srgb-matrix-trc.icc",
            "display-p3-matrix-trc.icc",
            "compact-dci-p3-v4.icc",
            "compact-display-p3-v4.icc",
        ).forEach { name ->
            val bytes = resource(name)
            val signatures = tagSignatures(bytes)

            assertTrue(signature("desc") in signatures, "$name desc")
            assertTrue(signature("cprt") in signatures, "$name cprt")
            assertTrue(signature("wtpt") in signatures, "$name wtpt")
            assertTrue(bytes.slice(100 until 128).all { it == 0.toByte() }, "$name header reserved")
            repeat(readU32(bytes, 128)) { index ->
                val tagOffset = readU32(bytes, 132 + index * 12 + 4)
                assertTrue(bytes.slice(tagOffset + 4 until tagOffset + 8).all { it == 0.toByte() })
            }
        }
    }

    @Test
    fun `parses grayscale matrix trc profile`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 16, IccSignature.GRAY.value)
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.R_TRC.value), IccSignature.K_TRC.value)

        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(ColorModel.GRAY, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
    }

    @Test
    fun `parses para selectors zero through four`() {
        val parameters = listOf(
            floatArrayOf(2f),
            floatArrayOf(2f, 1f, -0.25f),
            floatArrayOf(2f, 1f, -0.25f, 0.1f),
            floatArrayOf(2f, 1f, 0f, 0.25f, 0.25f),
            floatArrayOf(2f, 1f, 0f, 0.25f, 0.25f, 0f, 0f),
        )

        parameters.forEachIndexed { selector, values ->
            val bytes = rewriteSharedParametricCurve(selector, values)

            val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

            assertEquals(2f, profile.transferFunction!!.g, 0f, "selector $selector")
        }
    }

    @Test
    fun `para type 4 parser uses standard g a b c d e f order`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeSharedParametricCurve(bytes, 4, floatArrayOf(2f, 1f, 0f, 0.25f, 0.25f, 0f, 0f))

        val transferFunction = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow().transferFunction!!

        assertEquals(0.25f, transferFunction.c, 0f)
        assertEquals(0f, transferFunction.e, 0f)
    }

    @Test
    fun `rejects para whose nonlinear branch is undefined`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(0.5f, 1f, -0.5f, 1f, 0.25f))

        assertFailure("icc.curve.values", bytes)
    }

    @Test
    fun `rejects para with negative nonlinear base exactly at threshold`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(0.5f, 1f, -0.2505f, 0f, 0.25f))

        assertFailure("icc.curve.values", bytes)
    }

    @Test
    fun `rejects para with decreasing lower branch`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(2f, 1f, 0f, -0.5f, 0.25f))

        assertFailure("icc.curve.values", bytes)
    }

    @Test
    fun `rejects para with downward jump`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(2f, 1f, 0f, 1f, 0.5f))

        assertFailure("icc.curve.values", bytes)
    }

    @Test
    fun `accepts para with upward gap and returns the threshold for an inverse gap value`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(2f, 1f, 0f, 0.1f, 0.5f))
        val curve = ParametricIccCurve(3, floatArrayOf(2f, 1f, 0f, 0.1f, 0.5f))

        IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
        assertEquals(0.5f, curve.inverse(0.15f), 0f)
    }

    @Test
    fun `parametric gap inverse chooses the closest boundary with upper tie rule`() {
        val typeThree = ParametricIccCurve(3, floatArrayOf(2f, 1f, 0f, 0.1f, 0.5f))
        val typeFour = ParametricIccCurve(4, floatArrayOf(2f, 1f, 0f, 0.1f, 0.5f, 0f, 0f))
        val typeThreeTie = ParametricIccCurve(3, floatArrayOf(2f, 1f, 0f, 0f, 0.5f))
        val typeFourTie = ParametricIccCurve(4, floatArrayOf(2f, 1f, 0f, 0f, 0.5f, 0f, 0f))
        val lowerBoundary = Math.nextDown(0.5f)

        assertEquals(lowerBoundary, typeThree.inverse(0.06f), 0f)
        assertEquals(lowerBoundary, typeFour.inverse(0.06f), 0f)
        assertEquals(0.5f, typeThree.inverse(0.15f), 0f)
        assertEquals(0.5f, typeFour.inverse(0.15f), 0f)
        assertEquals(0.5f, typeThreeTie.inverse(0.125f), 0f)
        assertEquals(0.5f, typeFourTie.inverse(0.125f), 0f)
    }

    @Test
    fun `parametric inverse returns the first lower branch terminal plateau value`() {
        val typeThree = ParametricIccCurve(3, floatArrayOf(1f, 1f, 0.25f, 2f, 0.75f))
        val typeFour = ParametricIccCurve(4, floatArrayOf(1f, 1f, 0.25f, 2f, 0.75f, 0f, 0f))

        assertEquals(0.5f, typeThree.inverse(1f), 0f)
        assertEquals(0.5f, typeFour.inverse(1f), 0f)
    }

    @Test
    fun `accepts clipped and single branch parametric curves`() {
        val clippedTypeFour = floatArrayOf(1f, 1f, 0f, 1f, 0.5f, 0.25f, 0f)
        val lowerOnlyTypeThree = floatArrayOf(1f, 1f, 0f, 0.5f, 2f)
        val overflowingButClippedTypeThree = floatArrayOf(32767f, 1f, 1f, 0.5f, 0.5f)
        val typeFourCurve = ParametricIccCurve(4, clippedTypeFour)

        IccProfileParser.parse(rewriteSharedParametricCurve(4, clippedTypeFour), IccParseLimits()).getOrThrow()
        IccProfileParser.parse(rewriteSharedParametricCurve(3, lowerOnlyTypeThree), IccParseLimits()).getOrThrow()
        IccProfileParser.parse(rewriteSharedParametricCurve(3, overflowingButClippedTypeThree), IccParseLimits()).getOrThrow()
        assertEquals(1f, typeFourCurve.evaluate(1f), 0f)
        assertEquals(0.75f, typeFourCurve.inverse(1f), 0f)
        assertEquals(1f, ParametricIccCurve(3, lowerOnlyTypeThree).inverse(1f), 0f)
        assertEquals(1f, ParametricIccCurve(3, overflowingButClippedTypeThree).evaluate(1f), 0f)
    }

    @Test
    fun `rejects para with a quantized downward jump at threshold`() {
        val bytes = rewriteSharedParametricCurve(3, floatArrayOf(2f, 1f, -0.25f, 0.002f, 0.25f))

        assertFailure("icc.curve.values", bytes)
    }

    @Test
    fun `parses single value curv as gamma`() {
        val bytes = rewriteSharedCurv(intArrayOf(0x0200))

        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(2f, profile.transferFunction!!.g, 0f)
    }

    @Test
    fun `sampled curv returns typed refusal instead of srgb`() {
        val bytes = rewriteSharedCurv(intArrayOf(0, 0x4000, 0xffff))

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.curve.sampled", failure.code)
    }

    @Test
    fun `sampled curv is refused before sample values are inspected`() {
        val bytes = rewriteSharedCurv(intArrayOf(0xffff, 0, 0xffff))

        assertFailure("icc.curve.sampled", bytes)
    }

    @Test
    fun `rejects para selectors whose tag retains stale type 4 size`() {
        repeat(4) { selector ->
            val bytes = resource("srgb-matrix-trc.icc")
            val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
            writeU16(bytes, curveOffset + 8, selector)

            assertFailure("icc.curve.range", bytes)
        }
    }

    @Test
    fun `rejects curv counts whose tag retains stale payload bytes`() {
        listOf(0, 1, 2).forEach { count ->
            val bytes = resource("srgb-matrix-trc.icc")
            val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
            writeU32(bytes, curveOffset, IccSignature.CURVE_TYPE.value)
            writeU32(bytes, curveOffset + 8, count)

            assertFailure("icc.curve.range", bytes)
        }
    }

    @Test
    fun `sampled curv interpolates and inverts monotonically`() {
        val curve = SampledIccCurve(floatArrayOf(0f, 0.25f, 1f))

        assertEquals(0.125f, curve.evaluate(0.25f), 1e-6f)
        assertEquals(0.25f, curve.inverse(0.125f), 1e-6f)
        assertEquals(1f, curve.evaluate(2f), 0f)
    }

    @Test
    fun `curve inverses follow Annex F plateau boundaries`() {
        val typeOne = ParametricIccCurve(1, floatArrayOf(2f, 1f, -0.25f))
        val typeTwo = ParametricIccCurve(2, floatArrayOf(2f, 1f, -0.25f, 0.1f))
        val initial = SampledIccCurve(floatArrayOf(0f, 0f, 0.5f, 1f))
        val internal = SampledIccCurve(floatArrayOf(0f, 0.25f, 0.25f, 1f))
        val trailing = SampledIccCurve(floatArrayOf(0f, 0.5f, 1f, 1f))

        assertEquals(0.25f, typeOne.inverse(0f), 0f)
        assertEquals(0.25f, typeTwo.inverse(0.1f), 0f)
        assertEquals(1f / 3f, initial.inverse(0f), 0f)
        assertEquals(2f / 3f, internal.inverse(0.25f), 0f)
        assertEquals(2f / 3f, trailing.inverse(1f), 0f)
    }

    @Test
    fun `direct sampled curve rejects oversized input`() {
        assertFailsWith<IllegalArgumentException> {
            SampledIccCurve(FloatArray(65_537))
        }
    }

    @Test
    fun `direct curve APIs return finite values for non finite inputs`() {
        val parametric = ParametricIccCurve(0, floatArrayOf(2f))
        val sampled = SampledIccCurve(floatArrayOf(0f, 1f))

        assertTrue(parametric.evaluate(Float.NaN).isFinite())
        assertTrue(parametric.inverse(Float.POSITIVE_INFINITY).isFinite())
        assertTrue(sampled.evaluate(Float.NEGATIVE_INFINITY).isFinite())
        assertTrue(sampled.inverse(Float.NaN).isFinite())
    }

    @Test
    fun `para type 0 evaluates gamma`() {
        val curve = ParametricIccCurve(0, floatArrayOf(2f))

        assertEquals(0.25f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.25f), 1e-6f)
    }

    @Test
    fun `para type 1 evaluates thresholded gamma`() {
        val curve = ParametricIccCurve(1, floatArrayOf(2f, 1f, -0.25f))

        assertEquals(0f, curve.evaluate(0.2f), 0f)
        assertEquals(0.0625f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.0625f), 1e-6f)
    }

    @Test
    fun `para type 2 evaluates offset gamma`() {
        val curve = ParametricIccCurve(2, floatArrayOf(2f, 1f, -0.25f, 0.1f))

        assertEquals(0.1f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.1625f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.1625f), 1e-6f)
    }

    @Test
    fun `para type 3 evaluates linear lower segment`() {
        val curve = ParametricIccCurve(3, floatArrayOf(2f, 1f, 0f, 0.25f, 0.25f))

        assertEquals(0.05f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.25f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.2f, curve.inverse(0.05f), 1e-6f)
    }

    @Test
    fun `para type 4 evaluates both offset segments`() {
        val curve = ParametricIccCurve(4, floatArrayOf(2f, 1f, 0f, 0.25f, 0.25f, 0f, 0f))

        assertEquals(0.05f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.25f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.2f, curve.inverse(0.05f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.25f), 1e-6f)
    }

    @Test
    fun `direct para rejects non finite behavior`() {
        assertFailsWith<IllegalArgumentException> {
            ParametricIccCurve(3, floatArrayOf(0.5f, 1f, -0.5f, 1f, 0.25f))
        }
    }

    @Test
    fun `rejects v2 profile containing v4 para tags`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 8, 0x02400000)
        writeU32(bytes, tagOffset(bytes, IccSignature.DESCRIPTION.value), IccSignature.DESCRIPTION_TYPE.value)
        writeU32(bytes, tagOffset(bytes, IccSignature.COPYRIGHT.value), IccSignature.TEXT_TYPE.value)

        assertFailure("icc.curve.version", bytes)
    }

    @Test
    fun `rejects RGB output matrix trc profile`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 12, IccSignature.OUTPUT_CLASS.value)

        assertFailure("icc.header.class", bytes)
    }

    @Test
    fun `rejects invalid version encoding and reserved bytes`() {
        val invalidBcd = resource("srgb-matrix-trc.icc").also { it[9] = 0x4a }
        val unsupportedMinor = resource("srgb-matrix-trc.icc").also { it[9] = 0x50 }
        val invalidVersionReserved = resource("srgb-matrix-trc.icc").also { it[10] = 1 }

        assertFailure("icc.header.version", invalidBcd)
        assertFailure("icc.header.version", unsupportedMinor)
        assertFailure("icc.header.version", invalidVersionReserved)
    }

    @Test
    fun `rejects nonzero header reserved bytes`() {
        val bytes = resource("srgb-matrix-trc.icc")
        bytes[100] = 1

        assertFailure("icc.header.reserved", bytes)
    }

    @Test
    fun `rejects reserved device attribute bits before parsing tags`() {
        val bytes = resource("srgb-matrix-trc.icc")
        bytes[60] = 1

        assertFailure("icc.header.attributes", bytes)
    }

    @Test
    fun `allows defined device attributes and vendor bits`() {
        val bytes = resource("srgb-matrix-trc.icc")
        bytes[56] = 1
        bytes[63] = 0x0f

        IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
    }

    @Test
    fun `rejects reserved or undefined rendering intents before parsing tags`() {
        val reserved = resource("srgb-matrix-trc.icc").also { it[64] = 1 }
        val undefined = resource("srgb-matrix-trc.icc").also { it[67] = 4 }

        assertFailure("icc.header.intent", reserved)
        assertFailure("icc.header.intent", undefined)
    }

    @Test
    fun `allows rendering intents zero through three`() {
        repeat(4) { intent ->
            val bytes = resource("srgb-matrix-trc.icc")
            bytes[67] = intent.toByte()

            IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
        }
    }

    @Test
    fun `rejects nonzero tag reserved bytes`() {
        val xyz = resource("srgb-matrix-trc.icc")
        xyz[tagOffset(xyz, IccSignature.R_XYZ.value) + 4] = 1
        val para = resource("srgb-matrix-trc.icc")
        para[tagOffset(para, IccSignature.R_TRC.value) + 10] = 1

        assertFailure("icc.tag.reserved", xyz)
        assertFailure("icc.tag.reserved", para)
    }

    @Test
    fun `rejects nonzero external alignment padding`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val descriptionOffset = tagOffset(bytes, IccSignature.DESCRIPTION.value)
        bytes[descriptionOffset + tagSize(bytes, IccSignature.DESCRIPTION.value)] = 0x7f

        assertFailure("icc.profile.padding", bytes)
    }

    @Test
    fun `rejects missing final alignment padding with a typed failure`() {
        val canonical = rewriteSharedCurv(intArrayOf(0x0200))
        val bytes = canonical.copyOf(canonical.size - 2)
        writeU32(bytes, 0, bytes.size)

        assertFailure("icc.profile.envelope", bytes)
    }

    @Test
    fun `rejects suffix data on required XYZ tags`() {
        val whitePoint = appendTagSuffix(resource("srgb-matrix-trc.icc"), IccSignature.WHITE_POINT.value)
        val redColumn = appendTagSuffix(resource("srgb-matrix-trc.icc"), IccSignature.R_XYZ.value)

        assertFailure("icc.tag.type", whitePoint)
        assertFailure("icc.tag.type", redColumn)
    }

    @Test
    fun `display media white point must round to D50 while input keeps its own policy`() {
        val display = resource("srgb-matrix-trc.icc")
        writeS15Fixed16(display, tagOffset(display, IccSignature.WHITE_POINT.value) + 8, 0.955f)
        val input = resource("srgb-matrix-trc.icc")
        writeU32(input, 12, IccSignature.INPUT_CLASS.value)
        writeS15Fixed16(input, tagOffset(input, IccSignature.WHITE_POINT.value) + 8, 0.955f)

        assertFailure("icc.tag.white-point", display)
        IccProfileParser.parse(input, IccParseLimits()).getOrThrow()
    }

    @Test
    fun `accepts only illuminants that round to D50 at four decimals`() {
        val inside = resource("srgb-matrix-trc.icc")
        writeU32(inside, 68, readU32(inside, 68) + 3)
        val outside = resource("srgb-matrix-trc.icc")
        writeU32(outside, 68, readU32(outside, 68) + 4)

        IccProfileParser.parse(inside, IccParseLimits()).getOrThrow()
        assertFailure("icc.header.illuminant", outside)
    }

    @Test
    fun `gray profiles retain canonical D50 rather than header rounding noise`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 16, IccSignature.GRAY.value)
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.R_TRC.value), IccSignature.K_TRC.value)
        writeU32(bytes, 68, readU32(bytes, 68) + 3)

        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(0.9642f, profile.toXyzD50!![0, 0], 0f)
        assertEquals(1f, profile.toXyzD50!![1, 1], 0f)
        assertEquals(0.8249f, profile.toXyzD50!![2, 2], 0f)
    }

    @Test
    fun `rejects partial tag overlap but allows exact sharing`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val redEntry = tagEntryOffset(bytes, IccSignature.R_XYZ.value)
        writeU32(bytes, redEntry + 4, tagOffset(bytes, IccSignature.WHITE_POINT.value) + 4)

        assertFailure("icc.tag.overlap", bytes)
        parseResource("srgb-matrix-trc.icc")
    }

    @Test
    fun `rejects trailing input outside declared profile`() {
        val bytes = resource("srgb-matrix-trc.icc").copyOf(resource("srgb-matrix-trc.icc").size + 1)

        assertFailure("icc.header.size", bytes)
    }

    @Test
    fun `rejects unexplained bytes inside declared profile envelope`() {
        val original = resource("srgb-matrix-trc.icc")
        val bytes = original.copyOf(original.size + 4)
        writeU32(bytes, 0, bytes.size)

        assertFailure("icc.profile.envelope", bytes)
    }

    @Test
    fun `rejects singular RGB matrix`() {
        val bytes = resource("srgb-matrix-trc.icc")
        listOf(IccSignature.R_XYZ, IccSignature.G_XYZ, IccSignature.B_XYZ).forEach { signature ->
            val offset = tagOffset(bytes, signature.value)
            repeat(12) { bytes[offset + 8 + it] = 0 }
        }

        assertFailure("icc.profile.matrix", bytes)
    }

    @Test
    fun `rejects unequal RGB transfer curves`() {
        val original = resource("srgb-matrix-trc.icc")
        val bytes = original.copyOf(original.size + 16)
        writeU32(bytes, 0, bytes.size)
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.G_TRC.value) + 4, original.size)
        writeU32(bytes, tagEntryOffset(bytes, IccSignature.G_TRC.value) + 8, 16)
        writeU32(bytes, original.size, IccSignature.PARAMETRIC_CURVE_TYPE.value)
        writeS15Fixed16(bytes, original.size + 12, 2.2f)

        assertFailure("icc.profile.trc", bytes)
    }

    private fun parseResource(name: String) = IccProfileParser.parse(resource(name), IccParseLimits()).getOrThrow()

    private fun resource(name: String): ByteArray {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("icc/$name"), "missing icc/$name")
        return stream.use { it.readBytes() }
    }

    private fun tagOffset(bytes: ByteArray, signature: Int): Int {
        val count = readU32(bytes, 128)
        repeat(count) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == signature) return readU32(bytes, entry + 4)
        }
        error("missing tag ${IccSignature(signature)}")
    }

    private fun tagEntryOffset(bytes: ByteArray, signature: Int): Int {
        repeat(readU32(bytes, 128)) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == signature) return entry
        }
        error("missing tag ${IccSignature(signature)}")
    }

    private fun tagSize(bytes: ByteArray, signature: Int): Int =
        readU32(bytes, tagEntryOffset(bytes, signature) + 8)

    private fun tagSignatures(bytes: ByteArray): Set<Int> = buildSet {
        repeat(readU32(bytes, 128)) { index -> add(readU32(bytes, 132 + index * 12)) }
    }

    private fun signature(value: String): Int {
        require(value.length == 4)
        return value.fold(0) { result, character -> (result shl 8) or character.code }
    }

    private fun writeSharedParametricCurve(bytes: ByteArray, type: Int, parameters: FloatArray) {
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        writeU32(bytes, curveOffset, IccSignature.PARAMETRIC_CURVE_TYPE.value)
        writeU16(bytes, curveOffset + 8, type)
        writeU16(bytes, curveOffset + 10, 0)
        parameters.forEachIndexed { index, value -> writeS15Fixed16(bytes, curveOffset + 12 + index * 4, value) }
    }

    private fun rewriteSharedParametricCurve(type: Int, parameters: FloatArray): ByteArray {
        val bytes = resizeSharedCurveTag(12 + parameters.size * 4)
        writeSharedParametricCurve(bytes, type, parameters)
        return bytes
    }

    private fun rewriteSharedCurv(samples: IntArray): ByteArray {
        val bytes = resizeSharedCurveTag(12 + samples.size * 2)
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        writeU32(bytes, curveOffset, IccSignature.CURVE_TYPE.value)
        writeU32(bytes, curveOffset + 8, samples.size)
        samples.forEachIndexed { index, value -> writeU16(bytes, curveOffset + 12 + index * 2, value) }
        return bytes
    }

    private fun resizeSharedCurveTag(tagSize: Int): ByteArray {
        val original = resource("srgb-matrix-trc.icc")
        val curveOffset = tagOffset(original, IccSignature.R_TRC.value)
        val profileSize = curveOffset + (tagSize + 3 and -4)
        val bytes = original.copyOf(profileSize)
        writeU32(bytes, 0, profileSize)
        listOf(IccSignature.R_TRC, IccSignature.G_TRC, IccSignature.B_TRC).forEach { signature ->
            writeU32(bytes, tagEntryOffset(bytes, signature.value) + 8, tagSize)
        }
        for (offset in curveOffset until profileSize) bytes[offset] = 0
        return bytes
    }

    private fun setSharedCurveTagSize(bytes: ByteArray, tagSize: Int) {
        listOf(IccSignature.R_TRC, IccSignature.G_TRC, IccSignature.B_TRC).forEach { signature ->
            writeU32(bytes, tagEntryOffset(bytes, signature.value) + 8, tagSize)
        }
    }

    private fun appendTagSuffix(original: ByteArray, signature: Int): ByteArray {
        val entry = tagEntryOffset(original, signature)
        val offset = readU32(original, entry + 4)
        val size = readU32(original, entry + 8)
        val insertion = offset + size
        val bytes = ByteArray(original.size + 4)
        original.copyInto(bytes, endIndex = insertion)
        bytes[insertion] = 0x41
        bytes[insertion + 1] = 0x42
        bytes[insertion + 2] = 0x43
        bytes[insertion + 3] = 0x44
        original.copyInto(bytes, destinationOffset = insertion + 4, startIndex = insertion)
        writeU32(bytes, 0, bytes.size)
        repeat(readU32(bytes, 128)) { index ->
            val tagEntry = 132 + index * 12
            val tagOffset = readU32(bytes, tagEntry + 4)
            if (tagOffset == offset) writeU32(bytes, tagEntry + 8, size + 4)
            if (tagOffset >= insertion) writeU32(bytes, tagEntry + 4, tagOffset + 4)
        }
        return bytes
    }

    private fun assertFailure(code: String, bytes: ByteArray) {
        val failure = assertNotNull(
            IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull(),
            "expected typed failure $code",
        )
        assertEquals(code, failure.code)
    }

    private fun copyFourBytes(bytes: ByteArray, sourceOffset: Int, destinationOffset: Int) {
        repeat(4) { bytes[destinationOffset + it] = bytes[sourceOffset + it] }
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readS15Fixed16(bytes: ByteArray, offset: Int): Float = readU32(bytes, offset) / 65536f

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeS15Fixed16(bytes: ByteArray, offset: Int, value: Float) {
        writeU32(bytes, offset, (value * 65536f).toInt())
    }

    private fun assertTransferFunctionNear(expected: SkcmsTransferFunction, actual: SkcmsTransferFunction) {
        val differences = listOf(
            abs(expected.g - actual.g),
            abs(expected.a - actual.a),
            abs(expected.b - actual.b),
            abs(expected.c - actual.c),
            abs(expected.d - actual.d),
            abs(expected.e - actual.e),
            abs(expected.f - actual.f),
        )
        assertTrue(differences.all { it <= 7e-5f }, "transfer function differs: $actual")
    }

    private fun assertMatrixNear(expected: SkcmsMatrix3x3, actual: SkcmsMatrix3x3) {
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(expected[row, column], actual[row, column], 1f / 65536f, "matrix[$row,$column]")
        }
    }
}
