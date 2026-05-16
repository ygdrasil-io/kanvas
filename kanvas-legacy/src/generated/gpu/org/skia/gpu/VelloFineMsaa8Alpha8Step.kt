package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineMsaa8R8
import vello_cpp.build_mask_lut_8

/**
 * C++ original:
 * ```cpp
 * class VelloFineMsaa8Alpha8Step final
 *         : public VelloFineMsaaStepBase<vello_cpp::ShaderStage::FineMsaa8R8,
 *                                        kAlpha_8_SkColorType,
 *                                        vello_cpp::build_mask_lut_8> {
 * public:
 *     VelloFineMsaa8Alpha8Step();
 * }
 * ```
 */
public class VelloFineMsaa8Alpha8Step public constructor() : VelloFineMsaaStepBase(), FineMsaa8R8,
    Int, build_mask_lut_8
