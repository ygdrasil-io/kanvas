package org.skia.tests

/**
 * Alias for [ScaledTilemodesGM]. The upstream class is
 * `gm/tilemodes_scaled.cpp::ScaledTilingGM` (registered as
 * `scaled_tilemodes` / `scaled_tilemodes_npot`) -- our first port
 * picked the registration name. Keeping this `typealias` so tooling
 * that looks up the class via the original C++ identifier resolves
 * cleanly.
 */
public typealias ScaledTilingGM = ScaledTilemodesGM
