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

    // DFTextBlobPerspGM — real body port landed in its own DFTextBlobPerspTest
    //                     (STUB.DF_TEXT_RASTER until raster grows an SDF path).

    // DrawImageSetGM / DrawImageSetAlphaOnlyGM / DrawImageSetRectToRectGM
    // — real ported bodies now live in DrawImageSetTest /
    // DrawImageSetAlphaOnlyTest / DrawImageSetRectToRectTest, each
    // `@Disabled("STUB.EDGE_AA_IMAGE_SET")` until
    // SkCanvas.experimental_DrawEdgeAAImageSet has a body.

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

    // FontRegenGM — real body port landed in its own FontRegenTest
    //               (GPU atlas-stress prologue is N/A for raster, but the
    //                visible text-draw output matches upstream's reference).

    // BadAppleGM — real body port landed in its own BadAppleTest
    //              (sibling of FontRegenGM in gm/fontregen.cpp — just two
    //               256pt text blobs on white background).
}
