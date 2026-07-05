package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Aggregate `@Disabled` placeholder for the O6 batch GMs that need
 * features the raster path doesn't expose yet. Each test method
 * documents *why* the upstream GM cannot be ported today so the
 * tree advertises the missing coverage.
 */
@Disabled("O6 batch — GMs depending on unported APIs (image codecs / SkAAClip / YUV / etc.)")
class O6UnportableGMsTest {

    /** `gm/mandoline.cpp::SimpleClipGM` — uses SkAAClip / setPath / Rgn op. */
    @Test fun `SimpleClipGM requires SkAAClip path-region API`() {}

    /** `gm/bleed.cpp::SrcRectConstraintGM` (×6 variants) — needs custom surfaces, color images. */
    @Test fun `SrcRectConstraintGM family requires custom surfaces and per-mipmap sampling control`() {}

    /** `gm/vertices.cpp::VerticesGM` (1× + scaled) — needs focused visual parity follow-up. */
    @Test fun `VerticesGM requires drawVertices visual parity follow-up`() {}

    /** `gm/wacky_yuv_formats.cpp::WackyYUVFormatsGM` — needs YUV plane API + 8 formats. */
    @Test fun `WackyYUVFormatsGM requires SkYUVAPixmaps and 8 YUV layout formats`() {}

    /** `gm/wacky_yuv_formats.cpp::YUVMakeColorSpaceGM` — needs SkImage_FromYUVAPixmaps colorspace tags. */
    @Test fun `YUVMakeColorSpaceGM requires SkImage_FromYUVAPixmaps with colorspace conversion`() {}

    /** `gm/tilemodes_scaled.cpp::ScaledTilingGM` (POT + NPOT) — needs SkTextUtils + Aniso sampling. */
    @Test fun `ScaledTilingGM requires SkTextUtils DrawString and Aniso bitmap sampling`() {}

    /**
     * `gm/shapes.cpp::ShapesGM` is an *abstract base*. The two concrete
     * subclasses (`SimpleShapesGM`, `InnerShapesGM`) are already ported
     * — see [SimpleShapesGM] and [InnerShapesGM]. No further work needed.
     */
    @Test fun `ShapesGM is abstract — concrete subclasses already ported`() {}
}
