package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineMsaa16
import vello_cpp.build_mask_lut_16

/**
 * C++ original:
 * ```cpp
 * class VelloFineMsaa16Step final : public VelloFineMsaaStepBase<vello_cpp::ShaderStage::FineMsaa16,
 *                                                                kRGBA_8888_SkColorType,
 *                                                                vello_cpp::build_mask_lut_16> {
 * public:
 *     VelloFineMsaa16Step();
 * }
 * ```
 */
public class VelloFineMsaa16Step public constructor() : VelloFineMsaaStepBase(), FineMsaa16, Int,
    build_mask_lut_16
