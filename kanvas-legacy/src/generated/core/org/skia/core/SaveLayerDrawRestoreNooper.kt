package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SaveLayerDrawRestoreNooper {
 *     // Note that we use IsSingleDraw here, to avoid matching drawAtlas, drawVertices, etc...
 *     // Those operations (can) draw multiple, overlapping primitives that blend with each other.
 *     // Applying this operation to them changes their behavior. (skbug.com/40045501)
 *     typedef Pattern<Is<SaveLayer>, IsSingleDraw, Is<Restore>> Match;
 *
 *     bool onMatch(SkRecord* record, Match* match, int begin, int end) {
 *         if (match->first<SaveLayer>()->backdrop) {
 *             // can't throw away the layer if we have a backdrop
 *             return false;
 *         }
 *
 *         if (!match->first<SaveLayer>()->filters.empty()) {
 *             // Our optimizations don't handle the filter list correctly - don't bother trying
 *             return false;
 *         }
 *
 *         // A SaveLayer's bounds field is just a hint, so we should be free to ignore it.
 *         SkPaint* layerPaint = match->first<SaveLayer>()->paint;
 *         SkPaint* drawPaint = match->second<SkPaint>();
 *
 *         if (nullptr == layerPaint && effectively_srcover(drawPaint)) {
 *             // There wasn't really any point to this SaveLayer at all.
 *             return KillSaveLayerAndRestore(record, begin);
 *         }
 *
 *         if (drawPaint == nullptr) {
 *             // We can just give the draw the SaveLayer's paint.
 *             // TODO(mtklein): figure out how to do this clearly
 *             return false;
 *         }
 *
 *         if (!fold_opacity_layer_color_to_paint(layerPaint, false /*isSaveLayer*/, drawPaint)) {
 *             return false;
 *         }
 *
 *         return KillSaveLayerAndRestore(record, begin);
 *     }
 *
 *     static bool KillSaveLayerAndRestore(SkRecord* record, int saveLayerIndex) {
 *         record->replace<NoOp>(saveLayerIndex);    // SaveLayer
 *         record->replace<NoOp>(saveLayerIndex+2);  // Restore
 *         return true;
 *     }
 * }
 * ```
 */
public open class SaveLayerDrawRestoreNooper {
  /**
   * C++ original:
   * ```cpp
   * bool onMatch(SkRecord* record, Match* match, int begin, int end) {
   *         if (match->first<SaveLayer>()->backdrop) {
   *             // can't throw away the layer if we have a backdrop
   *             return false;
   *         }
   *
   *         if (!match->first<SaveLayer>()->filters.empty()) {
   *             // Our optimizations don't handle the filter list correctly - don't bother trying
   *             return false;
   *         }
   *
   *         // A SaveLayer's bounds field is just a hint, so we should be free to ignore it.
   *         SkPaint* layerPaint = match->first<SaveLayer>()->paint;
   *         SkPaint* drawPaint = match->second<SkPaint>();
   *
   *         if (nullptr == layerPaint && effectively_srcover(drawPaint)) {
   *             // There wasn't really any point to this SaveLayer at all.
   *             return KillSaveLayerAndRestore(record, begin);
   *         }
   *
   *         if (drawPaint == nullptr) {
   *             // We can just give the draw the SaveLayer's paint.
   *             // TODO(mtklein): figure out how to do this clearly
   *             return false;
   *         }
   *
   *         if (!fold_opacity_layer_color_to_paint(layerPaint, false /*isSaveLayer*/, drawPaint)) {
   *             return false;
   *         }
   *
   *         return KillSaveLayerAndRestore(record, begin);
   *     }
   * ```
   */
  public fun onMatch(
    record: SkRecord?,
    match: SaveLayerDrawRestoreNooperMatch?,
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
     *         record->replace<NoOp>(saveLayerIndex);    // SaveLayer
     *         record->replace<NoOp>(saveLayerIndex+2);  // Restore
     *         return true;
     *     }
     * ```
     */
    public fun killSaveLayerAndRestore(record: SkRecord?, saveLayerIndex: Int): Boolean {
      TODO("Implement killSaveLayerAndRestore")
    }
  }
}
