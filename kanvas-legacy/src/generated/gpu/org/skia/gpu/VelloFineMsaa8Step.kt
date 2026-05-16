package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineMsaa8
import vello_cpp.build_mask_lut_8

/**
 * C++ original:
 * ```cpp
 * class VelloFineMsaa8Step final : public VelloFineMsaaStepBase<vello_cpp::ShaderStage::FineMsaa8,
 *                                                               kRGBA_8888_SkColorType,
 *                                                               vello_cpp::build_mask_lut_8> {
 * public:
 *     VelloFineMsaa8Step();
 * }
 * ```
 */
public class VelloFineMsaa8Step public constructor() : VelloFineMsaaStepBase(), FineMsaa8, Int,
    build_mask_lut_8
