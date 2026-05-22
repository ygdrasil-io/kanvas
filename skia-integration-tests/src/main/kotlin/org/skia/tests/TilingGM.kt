package org.skia.tests

/**
 * Alias for [TilemodesGM]. The upstream class is
 * `gm/tilemodes.cpp::TilingGM` (registered as `tilemodes` and
 * `tilemodes_npot`) -- the descriptive name was used at first-port
 * time. Keeping this `typealias` so downstream tooling that looks up
 * the class via the shorter upstream name keeps resolving.
 */
public typealias TilingGM = TilemodesGM
