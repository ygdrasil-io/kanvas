package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkEncodedImageFormat`](https://github.com/google/skia/blob/main/include/codec/SkEncodedImageFormat.h)
 * — the on-disk container format an [Codec] decoded from.
 *
 * D3.1 only ships [kPNG]; the other variants are listed for parity with
 * upstream so callers can `switch` exhaustively without us having to
 * widen the enum each time a new codec lands (D3.2 = JPEG, D3.3 = GIF
 * + BMP + WBMP, D3.4 = WEBP).
 */
public enum class SkEncodedImageFormat {
    kBMP,
    kGIF,
    kICO,
    kJPEG,
    kPNG,
    kWBMP,
    kWEBP,
    kPKM,
    kKTX,
    kASTC,
    kDNG,
    kHEIF,
    kAVIF,
    kJPEGXL,
}
