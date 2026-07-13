package org.graphiks.kanvas.skia.gm.path

/** Port of Skia's `gm/path_stroke_with_zero_length.cpp::zero_length_paths_dbl_aa` (1874 × 398). */
class ZeroLengthPathsDblAaGm : ZeroLengthPathsGm(
    name = "zero_length_paths_dbl_aa",
    antiAlias = true,
    doubleContour = true,
)
