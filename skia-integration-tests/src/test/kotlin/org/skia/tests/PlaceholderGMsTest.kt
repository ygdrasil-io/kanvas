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

    // DFTextBlobPerspGM — real body port landed in its own DFTextBlobPerspTest
    //                     (STUB.DF_TEXT_RASTER until raster grows an SDF path).

    // DrawImageSetGM / DrawImageSetAlphaOnlyGM / DrawImageSetRectToRectGM
    // — real ported bodies now live in DrawImageSetTest /
    // DrawImageSetAlphaOnlyTest / DrawImageSetRectToRectTest, each
    // `@Disabled("STUB.EDGE_AA_IMAGE_SET")` until
    // SkCanvas.experimental_DrawEdgeAAImageSet has a body.

    // FlippityGM — real body port landed in its own FlippityTest. The
    // GPU-only `kBottomLeft_GrSurfaceOrigin` flag collapses on raster
    // (both reference-image rows render identically), matching the
    // upstream raster sink that produced `original-888/flippity.png`.

    // FontCacheGM — real body port landed in its own FontCacheTest
    //                (cross-backend ratchet at ~84 %, see PR notes).

    // FontPaletteGM — real body port landed in its own FontPaletteTest
    //                 (STUB.COLR_V1 / STUB.FONTATIONS / STUB.FIXTURE —
    //                  same blocker trio as ColrV1Test).

    // FontRegenGM — real body port landed in its own FontRegenTest
    //               (GPU atlas-stress prologue is N/A for raster, but the
    //                visible text-draw output matches upstream's reference).

    // BadAppleGM — real body port landed in its own BadAppleTest
    //              (sibling of FontRegenGM in gm/fontregen.cpp — just two
    //               256pt text blobs on white background).
}
