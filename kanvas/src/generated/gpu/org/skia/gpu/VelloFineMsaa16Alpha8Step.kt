package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineMsaa16R8
import vello_cpp.build_mask_lut_16

/**
 * C++ original:
 * ```cpp
 * class VelloFineMsaa16Alpha8Step final
 *         : public VelloFineMsaaStepBase<vello_cpp::ShaderStage::FineMsaa16R8,
 *                                        kAlpha_8_SkColorType,
 *                                        vello_cpp::build_mask_lut_16> {
 * public:
 *     VelloFineMsaa16Alpha8Step();
 * }
 * ```
 */
public class VelloFineMsaa16Alpha8Step public constructor() : VelloFineMsaaStepBase(), FineMsaa16R8,
    Int, build_mask_lut_16
