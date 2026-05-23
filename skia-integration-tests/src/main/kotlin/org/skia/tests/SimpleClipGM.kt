package org.skia.tests

/**
 * Alias for [SimpleAaclipGM]. The upstream class is
 * `gm/simpleaaclip.cpp::SimpleClipGM`, an abstract base used by
 * three `DEF_GM` instances : `simpleaaclip_rect`, `simpleaaclip_path`,
 * `simpleaaclip_aaclip`. [SimpleAaclipGM] already covers the rect
 * and path variants. Keeping this `typealias` so tooling that
 * looks up the class via the upstream base-class name resolves
 * cleanly.
 */
public typealias SimpleClipGM = SimpleAaclipRectGM
