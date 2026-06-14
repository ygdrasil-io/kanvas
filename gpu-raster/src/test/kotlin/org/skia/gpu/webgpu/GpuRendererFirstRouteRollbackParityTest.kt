package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint
import java.security.MessageDigest

class GpuRendererFirstRouteRollbackParityTest {
    @Test
    fun `rollback parity report compares legacy product flag and restored legacy output`() =
        withGpuRendererFillRectProperties(shadow = null, product = null) {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")

            context!!.use { ctx ->
                val rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f)
                val clip = SkIRect.MakeLTRB(0, 0, 32, 32)
                val legacyBefore = renderDeviceFillRect(ctx, rect, clip, productProperty = null)
                val productFlagged = renderDeviceFillRect(ctx, rect, clip, productProperty = "true")
                val legacyRollback = renderDeviceFillRect(ctx, rect, clip, productProperty = null)
                val unsupported = productFlagUnsupportedStrokeAndFill()

                val report = GpuRendererFirstRouteRollbackParityValidator.validate(
                    legacyBefore = rollbackSnapshot(
                        label = "legacy-before",
                        result = legacyBefore.shadowResult,
                        pixelChecksum = legacyBefore.pixels.sha256(),
                    ),
                    productFlagged = rollbackSnapshot(
                        label = "product-flagged",
                        result = productFlagged.shadowResult,
                        pixelChecksum = productFlagged.pixels.sha256(),
                    ),
                    legacyRollback = rollbackSnapshot(
                        label = "legacy-rollback",
                        result = legacyRollback.shadowResult,
                        pixelChecksum = legacyRollback.pixels.sha256(),
                    ),
                    unsupportedVariant = unsupported,
                )

                assertTrue(report.gatePassed)
                assertTrue(report.parityMatched)
                assertTrue(report.rollbackRestoredLegacy)
                assertFalse(report.productRouteActivated)
                assertFalse(report.releaseBlocking)
                assertEquals(0.0, report.readinessDelta)
                assertEquals("solid-fill-rect", report.routeScope)
                assertTrue(report.transcript.contains("legacy-before:status=skipped"))
                assertTrue(report.transcript.contains("product-flagged:status=product-flagged"))
                assertTrue(report.transcript.contains("legacy-rollback:status=skipped"))
                assertTrue(report.transcript.contains("unsupported-variant:status=refused"))
                assertTrue(report.transcript.contains("diagnostic=unsupported.adapter.paint_style"))
                assertFalse(report.transcript.contains("GPUCommandSubmission.Submitted"))
                assertFalse(report.transcript.contains("GPUReadbackResult.Completed"))
            }
        }

    @Test
    fun `rollback parity report fails visibly on checksum mismatch without activation`() {
        val legacyBefore = rollbackSnapshot(
            label = "legacy-before",
            result = skippedResult(),
            pixelChecksum = "sha256:before",
        )
        val productFlagged = rollbackSnapshot(
            label = "product-flagged",
            result = productFlaggedResult(),
            pixelChecksum = "sha256:different",
        )
        val legacyRollback = rollbackSnapshot(
            label = "legacy-rollback",
            result = skippedResult(),
            pixelChecksum = "sha256:before",
        )

        val report = GpuRendererFirstRouteRollbackParityValidator.validate(
            legacyBefore = legacyBefore,
            productFlagged = productFlagged,
            legacyRollback = legacyRollback,
            unsupportedVariant = unsupportedVariantSnapshot(),
        )

        assertFalse(report.gatePassed)
        assertFalse(report.parityMatched)
        assertTrue(report.rollbackRestoredLegacy)
        assertFalse(report.productRouteActivated)
        assertFalse(report.releaseBlocking)
        assertEquals(0.0, report.readinessDelta)
        assertTrue(report.diagnostics.contains("rollback.parity.checksum_mismatch"))
        assertTrue(report.transcript.contains("gatePassed=false"))
    }

    private data class DeviceFillRectRun(
        val pixels: ByteArray,
        val shadowResult: GpuRendererShadowResult,
    )

    private fun renderDeviceFillRect(
        context: WebGpuContext,
        rect: SkRect,
        clip: SkIRect,
        productProperty: String?,
    ): DeviceFillRectRun =
        withGpuRendererFillRectProperties(shadow = null, product = productProperty) {
            SkWebGpuDevice(context, 32, 32).use { device ->
                device.drawRect(rect, clip, SkPaint(SK_ColorMAGENTA))
                val result = device.gpuRendererShadowResultForTests()
                assertNotNull(result)
                DeviceFillRectRun(
                    pixels = device.flush(),
                    shadowResult = result!!,
                )
            }
        }

    private fun productFlagUnsupportedStrokeAndFill(): GpuRendererFirstRouteRollbackSnapshot {
        val paint = SkPaint(SK_ColorMAGENTA).apply {
            style = SkPaint.Style.kStrokeAndFill_Style
        }
        val result = shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.ProductFlag),
            commandId = 21,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = paint,
            targetWidth = 32,
            targetHeight = 32,
        )
        return rollbackSnapshot(
            label = "unsupported-variant",
            result = result,
            pixelChecksum = "not-rendered",
        )
    }

    private fun unsupportedVariantSnapshot(): GpuRendererFirstRouteRollbackSnapshot =
        rollbackSnapshot(
            label = "unsupported-variant",
            result = productFlagUnsupportedStrokeAndFill().result,
            pixelChecksum = "not-rendered",
        )

    private fun skippedResult(): GpuRendererShadowResult =
        shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(),
            commandId = 0,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = SkPaint(SK_ColorMAGENTA),
            targetWidth = 32,
            targetHeight = 32,
        )

    private fun productFlaggedResult(): GpuRendererShadowResult =
        shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.ProductFlag),
            commandId = 20,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = SkPaint(SK_ColorMAGENTA),
            targetWidth = 32,
            targetHeight = 32,
        )

    private fun rollbackSnapshot(
        label: String,
        result: GpuRendererShadowResult,
        pixelChecksum: String,
    ): GpuRendererFirstRouteRollbackSnapshot =
        GpuRendererFirstRouteRollbackSnapshot(
            label = label,
            result = result,
            pixelChecksum = pixelChecksum,
        )

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString(prefix = "sha256:", separator = "") { byte -> "%02x".format(byte) }
    }

    private fun <T> withGpuRendererFillRectProperties(
        shadow: String?,
        product: String?,
        block: () -> T,
    ): T {
        val previousShadow = System.getProperty(GpuRendererShadowConfig.ShadowFillRectProperty)
        val previousProduct = System.getProperty(GpuRendererShadowConfig.ProductFillRectProperty)
        setOrClearProperty(GpuRendererShadowConfig.ShadowFillRectProperty, shadow)
        setOrClearProperty(GpuRendererShadowConfig.ProductFillRectProperty, product)
        return try {
            block()
        } finally {
            setOrClearProperty(GpuRendererShadowConfig.ShadowFillRectProperty, previousShadow)
            setOrClearProperty(GpuRendererShadowConfig.ProductFillRectProperty, previousProduct)
        }
    }

    private fun setOrClearProperty(key: String, value: String?) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }
}
