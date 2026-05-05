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
 * **Behavioural note** — our AWT-backed rasteriser ignores this enum
 * silently. AWT applies its own hinting policy through
 * `RenderingHints.KEY_TEXT_ANTIALIASING` / `KEY_FRACTIONALMETRICS` /
 * `KEY_TEXT_LCD_CONTRAST` rather than per-glyph hint levels. The
 * value is recorded on [SkFont] for source-level compatibility with
 * upstream code (so `font.setHinting(...)` calls compile and execute
 * without crashing) but does not affect rendered pixels.
 *
 * Cf. `archives/MIGRATION_PLAN_TEXT.md` — same family of caveats as
 * [SkFont.Edging.kSubpixelAntiAlias] (which we silently downgrade to
 * `kAntiAlias`).
 */
public enum class SkFontHinting {
    kNone,
    kSlight,
    kNormal,
    kFull,
}
