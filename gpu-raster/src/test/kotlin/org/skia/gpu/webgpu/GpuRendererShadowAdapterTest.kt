package org.skia.gpu.webgpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.foundation.SkPaint

class GpuRendererShadowAdapterTest {
    @Test
    fun `shadow fill rect property is evidence only and preserves product WebGPU pixels`() = withShadowFillRectProperty(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f)
            val clip = SkIRect.MakeLTRB(0, 0, 32, 32)
            val disabled = renderDeviceFillRect(ctx, rect, clip, shadowProperty = null)
            val shadowed = renderDeviceFillRect(ctx, rect, clip, shadowProperty = "true")

            assertArrayEquals(disabled.pixels, shadowed.pixels)

            val disabledResult = disabled.shadowResult
            assertNotNull(disabledResult)
            assertEquals(GpuRendererShadowHandoffStatus.Skipped, disabledResult!!.status)
            assertNull(disabledResult.normalizedCommand)

            val shadowResult = shadowed.shadowResult
            assertNotNull(shadowResult)
            val command = assertInstanceOf(
                NormalizedDrawCommand.FillRect::class.java,
                shadowResult!!.normalizedCommand,
            )

            assertEquals(GpuRendererShadowHandoffStatus.Native, shadowResult.status)
            assertEquals("legacy.fillRect.shadow", command.source.operation)
            val dump = shadowResult.dump()
            assertTrue(dump.contains("cpuFallback=false"))
            assertFalse(dump.contains("GPUCommandSubmission.Submitted"))
            assertFalse(dump.contains("execution.submission:submitted"))
            assertFalse(dump.contains("GPUReadbackResult.Completed"))
            assertFalse(dump.contains("diagnostic-webgpu-first-route-pm-evidence"))
        }
    }

    @Test
    fun `legacy device draw rect hook follows system property gate`() = withShadowFillRectProperty(null) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, 32, 32).use { device ->
                val rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f)
                val clip = SkIRect.MakeLTRB(0, 0, 32, 32)

                device.drawRect(rect, clip, SkPaint(SK_ColorMAGENTA))
                val disabled = device.gpuRendererShadowResultForTests()
                assertNotNull(disabled)
                assertEquals(GpuRendererShadowHandoffStatus.Skipped, disabled!!.status)
                assertNull(disabled.normalizedCommand)

                withShadowFillRectProperty("true") {
                    val strokeAndFillPaint = SkPaint(SK_ColorMAGENTA).apply {
                        style = SkPaint.Style.kStrokeAndFill_Style
                    }

                    device.drawRect(rect, clip, strokeAndFillPaint)

                    val shadow = device.gpuRendererShadowResultForTests()
                    assertNotNull(shadow)
                    val command = assertInstanceOf(
                        NormalizedDrawCommand.FillRect::class.java,
                        shadow!!.normalizedCommand,
                    )

                    assertEquals(GpuRendererShadowHandoffStatus.Native, shadow.status)
                    assertEquals("native.fill_rect.solid", shadow.routeLabel)
                    assertEquals("legacy.fillRect.shadow", command.source.operation)
                    assertEquals(SkPaint.Style.kStrokeAndFill_Style, strokeAndFillPaint.style)
                    assertTrue(shadow.dump().contains("cpuFallback=false"))
                }
            }
        }
    }

    @Test
    fun `legacy device product flag property records candidate and preserves product WebGPU pixels`() =
        withGpuRendererFillRectProperties(shadow = null, product = null) {
            val context = WebGpuContext.createOrNull()
            Assumptions.assumeTrue(context != null, "No WebGPU adapter")

            context!!.use { ctx ->
                val rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f)
                val clip = SkIRect.MakeLTRB(0, 0, 32, 32)
                val disabled = renderDeviceFillRect(ctx, rect, clip, shadowProperty = null, productProperty = null)
                val productFlagged = renderDeviceFillRect(ctx, rect, clip, shadowProperty = null, productProperty = "true")

                assertArrayEquals(disabled.pixels, productFlagged.pixels)

                val disabledResult = disabled.shadowResult
                assertNotNull(disabledResult)
                assertEquals(GpuRendererShadowHandoffStatus.Skipped, disabledResult!!.status)
                assertFalse(disabledResult.productFlag.enabled)

                val productResult = productFlagged.shadowResult
                assertNotNull(productResult)
                val command = assertInstanceOf(
                    NormalizedDrawCommand.FillRect::class.java,
                    productResult!!.normalizedCommand,
                )

                assertEquals(GpuRendererShadowHandoffStatus.ProductFlagged, productResult.status)
                assertEquals("product_flag.native.fill_rect.solid", productResult.routeLabel)
                assertEquals("legacy.fillRect.product_flag", command.source.operation)
                assertTrue(productResult.productFlag.enabled)
                assertTrue(productResult.legacyRouteAvailable)
                assertTrue(productResult.dump().contains("cpuFallback=false"))
                assertFalse(productResult.dump().contains("GPUCommandSubmission.Submitted"))
                assertFalse(productResult.dump().contains("GPUReadbackResult.Completed"))
            }
        }

    @Test
    fun `legacy fill rect hook is disabled by default and treats stroke and fill as fill evidence`() {
        val disabled = shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(),
            commandId = 0,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = SkPaint(SK_ColorMAGENTA),
            targetWidth = 32,
            targetHeight = 32,
        )
        val strokeAndFillPaint = SkPaint(SK_ColorMAGENTA).apply {
            style = SkPaint.Style.kStrokeAndFill_Style
        }
        val shadow = shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
            commandId = 9,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = strokeAndFillPaint,
            targetWidth = 32,
            targetHeight = 32,
        )

        assertEquals(GpuRendererShadowHandoffStatus.Skipped, disabled.status)
        assertNull(disabled.normalizedCommand)
        assertEquals(GpuRendererShadowHandoffStatus.Native, shadow.status)
        assertEquals("native.fill_rect.solid", shadow.routeLabel)
        assertEquals(SkPaint.Style.kStrokeAndFill_Style, strokeAndFillPaint.style)
        assertTrue(shadow.dump().contains("cpuFallback=false"))
    }

    @Test
    fun `first route product flag is explicit and disabled by default`() {
        fun configFor(vararg pairs: Pair<String, String?>): GpuRendererShadowConfig {
            val values = pairs.toMap()
            return GpuRendererShadowConfig.fromSystemProperties { key -> values[key] }
        }

        val disabled = configFor()
        val shadow = configFor(GpuRendererShadowConfig.ShadowFillRectProperty to "true")
        val productFlag = configFor(GpuRendererShadowConfig.ProductFillRectProperty to "true")
        val productFlagWins = configFor(
            GpuRendererShadowConfig.ShadowFillRectProperty to "true",
            GpuRendererShadowConfig.ProductFillRectProperty to "true",
        )

        assertEquals(GpuRendererShadowMode.Disabled, disabled.mode)
        assertEquals(GpuRendererShadowMode.Shadow, shadow.mode)
        assertEquals(GpuRendererShadowMode.ProductFlag, productFlag.mode)
        assertEquals(GpuRendererShadowMode.ProductFlag, productFlagWins.mode)
        assertFalse(disabled.productFlag.enabled)
        assertFalse(shadow.productFlag.enabled)
        assertTrue(productFlag.productFlag.enabled)
        assertEquals("solid-fill-rect", productFlag.productFlag.routeScope)
    }

    @Test
    fun `first route product flag state must match explicit mode`() {
        assertThrows(IllegalArgumentException::class.java) {
            GpuRendererShadowConfig(
                mode = GpuRendererShadowMode.ProductFlag,
                productFlag = GpuRendererFirstRouteFlagState.Disabled,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GpuRendererShadowConfig(
                mode = GpuRendererShadowMode.Shadow,
                productFlag = GpuRendererFirstRouteFlagState.SolidFillRect,
            )
        }
    }

    @Test
    fun `product flag mode records controlled route candidate while keeping legacy route visible`() {
        val result = shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.ProductFlag),
            commandId = 11,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = SkPaint(SK_ColorMAGENTA),
            targetWidth = 32,
            targetHeight = 32,
        )

        val command = assertInstanceOf(NormalizedDrawCommand.FillRect::class.java, result.normalizedCommand)
        val dump = result.dump()

        assertEquals(GpuRendererShadowHandoffStatus.ProductFlagged, result.status)
        assertEquals("product_flag.native.fill_rect.solid", result.routeLabel)
        assertEquals("legacy.fillRect.product_flag", command.source.operation)
        assertEquals(GpuRendererShadowMode.ProductFlag, result.mode)
        assertTrue(result.productFlag.enabled)
        assertTrue(result.legacyRouteAvailable)
        assertTrue(dump.contains("mode=product-flag"))
        assertTrue(dump.contains("productFlag=true:solid-fill-rect"))
        assertTrue(dump.contains("legacyRouteAvailable=true"))
        assertTrue(dump.contains("cpuFallback=false"))
        assertFalse(dump.contains("GPUCommandSubmission.Submitted"))
        assertFalse(dump.contains("execution.submission:submitted"))
        assertFalse(dump.contains("GPUReadbackResult.Completed"))
        assertFalse(dump.contains("diagnostic-webgpu-first-route-pm-evidence"))
    }

    @Test
    fun `product flag mode does not expand stroke and fill into product flagged route`() {
        val strokeAndFillPaint = SkPaint(SK_ColorMAGENTA).apply {
            style = SkPaint.Style.kStrokeAndFill_Style
        }

        val result = shadowFillRectForLegacyPath(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.ProductFlag),
            commandId = 12,
            rect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
            clip = SkIRect.MakeLTRB(0, 0, 32, 32),
            paint = strokeAndFillPaint,
            targetWidth = 32,
            targetHeight = 32,
        )

        val dump = result.dump()

        assertEquals(GpuRendererShadowHandoffStatus.Refused, result.status)
        assertEquals("unsupported.adapter.paint_style", result.diagnosticCode)
        assertNull(result.normalizedCommand)
        assertTrue(result.productFlag.enabled)
        assertTrue(result.legacyRouteAvailable)
        assertTrue(dump.contains("mode=product-flag"))
        assertTrue(dump.contains("productFlag=true:solid-fill-rect"))
        assertTrue(dump.contains("legacyRouteAvailable=true"))
        assertTrue(dump.contains("cpuFallback=false"))
        assertFalse(dump.contains("status=product-flagged"))
    }

    @Test
    fun `default mode skips shadow handoff and captures no renderer command`() {
        val result = GpuRendererShadowAdapter().shadowFillRect(firstFillRectState())

        assertEquals(GpuRendererShadowHandoffStatus.Skipped, result.status)
        assertEquals("skipped.gpu_renderer_shadow.disabled", result.routeLabel)
        assertEquals("shadow.disabled", result.diagnosticCode)
        assertNull(result.normalizedCommand)
        assertNull(result.firstRouteDecision)
        assertNull(result.commandFacts)
        assertTrue(result.dump().contains("status=skipped"))
        assertTrue(result.dump().contains("route=skipped.gpu_renderer_shadow.disabled"))
    }

    @Test
    fun `shadow mode captures legacy fill rect as renderer normalized command and native planner evidence`() {
        val result = GpuRendererShadowAdapter(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
        ).shadowFillRect(
            firstFillRectState(
                transform = GpuRendererShadowTransform.Translate(dx = 3f, dy = 4f),
                clip = GpuRendererShadowClip.DeviceRect(SkRect.MakeLTRB(2f, 3f, 18f, 19f)),
            ),
        )

        val command = assertInstanceOf(NormalizedDrawCommand.FillRect::class.java, result.normalizedCommand)
        val material = assertInstanceOf(GPUMaterialDescriptor.SolidColor::class.java, command.material)
        val route = assertInstanceOf(GPURouteDecision.Native::class.java, result.firstRouteDecision)
        assertNotNull(result.commandFacts)
        val facts = result.commandFacts!!

        assertEquals(GpuRendererShadowHandoffStatus.Native, result.status)
        assertEquals("native.fill_rect.solid", result.routeLabel)
        assertNull(result.diagnosticCode)
        assertEquals("GpuRendererShadowAdapter", command.source.adapter)
        assertEquals("legacy.fillRect.shadow", command.source.operation)
        assertEquals(GPUTransformType.Translate, command.transform.type)
        assertEquals(3f, command.transform.translateX)
        assertEquals(4f, command.transform.translateY)
        assertEquals(GPUClipKind.DeviceRect, command.clip.kind)
        assertEquals("route.fill_rect.7", route.route.routeId)
        assertEquals("FillRect", facts.drawKind)
        assertEquals("Translate", facts.transformClass)
        assertEquals("DeviceRect", facts.clipKind)
        assertEquals("SolidColor", facts.materialKind)
        assertEquals(1f, material.r)
        assertEquals(0f, material.g)
        assertEquals(1f, material.b)
        assertEquals(1f, material.a)
        assertTrue(result.dump().contains("status=native"))
        assertTrue(result.dump().contains("route=native.fill_rect.solid"))
        assertTrue(result.dump().contains("rect=1.0,2.0,11.0,12.0"))
        assertTrue(result.dump().contains("clip=DeviceRect:2.0,3.0,18.0,19.0"))
    }

    @Test
    fun `shadow mode preserves renderer complex clip refusal without backend evidence`() {
        val result = GpuRendererShadowAdapter(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
        ).shadowFillRect(
            firstFillRectState(
                clip = GpuRendererShadowClip.ComplexStack(),
            ),
        )

        val command = assertInstanceOf(NormalizedDrawCommand.FillRect::class.java, result.normalizedCommand)
        val route = assertInstanceOf(GPURouteDecision.Refused::class.java, result.firstRouteDecision)

        assertEquals(GpuRendererShadowHandoffStatus.Refused, result.status)
        assertEquals("refused.unsupported.clip.complex_stack", result.routeLabel)
        assertEquals("unsupported.clip.complex_stack", result.diagnosticCode)
        assertEquals(GPUClipKind.ComplexStack, command.clip.kind)
        assertNotNull(result.commandFacts)
        assertEquals("unsupported.clip.complex_stack", route.diagnostic.code)

        val dump = result.dump()
        assertTrue(dump.contains("cpuFallback=false"))
        assertFalse(dump.contains("GPUCommandSubmission.Submitted"))
        assertFalse(dump.contains("execution.submission:submitted"))
        assertFalse(dump.contains("GPUReadbackResult.Completed"))
        assertFalse(dump.contains("diagnostic-webgpu-first-route-pm-evidence"))
    }

    @Test
    fun `shadow mode refuses unsupported legacy paint instead of falling back to cpu`() {
        val strokePaint = SkPaint(SK_ColorMAGENTA).apply {
            style = SkPaint.Style.kStroke_Style
        }

        val result = GpuRendererShadowAdapter(
            config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
        ).shadowFillRect(firstFillRectState(paint = strokePaint))

        assertEquals(GpuRendererShadowHandoffStatus.Refused, result.status)
        assertEquals("refused.unsupported.adapter.paint_style", result.routeLabel)
        assertEquals("unsupported.adapter.paint_style", result.diagnosticCode)
        assertNull(result.normalizedCommand)
        assertNull(result.firstRouteDecision)
        assertNull(result.commandFacts)
        assertTrue(result.dump().contains("status=refused"))
        assertTrue(result.dump().contains("diagnostic=unsupported.adapter.paint_style"))
        assertTrue(result.dump().contains("cpuFallback=false"))
    }

    @Test
    fun `shadow mode refuses invalid adapter facts before renderer normalization`() {
        val nonFinitePaint = SkPaint().apply {
            color4f = SkColor4f(Float.NaN, 0f, 1f, 1f)
        }
        val cases = listOf(
            "unsupported.adapter.rect_empty" to firstFillRectState(
                rect = SkRect.MakeLTRB(4f, 2f, 4f, 12f),
            ),
            "unsupported.adapter.clip_empty" to firstFillRectState(
                clip = GpuRendererShadowClip.DeviceRect(SkRect.MakeLTRB(6f, 8f, 5f, 9f)),
            ),
            "unsupported.adapter.paint_order" to firstFillRectState(
                paintOrder = -1,
            ),
            "unsupported.solid.non_finite" to firstFillRectState(
                paint = nonFinitePaint,
            ),
        )

        for ((expectedCode, state) in cases) {
            val result = GpuRendererShadowAdapter(
                config = GpuRendererShadowConfig(mode = GpuRendererShadowMode.Shadow),
            ).shadowFillRect(state)

            assertEquals(GpuRendererShadowHandoffStatus.Refused, result.status)
            assertEquals("refused.$expectedCode", result.routeLabel)
            assertEquals(expectedCode, result.diagnosticCode)
            assertNull(result.normalizedCommand)
            assertNull(result.firstRouteDecision)
            assertTrue(result.dump().contains("cpuFallback=false"))
        }
    }

    private fun firstFillRectState(
        rect: SkRect = SkRect.MakeLTRB(1f, 2f, 11f, 12f),
        paint: SkPaint = SkPaint(SK_ColorMAGENTA),
        transform: GpuRendererShadowTransform = GpuRendererShadowTransform.Identity,
        clip: GpuRendererShadowClip = GpuRendererShadowClip.WideOpen,
        paintOrder: Int = 3,
    ): GpuRendererShadowFillRectState =
        GpuRendererShadowFillRectState(
            commandId = 7,
            rect = rect,
            paint = paint,
            targetWidth = 32,
            targetHeight = 32,
            targetColorFormat = "rgba8unorm",
            transform = transform,
            clip = clip,
            paintOrder = paintOrder,
        )

    private fun renderDeviceFillRect(
        context: WebGpuContext,
        rect: SkRect,
        clip: SkIRect,
        shadowProperty: String?,
        productProperty: String? = null,
    ): DeviceFillRectPixels =
        withGpuRendererFillRectProperties(shadow = shadowProperty, product = productProperty) {
            SkWebGpuDevice(context, 32, 32).use { device ->
                device.drawRect(rect, clip, SkPaint(SK_ColorMAGENTA))
                val shadowResult = device.gpuRendererShadowResultForTests()
                DeviceFillRectPixels(
                    pixels = device.flush(),
                    shadowResult = shadowResult,
                )
            }
        }

    private data class DeviceFillRectPixels(
        val pixels: ByteArray,
        val shadowResult: GpuRendererShadowResult?,
    )

    private fun <T> withShadowFillRectProperty(value: String?, block: () -> T): T {
        return withGpuRendererFillRectProperties(shadow = value, product = null, block = block)
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
