package org.skia.foundation

/**
 * Mirrors Skia's [`SkTileMode`](https://github.com/google/skia/blob/main/include/core/SkTileMode.h).
 * Currently unused by [org.skia.core.SkCanvas.drawImageRect] (which clamps
 * to the source rect under [SrcRectConstraint.kStrict] semantics), but
 * required when image-shaders arrive in a later phase.
 */
public enum class SkTileMode {
    kClamp,
    kRepeat,
    kMirror,
    kDecal,
}
