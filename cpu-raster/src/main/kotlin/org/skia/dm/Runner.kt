package org.skia.dm

import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.tests.GM
import java.security.MessageDigest

/**
 * DM runner — drives a list of GMs through a list of sinks and
 * returns a [Report] aggregating per-(GM, sink) results.
 *
 * Mirrors upstream's
 * [`dm/DM.cpp`](https://github.com/google/skia/blob/main/dm/DM.cpp)
 * top-level loop : for each `Src` (GM) × each `Sink`, render and
 * record. The output JSON is line-compatible with
 * [`DMJsonWriter::DumpJson`](https://github.com/google/skia/blob/main/dm/DMJsonWriter.cpp)
 * — see [Report.toJson] for the shape.
 *
 * **Threading** : the runner is single-threaded by design. The
 * upstream harness multiplexes across cores via `SkTaskGroup` ;
 * D4.3 runs sequentially because most consumer call sites are
 * already JUnit threads, and the order-determinism makes
 * report diffing easier. A future slice can swap the inner loop
 * for `parallelStream` if a use case shows up.
 *
 * **Parametrising the run** :
 *  - [properties] are top-level metadata (e.g. build flavour).
 *    Mirrors upstream's `--key` flag (the leading set of
 *    `key,value` pairs that go into the `"key"` block).
 *  - [key] is the run-key — distinguishes replicas of the same
 *    matrix entry by host / OS / compiler. Same upstream `--key`
 *    flag in the `dm.json` `"key"` object.
 *
 * Usage :
 * ```kotlin
 * val report = Runner(
 *     sinks = listOf(RasterSink8888(), RasterSinkF16()),
 *     gms = listOf(BigRectGM(), SimpleRectGM(), …),
 *     properties = mapOf("build_flavor" to "release"),
 *     key = mapOf("os" to "Mac", "compiler" to "Clang"),
 * ).run()
 * println(report.summary())
 * File("dm.json").writeText(report.toJson())
 * ```
 */
public class Runner(
    private val sinks: List<Sink>,
    private val gms: List<GM>,
    private val properties: Map<String, String> = emptyMap(),
    private val key: Map<String, String> = emptyMap(),
) {

    /** Drive every (GM × sink) combination ; build the [Report]. */
    public fun run(): Report {
        val passed = mutableListOf<RunRecord>()
        val failed = mutableListOf<RunRecord>()
        for (gm in gms) {
            for (sink in sinks) {
                val record = runOne(gm, sink)
                if (record.passed) passed += record else failed += record
            }
        }
        return Report(
            passed = passed,
            failed = failed,
            properties = properties,
            key = key,
        )
    }

    private fun runOne(gm: GM, sink: Sink): RunRecord {
        return when (val r = sink.draw(gm)) {
            is Sink.Result.Ok -> buildPassRecord(gm, sink, r.bitmap)
            is Sink.Result.Bytes -> buildBytesRecord(gm, sink, r.bytes)
            is Sink.Result.Error -> buildFailRecord(gm, sink, r.message)
        }
    }

    private fun buildPassRecord(gm: GM, sink: Sink, bitmap: SkBitmap): RunRecord {
        // Encode the rendered bitmap to PNG and hash the bytes —
        // mirrors upstream's "dm hashes the encoded output, not the
        // raw pixmap" convention so two sinks producing the same
        // visible image produce the same md5.
        val pngBytes = PngEncoder.encode(bitmap) ?: ByteArray(0)
        val md5 = md5Hex(pngBytes)
        val classification = classifyBitmap(bitmap)
        return RunRecord(
            gmName = gm.name(),
            sinkTag = sink.tag,
            md5 = md5,
            extension = sink.fileExtension,
            colorType = classification.colorType,
            alphaType = classification.alphaType,
            gamut = classification.gamut,
            transferFn = classification.transferFn,
            colorDepth = classification.colorDepth,
        )
    }

    /**
     * Build a record from a vector-output sink (B2.5 [SvgSink]).
     * The MD5 is over the raw bytes — no PNG re-encode because the
     * payload is already the canonical encoded form. The bitmap-side
     * classification fields stay empty, since vector formats don't
     * have raster colour-type / gamut / depth.
     */
    private fun buildBytesRecord(gm: GM, sink: Sink, bytes: ByteArray): RunRecord =
        RunRecord(
            gmName = gm.name(),
            sinkTag = sink.tag,
            md5 = md5Hex(bytes),
            extension = sink.fileExtension,
            colorType = "",
            alphaType = "",
            gamut = "",
            transferFn = "",
            colorDepth = "",
        )

    private fun buildFailRecord(gm: GM, sink: Sink, message: String): RunRecord =
        RunRecord(
            gmName = gm.name(),
            sinkTag = sink.tag,
            md5 = "",
            extension = "",
            colorType = "",
            alphaType = "",
            gamut = "",
            transferFn = "",
            colorDepth = "",
            errorMessage = message,
        )

    /**
     * Stringify a bitmap's colour metadata using the same conventions
     * upstream Skia DM emits. Unknown gamuts / TFs degrade to
     * `"custom"` rather than throwing — the report stays diff-able
     * even when a GM uses an exotic working space.
     */
    private data class Classification(
        val colorType: String,
        val alphaType: String,
        val gamut: String,
        val transferFn: String,
        val colorDepth: String,
    )

    private fun classifyBitmap(bitmap: SkBitmap): Classification {
        val ct = when (bitmap.colorType) {
            SkColorType.kRGBA_8888 -> "rgba_8888"
            SkColorType.kRGBA_F16Norm -> "rgba_f16"
            SkColorType.kARGB_4444 -> "argb_4444"
            else -> bitmap.colorType.name.lowercase()
        }
        val depth = when (bitmap.colorType) {
            SkColorType.kRGBA_8888, SkColorType.kARGB_4444 -> "8888"
            SkColorType.kRGBA_F16Norm -> "f16"
            else -> "unknown"
        }
        // Default alpha-type derivation : 8888 stores unpremul, F16
        // stores premul, ARGB_4444 stores premul. Matches the
        // SkBitmap constructor's per-colour-type defaults.
        val at = when (bitmap.colorType) {
            SkColorType.kRGBA_8888 -> SkAlphaType.kUnpremul.toReportString()
            SkColorType.kRGBA_F16Norm -> SkAlphaType.kPremul.toReportString()
            SkColorType.kARGB_4444 -> SkAlphaType.kPremul.toReportString()
            else -> "unknown"
        }
        val gamut = describeGamut(bitmap.colorSpace)
        val transferFn = describeTransferFn(bitmap.colorSpace)
        return Classification(ct, at, gamut, transferFn, depth)
    }

    private fun SkAlphaType.toReportString(): String = when (this) {
        SkAlphaType.kPremul -> "premul"
        SkAlphaType.kUnpremul -> "unpremul"
        SkAlphaType.kOpaque -> "opaque"
        SkAlphaType.kUnknown -> "unknown"
    }

    /**
     * Map a [SkColorSpace] to upstream's gamut labels. We do an
     * identity check on the gamut matrix against the named primaries
     * Skia ships ; an unrecognised matrix becomes `"custom"`.
     */
    private fun describeGamut(cs: SkColorSpace): String = when {
        cs.isSRGB() -> "srgb"
        matrixEquals(cs.toXYZD50, SkNamedGamut.kSRGB) -> "srgb"
        matrixEquals(cs.toXYZD50, SkNamedGamut.kDisplayP3) -> "display_p3"
        matrixEquals(cs.toXYZD50, SkNamedGamut.kAdobeRGB) -> "adobe_rgb"
        matrixEquals(cs.toXYZD50, SkNamedGamut.kRec2020) -> "rec2020"
        matrixEquals(cs.toXYZD50, SkNamedGamut.kXYZ) -> "xyz"
        else -> "custom"
    }

    private fun describeTransferFn(cs: SkColorSpace): String = when (cs.transferFn) {
        SkNamedTransferFn.kSRGB -> "srgb"
        SkNamedTransferFn.kLinear -> "linear"
        SkNamedTransferFn.k2Dot2 -> "2dot2"
        SkNamedTransferFn.kRec2020 -> "rec2020"
        else -> "custom"
    }

    private fun matrixEquals(
        a: org.graphiks.math.SkcmsMatrix3x3,
        b: org.graphiks.math.SkcmsMatrix3x3,
    ): Boolean {
        for (r in 0 until 3) for (c in 0 until 3) {
            if (a.vals[r][c] != b.vals[r][c]) return false
        }
        return true
    }

    private companion object {
        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        fun md5Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("MD5").digest(bytes)
            val sb = StringBuilder(md.size * 2)
            for (b in md) {
                val v = b.toInt() and 0xFF
                sb.append(HEX_CHARS[v ushr 4])
                sb.append(HEX_CHARS[v and 0x0F])
            }
            return sb.toString()
        }
    }
}
