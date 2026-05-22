package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Aggregated `@Disabled` tests for the O2 batch (D-F) GMs that have
 * placeholder bodies — they exist as registered classes so the
 * cross-backend ratchet picks them up once the underlying API gap
 * is closed. Each test documents the blocker.
 *
 * Surfacing them all under one file keeps the file count under
 * control while still letting CI's test report show what's parked.
 */
class PlaceholderGMsTest {

    @Test @Disabled("GPU-only : distance-field text path not yet ported.")
    fun `DFTextGM placeholder`() { DFTextGM() }

    @Test @Disabled("GPU-only : distance-field text + perspective text-blob.")
    fun `DFTextBlobPerspGM placeholder`() { DFTextBlobPerspGM() }

    @Test @Disabled("SkCanvas.drawImageSet / ImageSetEntry API not yet exposed.")
    fun `DrawImageSetGM placeholder`() { DrawImageSetGM() }

    @Test @Disabled("SkCanvas.drawImageSet / ImageSetEntry API not yet exposed.")
    fun `DrawImageSetAlphaOnlyGM placeholder`() { DrawImageSetAlphaOnlyGM() }

    @Test @Disabled("SkCanvas.drawImageSet / ImageSetEntry API not yet exposed.")
    fun `DrawImageSetRectToRectGM placeholder`() { DrawImageSetRectToRectGM() }

    @Test @Disabled("GPU-only : texture-orientation / makeOrientedImage helpers not yet ported.")
    fun `FlippityGM placeholder`() { FlippityGM() }

    @Test @Disabled("GPU-only : glyph atlas LRU eviction not exposed for raster.")
    fun `FontCacheGM placeholder`() { FontCacheGM() }

    @Test @Disabled("Needs portable LiberationFontMgr (R-suivi).")
    fun `FontMgrBoundsGM placeholder`() { FontMgrBoundsGM() }

    @Test @Disabled("Needs portable LiberationFontMgr (R-suivi).")
    fun `FontMgrGM placeholder`() { FontMgrGM() }

    @Test @Disabled("Needs portable LiberationFontMgr + matchFamilyStyleCharacter (R-suivi).")
    fun `FontMgrMatchGM placeholder`() { FontMgrMatchGM() }

    @Test @Disabled("Needs SkFontArguments.Palette (CPAL palette selection).")
    fun `FontPaletteGM placeholder`() { FontPaletteGM() }

    @Test @Disabled("GPU-only : glyph atlas overflow / regen probe.")
    fun `FontRegenGM placeholder`() { FontRegenGM() }
}
