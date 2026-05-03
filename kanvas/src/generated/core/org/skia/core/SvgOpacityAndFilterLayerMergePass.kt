package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SvgOpacityAndFilterLayerMergePass {
 *     typedef Pattern<Is<SaveLayer>, Is<Save>, Is<ClipRect>, Is<SaveLayer>,
 *                     Is<Restore>, Is<Restore>, Is<Restore>> Match;
 *
 *     bool onMatch(SkRecord* record, Match* match, int begin, int end) {
 *         if (match->first<SaveLayer>()->backdrop) {
 *             // can't throw away the layer if we have a backdrop
 *             return false;
 *         }
 *
 *         if (!match->first<SaveLayer>()->filters.empty() ||
 *             !match->fourth<SaveLayer>()->filters.empty()) {
 *             // Our optimizations don't handle the filter list correctly - don't bother trying
 *             return false;
 *         }
 *
 *         SkPaint* opacityPaint = match->first<SaveLayer>()->paint;
 *         if (nullptr == opacityPaint) {
 *             // There wasn't really any point to this SaveLayer at all.
 *             return KillSaveLayerAndRestore(record, begin);
 *         }
 *
 *         // This layer typically contains a filter, but this should work for layers with for other
 *         // purposes too.
 *         SkPaint* filterLayerPaint = match->fourth<SaveLayer>()->paint;
 *         if (filterLayerPaint == nullptr) {
 *             // We can just give the inner SaveLayer the paint of the outer SaveLayer.
 *             // TODO(mtklein): figure out how to do this clearly
 *             return false;
 *         }
 *
 *         if (!fold_opacity_layer_color_to_paint(opacityPaint, true /*isSaveLayer*/,
 *                                                filterLayerPaint)) {
 *             return false;
 *         }
 *
 *         return KillSaveLayerAndRestore(record, begin);
 *     }
 *
 *     static bool KillSaveLayerAndRestore(SkRecord* record, int saveLayerIndex) {
 *         record->replace<NoOp>(saveLayerIndex);     // SaveLayer
 *         record->replace<NoOp>(saveLayerIndex + 6); // Restore
 *         return true;
 *     }
 * }
 * ```
 */
public open class SvgOpacityAndFilterLayerMergePass {
  /**
   * C++ original:
   * ```cpp
   * bool onMatch(SkRecord* record, Match* match, int begin, int end) {
   *         if (match->first<SaveLayer>()->backdrop) {
   *             // can't throw away the layer if we have a backdrop
   *             return false;
   *         }
   *
   *         if (!match->first<SaveLayer>()->filters.empty() ||
   *             !match->fourth<SaveLayer>()->filters.empty()) {
   *             // Our optimizations don't handle the filter list correctly - don't bother trying
   *             return false;
   *         }
   *
   *         SkPaint* opacityPaint = match->first<SaveLayer>()->paint;
   *         if (nullptr == opacityPaint) {
   *             // There wasn't really any point to this SaveLayer at all.
   *             return KillSaveLayerAndRestore(record, begin);
   *         }
   *
   *         // This layer typically contains a filter, but this should work for layers with for other
   *         // purposes too.
   *         SkPaint* filterLayerPaint = match->fourth<SaveLayer>()->paint;
   *         if (filterLayerPaint == nullptr) {
   *             // We can just give the inner SaveLayer the paint of the outer SaveLayer.
   *             // TODO(mtklein): figure out how to do this clearly
   *             return false;
   *         }
   *
   *         if (!fold_opacity_layer_color_to_paint(opacityPaint, true /*isSaveLayer*/,
   *                                                filterLayerPaint)) {
   *             return false;
   *         }
   *
   *         return KillSaveLayerAndRestore(record, begin);
   *     }
   * ```
   */
  public fun onMatch(
    record: SkRecord?,
    match: SvgOpacityAndFilterLayerMergePassMatch?,
    begin: Int,
    end: Int,
  ): Boolean {
    TODO("Implement onMatch")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool KillSaveLayerAndRestore(SkRecord* record, int saveLayerIndex) {
     *         record->replace<NoOp>(saveLayerIndex);     // SaveLayer
     *         record->replace<NoOp>(saveLayerIndex + 6); // Restore
     *         return true;
     *     }
     * ```
     */
    public fun killSaveLayerAndRestore(record: SkRecord?, saveLayerIndex: Int): Boolean {
      TODO("Implement killSaveLayerAndRestore")
    }
  }
}
