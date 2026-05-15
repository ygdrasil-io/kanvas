package org.skia.foundation

import org.skia.core.SkSurface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors Skia's
 * [`SkMipmapBuilder`](https://github.com/google/skia/blob/main/src/core/SkMipmapBuilder.h)
 * — a one-shot helper that allocates the per-level pixel buffers for a
 * mip pyramid, lets the caller paint each level explicitly, and finally
 * stamps the resulting chain onto an existing [SkImage] via [attachTo].
 *
 * **Usage** (mirrors upstream `gm/showmiplevels.cpp`) :
 * ```
 * val builder = SkMipmapBuilder(image.imageInfo())
 * for (i in 0 until builder.countLevels()) {
 *     val surf = SkSurfaces.WrapPixels(builder.level(i))
 *     surf?.canvas?.drawColor(myColors[i])
 * }
 * image = builder.attachTo(image) ?: image
 * ```
 *
 * **Level count** matches Skia's `SkMipmap::CountLevelsInternal` :
 * `floor(log2(max(w, h)))` — i.e. one level per power-of-two halving
 * down to (but **not** including) the 1×1 base. Upstream, level 0 is
 * the *first downsample* (half-size), not the base image — when calling
 * `attachTo(src)`, the resulting image carries `1 + builder.countLevels()`
 * total levels (the base image's pixels at index 0, then the builder's
 * levels at indices 1..N).
 *
 * **Compatibility check** : [attachTo] returns the existing pyramid
 * untouched (via a fresh [SkImage] sharing pixels and tags) when the
 * source image's [SkImage.imageInfo] matches the builder's reported
 * info ; otherwise it returns `null` (mirrors upstream's
 * "If these levels are compatible with src, return a new Image…
 * If not compatible, this returns nullptr").
 */
public class SkMipmapBuilder(
    private val info: SkImageInfo,
) {
    /**
     * Per-level backing storage. Allocated up-front so [level] and
     * [levelSurface] return live views over the same bytes.
     *
     * Each level is allocated with [SkColorType.kRGBA_8888] regardless
     * of the builder's reported [info] colour type — kanvas-skia's
     * raster pipeline only stores 32-bit pixels for [SkImage] anyway
     * (see [SkImage.pixels] KDoc), and the upstream contract is that
     * [level] returns a writable surface ; the per-pixel format is an
     * implementation detail the caller can't observe directly.
     */
    private data class LevelStorage(
        val width: Int,
        val height: Int,
        val bitmap: SkBitmap,
        // Backing byte buffer for the [SkPixmap] view. Lazily
        // synthesised from [bitmap.pixels] on first access ; calling
        // [attachTo] flushes any pending writes back into the bitmap.
        val pixmapBuffer: ByteBuffer,
    )

    private val levels: List<LevelStorage> = run {
        val out = ArrayList<LevelStorage>()
        var w = info.width
        var h = info.height
        while (w > 1 || h > 1) {
            val nw = maxOf(1, w / 2)
            val nh = maxOf(1, h / 2)
            val bm = SkBitmap(nw, nh, info.colorSpace, SkColorType.kRGBA_8888)
            val buf = ByteBuffer.allocate(nw * nh * 4).order(ByteOrder.LITTLE_ENDIAN)
            out += LevelStorage(nw, nh, bm, buf)
            w = nw
            h = nh
        }
        out
    }

    /**
     * Mirrors `SkMipmapBuilder::countLevels()` — number of explicitly
     * managed mip levels. Excludes the source image's level-0 pixels :
     * the level-0 base lives on the [SkImage] passed to [attachTo] ; the
     * builder owns the downsampled levels only.
     */
    public fun countLevels(): Int = levels.size

    /**
     * Mirrors `SkMipmapBuilder::level(int index)` — returns a writable
     * [SkPixmap] view of the level [index]'s backing storage. Pair with
     * [SkSurfaces.WrapPixels] to obtain a drawable surface.
     *
     * **Caveat** : kanvas-skia's [SkSurfaces.WrapPixels] copies the
     * pixmap into a fresh bitmap (no zero-copy on the JVM raster
     * backend), so painting through that surface does **not** persist
     * back into this builder's storage. Use [levelSurface] (kanvas-skia
     * extension) when you need a surface whose draws land directly in
     * the builder.
     *
     * Returns an empty [SkPixmap] when [index] is out of range (matches
     * upstream's "default-constructed SkPixmap" behaviour).
     */
    public fun level(index: Int): SkPixmap {
        if (index < 0 || index >= levels.size) return SkPixmap()
        val storage = levels[index]
        val pixmapInfo = SkImageInfo.Make(
            storage.width,
            storage.height,
            SkColorType.kRGBA_8888,
            SkAlphaType.kUnpremul,
            info.colorSpace,
        )
        return SkPixmap(pixmapInfo, storage.pixmapBuffer.duplicate(), storage.width * 4)
    }

    /**
     * **kanvas-skia extension** — returns a raster surface whose draws
     * write directly to the builder's level [index] backing bitmap.
     *
     * Upstream Skia's GM call sites use
     * `SkSurfaces::WrapPixels(builder.level(i))`, which on Skia is
     * zero-copy. kanvas-skia's `WrapPixels` copies through the JVM
     * raster backend (see [SkSurfaces.WrapPixels] KDoc), which would
     * silently discard the per-level paint. This helper preserves the
     * upstream draw-into-the-builder semantic without adding global
     * state to [SkSurfaces].
     *
     * Returns `null` when [index] is out of range.
     */
    public fun levelSurface(index: Int): SkSurface? {
        if (index < 0 || index >= levels.size) return null
        return SkSurface.MakeRasterDirect(levels[index].bitmap)
    }

    /**
     * Mirrors `SkMipmapBuilder::attachTo(const sk_sp<const SkImage>&)`.
     *
     * Returns a fresh [SkImage] combining [src]'s base-level pixels
     * (level 0) with this builder's downsampled levels (levels 1..N).
     *
     * Returns `null` when [src]'s width / height / colour space disagree
     * with the builder's reported [info] (Skia raises this as
     * "incompatible levels"). The colour-type mismatch case is handled
     * loosely : kanvas-skia stores [SkImage] pixels uniformly as 8888
     * regardless of the source [SkColorType], so a colour-type-only
     * mismatch is not an obstacle for the raster path.
     */
    public fun attachTo(src: SkImage): SkImage? {
        if (src.width != info.width || src.height != info.height) return null
        if (src.colorSpace.hash() != info.colorSpace.hash()) return null

        val mipChain = ArrayList<SkImage.MipLevel>()
        // Level 0 is the source image's pixels.
        mipChain += SkImage.MipLevel(src.width, src.height, src.pixels)
        for (storage in levels) {
            // Snapshot the per-level bitmap into a fresh IntArray.
            val bm = storage.bitmap
            mipChain += SkImage.MipLevel(bm.width, bm.height, bm.pixels8888.copyOf())
        }
        return SkImage(
            width = src.width,
            height = src.height,
            pixels = src.pixels,
            colorType = src.colorType,
            colorSpace = src.colorSpace,
            mipLevels = mipChain,
        )
    }
}
