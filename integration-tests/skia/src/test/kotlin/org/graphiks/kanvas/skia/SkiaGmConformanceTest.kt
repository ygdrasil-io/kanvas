package org.graphiks.kanvas.skia

import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkiaGmConformanceTest {
    @Test
    fun `eligible has neither reason nor owner`() {
        val decision = GmConformanceDecision(GmConformanceScope.ELIGIBLE)

        assertEquals("eligible", decision.scope.wireName)
        assertNull(decision.reason)
        assertNull(decision.owner)
        assertTrue(decision.mustAttempt)
        assertThrows(IllegalArgumentException::class.java) {
            GmConformanceDecision(GmConformanceScope.ELIGIBLE, reason = "not allowed")
        }
    }

    @Test
    fun `excluded font and codec require a reason without owner`() {
        listOf(GmConformanceScope.EXCLUDED_FONT, GmConformanceScope.EXCLUDED_CODEC).forEach { scope ->
            assertThrows(IllegalArgumentException::class.java) { GmConformanceDecision(scope) }
            assertThrows(IllegalArgumentException::class.java) {
                GmConformanceDecision(scope, reason = "reason", owner = "owner")
            }
            assertFalse(GmConformanceDecision(scope, reason = "reason").mustAttempt)
        }
    }

    @Test
    fun `accepted Skia gap requires reason and owner`() {
        assertThrows(IllegalArgumentException::class.java) {
            GmConformanceDecision(GmConformanceScope.ACCEPTED_SKIA_GAP, reason = "gap")
        }
        val decision = GmConformanceDecision(
            GmConformanceScope.ACCEPTED_SKIA_GAP,
            reason = "gap",
            owner = "renderer",
        )
        assertTrue(decision.mustAttempt)
    }

    @Test
    fun `quarantined resource limit requires reason and owner`() {
        assertThrows(IllegalArgumentException::class.java) {
            GmConformanceDecision(GmConformanceScope.QUARANTINED_RESOURCE_LIMIT, reason = "reason")
        }
        val decision = GmConformanceDecision(
            GmConformanceScope.QUARANTINED_RESOURCE_LIMIT,
            reason = "legacy-snapshot-262144-draw-rects-not-practically-renderable",
            owner = "legacy-renderer-remediation",
        )
        assertFalse(decision.mustAttempt)
    }

    @Test
    fun `blocking cost remains eligible`() {
        assertEquals(
            GmConformanceScope.ELIGIBLE,
            SkiaGmConformance.decisionFor(ConformanceProbeGm(renderCost = RenderCost.BLOCKING)).scope,
        )
    }

    @Test
    fun `font output observed during draw is excluded before render submission`() {
        val surface = ConformanceSurface()

        val evidence = captureInventoryEvidence(FontOutputProbeGm()) { surface }

        assertEquals(GmConformanceScope.EXCLUDED_FONT, evidence.conformanceDecision.scope)
        assertEquals("direct-font-output", evidence.conformanceDecision.reason)
        assertFalse(evidence.attempted)
        assertEquals(InventorySetupState.SUCCEEDED, evidence.setupState)
        assertEquals(0, surface.renderCalls)
    }

    @Test
    fun `drawString observed before setup failure remains excluded font`() {
        assertObservedFontSetupFailure {
            it.drawString("font", 1f, 10f, portableFont(10f), Paint(color = ColorARGB.Black))
        }
    }

    @Test
    fun `drawGlyphs observed before setup failure remains excluded font`() {
        assertObservedFontSetupFailure {
            val font = portableFont(10f)
            it.drawGlyphs(listOf(1), listOf(org.graphiks.math.geometry.Point2F32(1f, 10f)), font, Paint(color = ColorARGB.Black))
        }
    }

    @Test
    fun `drawTextBlob observed before setup failure remains excluded font`() {
        assertObservedFontSetupFailure {
            val font = portableFont(10f)
            it.drawTextBlob(font.toTextBlob("font", 0f, 0f), 0f, 0f, Paint(color = ColorARGB.Black))
        }
    }

    @Test
    fun `text family is excluded before setup submits a render`() {
        val surface = ConformanceSurface()

        val evidence = captureInventoryEvidence(ConformanceProbeGm(renderFamily = RenderFamily.TEXT)) { surface }

        assertEquals(GmConformanceScope.EXCLUDED_FONT, evidence.conformanceDecision.scope)
        assertFalse(evidence.attempted)
        assertEquals(0, surface.renderCalls)
    }

    @Test
    fun `explicit codec entry is excluded without render submission`() {
        val surface = ConformanceSurface()

        val evidence = captureInventoryEvidence(ConformanceProbeGm(name = "encode")) { surface }

        assertEquals(GmConformanceScope.EXCLUDED_CODEC, evidence.conformanceDecision.scope)
        assertEquals("direct-codec-decode-or-encode", evidence.conformanceDecision.reason)
        assertFalse(evidence.attempted)
        assertEquals(InventorySetupState.NOT_ATTEMPTED, evidence.setupState)
        assertEquals(0, surface.renderCalls)
    }

    @Test
    fun `jpg color cube is statically quarantined before surface creation`() {
        var surfaceCreated = false

        val evidence = captureInventoryEvidence(ConformanceProbeGm(name = "jpg-color-cube")) {
            surfaceCreated = true
            ConformanceSurface()
        }

        assertEquals(GmConformanceScope.QUARANTINED_RESOURCE_LIMIT, evidence.conformanceDecision.scope)
        assertEquals("legacy-snapshot-262144-draw-rects-not-practically-renderable", evidence.conformanceDecision.reason)
        assertEquals("legacy-renderer-remediation", evidence.conformanceDecision.owner)
        assertFalse(evidence.attempted)
        assertEquals(InventorySetupState.NOT_ATTEMPTED, evidence.setupState)
        assertEquals("excluded:quarantined-resource-limit", evidence.route)
        assertFalse(surfaceCreated)
    }

    @Test
    fun `codec conformance population matches the registered Annex A GMs`() {
        val expected = setOf(
            "clip_shader_difference", "clip_shader_layer", "clip_shader_persp", "clip_shader",
            "destcolor", "ducky_yuv_blend", "encode", "hslcolorfilter", "HSL_duck",
            "imagefilter_composed_transform", "imagefilter_convolve_subset", "imagefilters_effect_order",
            "imagefilter_matrix_localmatrix", "patch_image", "patch_image_persp", "savelayer_initfromprev",
            "all_bitmap_configs", "AnimCodecPlayerExif_required.webp", "AnimCodecPlayerExif_required.gif",
            "AnimCodecPlayerExif_stoplight_h.webp", "animatedGif", "bitmap-image-srgb-legacy",
            "bitmap_subset_shader", "colorwheel_alphatypes", "colorwheel", "compositor_quads_image",
            "coordclampshader", "copyTo4444", "draw_bitmap_rect_skbug4734", "encode-alpha-jpeg",
            "encode-color-types-webp-lossless", "encode-platform", "encode-srgb-png", "filterindiabox",
            "grayscalejpg", "imagefilter_transformed_image", "imagemakewithfilter", "imageshader_tinyscale",
            "localmatriximageshader_filtering", "localmatrixshader_persp", "local_matrix_shader_rt",
            "localmatrix_order", "makecolorspace", "makeRasterImage", "persp_images", "readpixelscodec",
            "reinterpretcolorspace", "repeated_bitmap", "repeated_bitmap_jpg", "showmiplevels_explicit",
            "mesh_with_effects", "mesh_with_image", "mesh_with_paint_color", "mesh_with_paint_image",
        )

        val actual = SkiaGmRegistry.all().asSequence()
            .filter { SkiaGmConformance.decisionFor(it).scope == GmConformanceScope.EXCLUDED_CODEC }
            .map { it.name }
            .toSet()

        assertEquals(expected, actual)
    }
}

private fun assertObservedFontSetupFailure(emitFont: (GmCanvas) -> Unit) {
    val surface = ConformanceSurface()

    val evidence = captureInventoryEvidence(ObservedFontThenThrowProbeGm(emitFont)) { surface }

    assertEquals(GmConformanceScope.EXCLUDED_FONT, evidence.conformanceDecision.scope)
    assertFalse(evidence.attempted)
    assertEquals(InventorySetupState.FAILED, evidence.setupState)
    assertEquals(0, surface.renderCalls)
}

private open class ConformanceProbeGm(
    override val name: String = "conformance-probe",
    override val renderFamily: RenderFamily = RenderFamily.PATH,
    override val renderCost: RenderCost = RenderCost.FAST,
) : SkiaGm {
    override val minSimilarity: Double = 90.0
    override fun draw(canvas: GmCanvas, width: Int, height: Int) = Unit
}

private class FontOutputProbeGm : ConformanceProbeGm() {
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawString("font", 1f, 10f, portableFont(10f), Paint(color = ColorARGB.Black))
    }
}

private class ObservedFontThenThrowProbeGm(
    private val emitFont: (GmCanvas) -> Unit,
) : ConformanceProbeGm() {
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        emitFont(canvas)
        error("font-output-setup-failure")
    }
}

private class ConformanceSurface : InventorySurfaceCapture {
    private val delegate = org.graphiks.kanvas.surface.Surface(32, 32)
    var renderCalls = 0

    override fun canvas() = delegate.canvas()
    override fun snapshotOperationCount(): Int = delegate.snapshotOps().size
    override fun render(): org.graphiks.kanvas.surface.RenderResult {
        renderCalls += 1
        return delegate.render()
    }
}
