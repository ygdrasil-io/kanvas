package org.skia.foundation

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRefCnt : public SkRefCntBase {
 *     // "#include SK_REF_CNT_MIXIN_INCLUDE" doesn't work with this build system.
 *     #if defined(SK_BUILD_FOR_GOOGLE3)
 *     public:
 *         void deref() const { this->unref(); }
 *     #endif
 * }
 * ```
 */
public open class SkRefCnt : SkRefCntBase()
