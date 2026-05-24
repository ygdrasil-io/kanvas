package org.skia.foundation

/**
 * Mirrors Skia's `SkFontHinting` (`include/core/SkFontTypes.h`).
 *
 * Glyph hinting policy — tells the font scaler how aggressively to
 * snap glyph outlines onto the pixel grid for legibility:
 *  - [kNone]   — outlines unchanged.
 *  - [kSlight] — minimal modification to improve contrast.
 *  - [kNormal] — outlines modified to improve contrast (Skia default).
 *  - [kFull]   — outlines modified for maximum contrast.
 *
 * **Behavioural note** — the portable OpenType backend records this enum
 * for source-level compatibility with upstream code, but currently does
 * not modify outlines or rendered pixels based on the requested hint level.
 */
public enum class SkFontHinting {
    kNone,
    kSlight,
    kNormal,
    kFull,
}
