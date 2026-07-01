package org.graphiks.kanvas.surface

/**
 * Memory layout of pixel data on the [Surface] and in [RenderResult].
 *
 * - [RGBA8] — red, green, blue, alpha in that order, 8 bits per channel.
 * - [BGRA8] — blue, green, red, alpha in that order, 8 bits per channel.
 */
enum class PixelFormat { RGBA8, BGRA8 }
