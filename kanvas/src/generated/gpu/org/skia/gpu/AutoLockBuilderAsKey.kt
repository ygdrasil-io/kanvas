package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class AutoLockBuilderAsKey {
 * public:
 *     AutoLockBuilderAsKey(PaintParamsKeyBuilder* builder)
 *             : fBuilder(builder)
 *             , fKey(builder->lockAsKey()) {}
 *
 *     ~AutoLockBuilderAsKey() {
 *         fBuilder->unlock();
 *     }
 *
 *     // Use as a PaintParamsKey
 *     const PaintParamsKey& operator*() const { return fKey; }
 *     const PaintParamsKey* operator->() const { return &fKey; }
 *
 * private:
 *     PaintParamsKeyBuilder* fBuilder;
 *     PaintParamsKey fKey;
 * }
 * ```
 */
public data class AutoLockBuilderAsKey public constructor(
  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder* fBuilder
   * ```
   */
  private var fBuilder: PaintParamsKeyBuilder?,
  /**
   * C++ original:
   * ```cpp
   * PaintParamsKey fKey
   * ```
   */
  private var fKey: PaintParamsKey,
) {
  /**
   * C++ original:
   * ```cpp
   * const PaintParamsKey& operator*() const { return fKey; }
   * ```
   */
  public fun `get`(): PaintParamsKey {
    TODO("Implement get")
  }
}
