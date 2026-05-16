package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SaveNoDrawsRestoreNooper {
 *     // Greedy matches greedily, so we also have to exclude Save and Restore.
 *     // Nested SaveLayers need to be excluded, or we'll match their Restore!
 *     typedef Pattern<Is<Save>,
 *                     Greedy<Not<Or<Is<Save>,
 *                                   Is<SaveLayer>,
 *                                   Is<Restore>,
 *                                   IsDraw>>>,
 *                     Is<Restore>>
 *         Match;
 *
 *     bool onMatch(SkRecord* record, Match*, int begin, int end) {
 *         // The entire span between Save and Restore (inclusively) does nothing.
 *         for (int i = begin; i < end; i++) {
 *             record->replace<NoOp>(i);
 *         }
 *         return true;
 *     }
 * }
 * ```
 */
public open class SaveNoDrawsRestoreNooper {
  /**
   * C++ original:
   * ```cpp
   * bool onMatch(SkRecord* record, Match*, int begin, int end) {
   *         // The entire span between Save and Restore (inclusively) does nothing.
   *         for (int i = begin; i < end; i++) {
   *             record->replace<NoOp>(i);
   *         }
   *         return true;
   *     }
   * ```
   */
  public fun onMatch(
    record: SkRecord?,
    param1: SaveNoDrawsRestoreNooperMatch?,
    begin: Int,
    end: Int,
  ): Boolean {
    TODO("Implement onMatch")
  }
}
