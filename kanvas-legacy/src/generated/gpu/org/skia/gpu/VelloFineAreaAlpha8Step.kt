package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineAreaR8

/**
 * C++ original:
 * ```cpp
 * class VelloFineAreaAlpha8Step final
 *         : public VelloFineStepBase<vello_cpp::ShaderStage::FineAreaR8, kAlpha_8_SkColorType> {
 * public:
 *     VelloFineAreaAlpha8Step();
 * }
 * ```
 */
public class VelloFineAreaAlpha8Step public constructor() : VelloFineStepBase(), FineAreaR8, Int
