package org.skia.core

/**
 * C++ original:
 * ```cpp
 * template <size_t bytes>
 * class SkRasterPipeline_ : public SkRasterPipeline {
 * public:
 *     SkRasterPipeline_()
 *         : SkRasterPipeline(&fBuiltinAlloc) {}
 *
 * private:
 *     SkSTArenaAlloc<bytes> fBuiltinAlloc;
 * }
 * ```
 */
public open class SkRasterPipeline256 public constructor() : SkRasterPipeline(TODO())
