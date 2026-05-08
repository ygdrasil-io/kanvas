package org.skia.core

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo

/**
 * Mirrors Skia's
 * [`GrSurfaceCharacterization`](https://github.com/google/skia/blob/main/include/private/chromium/GrSurfaceCharacterization.h)
 * — a value-type description of a surface's signature : width,
 * height, [SkColorType], [SkAlphaType], [SkColorSpace]. Two surfaces
 * with **identical** characterization are guaranteed to play back a
 * given [SkDeferredDisplayList] bit-identically.
 *
 * **Use case** — DDL compatibility check. A DDL recorded against
 * characterization `C` may only be replayed onto a surface whose
 * characterization equals `C`. [SkSurface.draw] enforces this :
 * mismatch returns `false` and skips the playback (matches Skia's
 * `skgpu::ganesh::DrawDDL` semantics).
 *
 * **Raster scope** — for raster sinks, the characterization carries
 * exactly the fields of [SkImageInfo] (no GPU-only state like
 * sample-count, mip-mapped flag, origin, etc.). The class is a thin
 * wrapper preserving upstream's vocabulary while delegating the
 * heavy lifting to [SkImageInfo].
 *
 * Construct via [Make] (accepts an [SkImageInfo]) or [From] (snaps
 * a characterization off an existing [SkSurface]).
 */
public class SkSurfaceCharacterization private constructor(
    public val imageInfo: SkImageInfo,
) {
    public val width: Int get() = imageInfo.width
    public val height: Int get() = imageInfo.height
    public val colorType: SkColorType get() = imageInfo.colorType
    public val alphaType: SkAlphaType get() = imageInfo.alphaType
    public val colorSpace: SkColorSpace get() = imageInfo.colorSpace

    /**
     * `true` iff this characterization matches the [surface]'s current
     * `imageInfo()`. Used by [SkSurface.draw] to gate DDL playback.
     */
    public fun isCompatibleWith(surface: SkSurface): Boolean =
        surface.imageInfo() == imageInfo

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkSurfaceCharacterization) return false
        return imageInfo == other.imageInfo
    }

    override fun hashCode(): Int = imageInfo.hashCode()

    override fun toString(): String =
        "SkSurfaceCharacterization(${width}×${height}, $colorType, $alphaType)"

    public companion object {
        /**
         * Build a characterization directly from an [SkImageInfo].
         * Mirrors Skia's
         * `GrRecordingContext::createCharacterization(SkImageInfo)`
         * collapsed onto our raster-only pipeline.
         */
        public fun Make(imageInfo: SkImageInfo): SkSurfaceCharacterization {
            require(!imageInfo.isEmpty()) {
                "SkSurfaceCharacterization: empty imageInfo $imageInfo"
            }
            return SkSurfaceCharacterization(imageInfo)
        }

        /**
         * Snap a characterization off an existing [surface]. Useful for
         * tile-based pipelines that want to record a DDL against the
         * exact destination they already have. Mirrors Skia's
         * `SkSurface::characterize()` collapsed onto raster.
         */
        public fun From(surface: SkSurface): SkSurfaceCharacterization =
            SkSurfaceCharacterization(surface.imageInfo())
    }
}
