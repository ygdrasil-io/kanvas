package org.graphiks.kanvas.skia.gm.path

/** Port of Skia's `gm/path_stroke_with_zero_length.cpp::zero_length_paths_dbl_bw` (1874 × 398). */
class ZeroLengthPathsDblBwGm : ZeroLengthPathsGm(
    name = "zero_length_paths_dbl_bw",
    antiAlias = false,
    doubleContour = true,
)
