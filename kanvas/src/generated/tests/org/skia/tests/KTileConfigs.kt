package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * static struct {
 *     SkTileMode tmx;
 *     SkTileMode tmy;
 * } kTileConfigs[] = {
 *     { SkTileMode::kRepeat, SkTileMode::kRepeat },
 *     { SkTileMode::kRepeat, SkTileMode::kClamp  },
 *     { SkTileMode::kMirror, SkTileMode::kRepeat },
 * }
 * ```
 */
public open class KTileConfigs
