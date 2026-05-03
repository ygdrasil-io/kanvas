package org.skia.foundation

/**
 * Region operation applied when adding a clip to the current clip stack.
 * Mirrors Skia's `SkClipOp`. Skia trimmed this enum down to two values
 * in modern releases — historical `kReplace` / `kUnion` / `kReverseDifference`
 * are no longer supported.
 *
 * - [kIntersect] : the new clip becomes the intersection of the existing
 *   clip with the supplied shape (the common, default case).
 * - [kDifference] : the new clip becomes the existing clip minus the
 *   supplied shape (cuts a hole). `:kanvas-skia`'s rasterizer does not
 *   yet honour this — the value exists so callers can express intent
 *   and a future device-side coverage mask can implement it.
 *
 * The numeric ordinal matches upstream (`kDifference = 0`, `kIntersect = 1`).
 */
public enum class SkClipOp {
    kDifference,
    kIntersect,
}
