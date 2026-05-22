package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `gm/slug.cpp::SlugGM` uses Skia's chromium-only `sktext::gpu::Slug`
 * abstraction (GPU/Graphite text-blob preprocessing). The whole GM
 * lives under `#if defined(SK_GANESH) || defined(SK_GRAPHITE)`, and
 * `Slug::ConvertBlob` has no raster equivalent. Marked Ignored — there
 * is no useful raster port and our :kanvas-skia text pipeline doesn't
 * model Slug.
 */
@Disabled("SlugGM requires sktext::gpu::Slug (GPU-only chromium extension)")
class SlugTest {
    @Test
    fun `SlugGM is not portable to raster`() {
        // Intentionally empty.
    }
}
