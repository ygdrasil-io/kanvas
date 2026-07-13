package org.graphiks.kanvas.skia.gm.path

/** Port of Skia's `gm/path_stroke_with_zero_length.cpp::zero_length_paths_aa` (522 × 398). */
class ZeroLengthPathsAaGm : ZeroLengthPathsGm(
    name = "zero_length_paths_aa",
    antiAlias = true,
    doubleContour = false,
)
