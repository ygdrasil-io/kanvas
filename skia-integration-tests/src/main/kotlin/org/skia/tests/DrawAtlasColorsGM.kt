package org.skia.tests

/**
 * Alias for [DrawAtlasColorGM]. The upstream class is
 * `gm/drawatlascolors.cpp::DrawAtlasColorsGM` (registered as
 * `draw-atlas-colors`) -- our first port picked the singular
 * Kotlin name. Keeping this `typealias` so tooling that looks up
 * the class via the plural upstream name resolves cleanly.
 */
public typealias DrawAtlasColorsGM = DrawAtlasColorGM
