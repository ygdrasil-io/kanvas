package org.skia.gpu.webgpu

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.tests.GM

/**
 * GPU equivalent of [org.skia.dm.RasterSinkF16] — runs a [GM] through
 * an [SkWebGpuDevice] sized to the GM's preferred size, then packs the
 * raw RGBA readback into an [SkBitmap] suitable for
 * [org.skia.testing.TestUtils.compareBitmapsDetailed] against the
 * reference PNG in `original-888/`.
 *
 * **G6.1 colorspace handling.** Reference PNGs are encoded in
 * `DM unified Rec.2020` (Rec.2020 primaries + Rec.2020 transfer ;
 * see `TestUtils.DM_REFERENCE_COLOR_SPACE`). After G6.1 the GPU
 * pipeline applies the sRGB → linear → Rec.2020 → encoded transform
 * **inside the present pass** (cf. `shaders/present_pass.wgsl`), so
 * this sink only needs to repack bytes into an `SkBitmap`. The G6.0
 * CPU loop is gone.
 *
 * **Premul vs non-premul.** The GPU readback bytes are premultiplied
 * (consequence of the premul fragment output + SrcOver pipeline, see
 * G2.1). For GMs that only use opaque source colours (every cross-test
 * GM today), `premul == non-premul` byte-for-byte. GMs that paint
 * translucent sources will need a divide-by-alpha pre-step ; deferred
 * until a translucent-source GM enters the ratchet.
 */
public object WebGpuSink {
    public data class DrawWithM60F16FragmentLaneDiagnosticSnapshotResult(
        val bitmap: SkBitmap,
        val snapshot: SkWebGpuDevice.M60F16FragmentLaneDiagnosticSnapshot,
        val applicationPointSnapshot: SkWebGpuDevice.M60F16BoundedCorrectionApplicationPointSnapshot,
        val coverageStencilContributionMapSnapshot:
            SkWebGpuDevice.M60F16CoverageStencilContributionMapSnapshot,
        val aaStencilCoverPostPassRuntimeHookSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPostPassRuntimeHookSnapshot,
        val aaStencilCoverPostPassReadbackSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPostPassReadbackSnapshot,
        val aaStencilCoverPredrawDstReadbackSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverPredrawDstReadbackSnapshot,
        val aaStencilCoverContributionIsolationSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverContributionIsolationSnapshot,
        val aaStencilCoverShaderReturnDiagnosticSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverShaderReturnDiagnosticSnapshot,
        val aaStencilCoverIsolatedColorTargetSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverIsolatedColorTargetSnapshot,
        val aaStencilCoverStorageColorTargetComparisonSnapshot:
            SkWebGpuDevice.M60F16AaStencilCoverStorageColorTargetComparisonSnapshot,
    )

    /**
     * Render [gm] through an [SkWebGpuDevice] backed by [context], then
     * return the resulting bitmap in `kRGBA_8888`. The device's present
     * pass has already applied the sRGB → Rec.2020 colorspace transform
     * so the bytes are directly comparable to the reference PNGs in
     * `DM_REFERENCE_COLOR_SPACE`.
     */
    public fun draw(
        context: WebGpuContext,
        gm: GM,
        targetColorSpaceBlend: Boolean = false,
    ): SkBitmap {
        val size = gm.size()
        val w = size.width
        val h = size.height
        SkWebGpuDevice(
            context,
            w,
            h,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = targetColorSpaceBlend,
        ).use { device ->
            device.setBackground(gm.bgColor())
            val canvas = SkCanvas(device)
            gm.draw(canvas)
            val rgba = device.flush()
            return rgbaBytesToBitmap(rgba, w, h)
        }
    }

    public fun drawWithM60F16FragmentLaneDiagnosticSnapshot(
        context: WebGpuContext,
        gm: GM,
        targetColorSpaceBlend: Boolean = false,
    ): DrawWithM60F16FragmentLaneDiagnosticSnapshotResult {
        val size = gm.size()
        val w = size.width
        val h = size.height
        SkWebGpuDevice(
            context,
            w,
            h,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = targetColorSpaceBlend,
        ).use { device ->
            device.setBackground(gm.bgColor())
            val canvas = SkCanvas(device)
            gm.draw(canvas)
            val rgba = device.flush()
            return DrawWithM60F16FragmentLaneDiagnosticSnapshotResult(
                bitmap = rgbaBytesToBitmap(rgba, w, h),
                snapshot = device.m60F16FragmentLaneDiagnosticSnapshot(),
                applicationPointSnapshot = device.m60F16BoundedCorrectionApplicationPointSnapshot(),
                coverageStencilContributionMapSnapshot =
                    device.m60F16CoverageStencilContributionMapSnapshot(),
                aaStencilCoverPostPassRuntimeHookSnapshot =
                    device.m60F16AaStencilCoverPostPassRuntimeHookSnapshot(),
                aaStencilCoverPostPassReadbackSnapshot =
                    device.m60F16AaStencilCoverPostPassReadbackSnapshot(),
                aaStencilCoverPredrawDstReadbackSnapshot =
                    device.m60F16AaStencilCoverPredrawDstReadbackSnapshot(),
                aaStencilCoverContributionIsolationSnapshot =
                    device.m60F16AaStencilCoverContributionIsolationSnapshot(),
                aaStencilCoverShaderReturnDiagnosticSnapshot =
                    device.m60F16AaStencilCoverShaderReturnDiagnosticSnapshot(),
                aaStencilCoverIsolatedColorTargetSnapshot =
                    device.m60F16AaStencilCoverIsolatedColorTargetSnapshot(),
                aaStencilCoverStorageColorTargetComparisonSnapshot =
                    device.m60F16AaStencilCoverStorageColorTargetComparisonSnapshot(),
            )
        }
    }

    /**
     * Pack a row-major RGBA byte stream into an [SkBitmap.pixels8888]
     * ARGB int array. Layout convention :
     * - input bytes : `R, G, B, A` per pixel (after the GPU present
     *   pass, already in `DM_REFERENCE_COLOR_SPACE`)
     * - output ints : `0xAARRGGBB` per pixel
     */
    private fun rgbaBytesToBitmap(rgba: ByteArray, w: Int, h: Int): SkBitmap {
        require(rgba.size == w * h * 4) {
            "RGBA buffer size mismatch : expected ${w * h * 4} bytes for $w x $h, got ${rgba.size}"
        }
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until w * h) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }
}
