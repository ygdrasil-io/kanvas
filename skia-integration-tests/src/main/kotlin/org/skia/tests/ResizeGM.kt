package org.skia.tests

/**
 * Alias for [ResizeImageFilterGM]. The upstream class is
 * `gm/resizeimagefilter.cpp::ResizeGM` (registered as
 * `resizeimagefilter`) -- the more descriptive Kotlin name was used
 * at first-port time. Keeping this `typealias` so downstream tooling
 * (CI roll-up scripts, dashboard) that looks up the class via the
 * shorter upstream name keeps resolving.
 */
public typealias ResizeGM = ResizeImageFilterGM
