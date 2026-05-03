package org.skia.gpu

import kotlin.Int
import vello_cpp.ShaderStage.FineArea

/**
 * C++ original:
 * ```cpp
 * class VelloFineAreaStep final
 *         : public VelloFineStepBase<vello_cpp::ShaderStage::FineArea, kRGBA_8888_SkColorType> {
 * public:
 *     VelloFineAreaStep();
 * }
 * ```
 */
public class VelloFineAreaStep public constructor() : VelloFineStepBase(), FineArea, Int
