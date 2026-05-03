package org.skia.gpu

import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class MtlAlloc : public SkRefCnt {
 * public:
 *     ~MtlAlloc() override = default;
 * }
 * ```
 */
public open class MtlAlloc : SkRefCnt()
