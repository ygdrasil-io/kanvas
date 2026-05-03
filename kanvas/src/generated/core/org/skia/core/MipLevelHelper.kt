package org.skia.core

import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkMatrix
import org.skia.memory.SkArenaAlloc

/**
 * C++ original:
 * ```cpp
 * struct MipLevelHelper {
 *     SkPixmap pm;
 *     SkMatrix inv;
 *     SkRasterPipelineContexts::GatherCtx* gather;
 *     SkRasterPipelineContexts::TileCtx* limitX;
 *     SkRasterPipelineContexts::TileCtx* limitY;
 *     SkRasterPipelineContexts::DecalTileCtx* decalCtx = nullptr;
 *
 *     void allocAndInit(SkArenaAlloc* alloc,
 *                       const SkSamplingOptions& sampling,
 *                       SkTileMode tileModeX,
 *                       SkTileMode tileModeY) {
 *         gather = alloc->make<SkRasterPipelineContexts::GatherCtx>();
 *         gather->pixels = pm.addr();
 *         gather->stride = pm.rowBytesAsPixels();
 *         gather->width = pm.width();
 *         gather->height = pm.height();
 *
 *         if (sampling.useCubic) {
 *             SkImageShader::CubicResamplerMatrix(sampling.cubic.B, sampling.cubic.C)
 *                     .getColMajor(gather->weights);
 *         }
 *
 *         limitX = alloc->make<SkRasterPipelineContexts::TileCtx>();
 *         limitY = alloc->make<SkRasterPipelineContexts::TileCtx>();
 *         limitX->scale = pm.width();
 *         limitX->invScale = 1.0f / pm.width();
 *         limitY->scale = pm.height();
 *         limitY->invScale = 1.0f / pm.height();
 *
 *         // We would like an image that is mapped 1:1 with device pixels but at a half pixel offset
 *         // to select every pixel from the src image once. Our rasterizer biases upward. That is a
 *         // rect from 0.5...1.5 fills pixel 1 and not pixel 0. So we make exact integer pixel sample
 *         // values select the pixel to the left/above the integer value.
 *         //
 *         // Note that a mirror mapping between canvas and image space will not have this property -
 *         // on one side of the image a row/column will be skipped and one repeated on the other side.
 *         //
 *         // The GM nearest_half_pixel_image tests both of the above scenarios.
 *         //
 *         // The implementation of SkTileMode::kMirror also modifies integer pixel snapping to create
 *         // consistency when the sample coords are running backwards and must account for gather
 *         // modification we perform here. The GM mirror_tile tests this.
 *         if (!sampling.useCubic && sampling.filter == SkFilterMode::kNearest) {
 *             gather->roundDownAtInteger = true;
 *             limitX->mirrorBiasDir = limitY->mirrorBiasDir = 1;
 *         }
 *
 *         if (tileModeX == SkTileMode::kDecal || tileModeY == SkTileMode::kDecal) {
 *             decalCtx = alloc->make<SkRasterPipelineContexts::DecalTileCtx>();
 *             decalCtx->limit_x = limitX->scale;
 *             decalCtx->limit_y = limitY->scale;
 *
 *             // When integer sample coords snap left/up then we want the right/bottom edge of the
 *             // image bounds to be inside the image rather than the left/top edge, that is (0, w]
 *             // rather than [0, w).
 *             if (gather->roundDownAtInteger) {
 *                 decalCtx->inclusiveEdge_x = decalCtx->limit_x;
 *                 decalCtx->inclusiveEdge_y = decalCtx->limit_y;
 *             }
 *         }
 *     }
 * }
 * ```
 */
public data class MipLevelHelper public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPixmap pm
   * ```
   */
  public var pm: SkPixmap,
  /**
   * C++ original:
   * ```cpp
   * SkMatrix inv
   * ```
   */
  public var inv: SkMatrix,
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::GatherCtx* gather
   * ```
   */
  public var gather: GatherCtx?,
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::TileCtx* limitX
   * ```
   */
  public var limitX: TileCtx?,
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::TileCtx* limitY
   * ```
   */
  public var limitY: TileCtx?,
  /**
   * C++ original:
   * ```cpp
   * SkRasterPipelineContexts::DecalTileCtx* decalCtx = nullptr
   * ```
   */
  public var decalCtx: DecalTileCtx?,
) {
  /**
   * C++ original:
   * ```cpp
   * void allocAndInit(SkArenaAlloc* alloc,
   *                       const SkSamplingOptions& sampling,
   *                       SkTileMode tileModeX,
   *                       SkTileMode tileModeY) {
   *         gather = alloc->make<SkRasterPipelineContexts::GatherCtx>();
   *         gather->pixels = pm.addr();
   *         gather->stride = pm.rowBytesAsPixels();
   *         gather->width = pm.width();
   *         gather->height = pm.height();
   *
   *         if (sampling.useCubic) {
   *             SkImageShader::CubicResamplerMatrix(sampling.cubic.B, sampling.cubic.C)
   *                     .getColMajor(gather->weights);
   *         }
   *
   *         limitX = alloc->make<SkRasterPipelineContexts::TileCtx>();
   *         limitY = alloc->make<SkRasterPipelineContexts::TileCtx>();
   *         limitX->scale = pm.width();
   *         limitX->invScale = 1.0f / pm.width();
   *         limitY->scale = pm.height();
   *         limitY->invScale = 1.0f / pm.height();
   *
   *         // We would like an image that is mapped 1:1 with device pixels but at a half pixel offset
   *         // to select every pixel from the src image once. Our rasterizer biases upward. That is a
   *         // rect from 0.5...1.5 fills pixel 1 and not pixel 0. So we make exact integer pixel sample
   *         // values select the pixel to the left/above the integer value.
   *         //
   *         // Note that a mirror mapping between canvas and image space will not have this property -
   *         // on one side of the image a row/column will be skipped and one repeated on the other side.
   *         //
   *         // The GM nearest_half_pixel_image tests both of the above scenarios.
   *         //
   *         // The implementation of SkTileMode::kMirror also modifies integer pixel snapping to create
   *         // consistency when the sample coords are running backwards and must account for gather
   *         // modification we perform here. The GM mirror_tile tests this.
   *         if (!sampling.useCubic && sampling.filter == SkFilterMode::kNearest) {
   *             gather->roundDownAtInteger = true;
   *             limitX->mirrorBiasDir = limitY->mirrorBiasDir = 1;
   *         }
   *
   *         if (tileModeX == SkTileMode::kDecal || tileModeY == SkTileMode::kDecal) {
   *             decalCtx = alloc->make<SkRasterPipelineContexts::DecalTileCtx>();
   *             decalCtx->limit_x = limitX->scale;
   *             decalCtx->limit_y = limitY->scale;
   *
   *             // When integer sample coords snap left/up then we want the right/bottom edge of the
   *             // image bounds to be inside the image rather than the left/top edge, that is (0, w]
   *             // rather than [0, w).
   *             if (gather->roundDownAtInteger) {
   *                 decalCtx->inclusiveEdge_x = decalCtx->limit_x;
   *                 decalCtx->inclusiveEdge_y = decalCtx->limit_y;
   *             }
   *         }
   *     }
   * ```
   */
  public fun allocAndInit(
    alloc: SkArenaAlloc?,
    sampling: SkSamplingOptions,
    tileModeX: SkTileMode,
    tileModeY: SkTileMode,
  ) {
    TODO("Implement allocAndInit")
  }
}
