package org.graphiks.kanvas.surface

import org.graphiks.kanvas.types.ColorSpace

/**
 * The outcome of a single [Surface.render] invocation.
 *
 * Carries the rendered pixel buffer, dimensions, pixel [format], [Diagnostics]
 * accumulated during rendering, and [RenderStats] counters. Provides convenience
 * properties [isClean] and [hasIssues] for quick health checks, and [assertClean]
 * for test assertions.
 *
 * @property pixels      flat row-major RGBA pixel data (4 bytes per pixel)
 * @property width       image width in pixels
 * @property height      image height in pixels
 * @property format      pixel memory layout (RGBA8 or BGRA8)
 * @property colorSpace  the color space of the pixel data
 * @property diagnostics issues recorded during this render pass
 * @property stats       performance and dispatch counters
 */
data class RenderResult(
    val pixels: UByteArray,
    val width: Int,
    val height: Int,
    val format: PixelFormat = PixelFormat.RGBA8,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val diagnostics: Diagnostics,
    val stats: RenderStats,
) {
    /** True when no diagnostics were recorded during rendering. */
    val isClean: Boolean get() = diagnostics.isEmpty

    /** True when at least one diagnostic was recorded. */
    val hasIssues: Boolean get() = !diagnostics.isEmpty

    /**
     * Assert that rendering completed without any diagnostics.
     * @throws IllegalArgumentException if any diagnostics exist, with the [Diagnostics.summary] as the message
     */
    fun assertClean() { require(isClean) { diagnostics.summary() } }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderResult) return false
        return pixels.contentEquals(other.pixels) && width == other.width && height == other.height
            && format == other.format && colorSpace == other.colorSpace && diagnostics == other.diagnostics && stats == other.stats
    }
    override fun hashCode(): Int = pixels.contentHashCode() * 31 + width + height + format.hashCode() + colorSpace.hashCode() + diagnostics.hashCode() + stats.hashCode()
}
