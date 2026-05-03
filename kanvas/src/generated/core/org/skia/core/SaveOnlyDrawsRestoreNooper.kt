package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SaveOnlyDrawsRestoreNooper {
 *     typedef Pattern<Is<Save>,
 *                     Greedy<Or<Is<NoOp>, IsDraw>>,
 *                     Is<Restore>>
 *         Match;
 *
 *     bool onMatch(SkRecord* record, Match*, int begin, int end) {
 *         record->replace<NoOp>(begin);  // Save
 *         record->replace<NoOp>(end-1);  // Restore
 *         return true;
 *     }
 * }
 * ```
 */
public open class SaveOnlyDrawsRestoreNooper {
  /**
   * C++ original:
   * ```cpp
   * bool onMatch(SkRecord* record, Match*, int begin, int end) {
   *         record->replace<NoOp>(begin);  // Save
   *         record->replace<NoOp>(end-1);  // Restore
   *         return true;
   *     }
   * ```
   */
  public fun onMatch(
    record: SkRecord?,
    param1: SaveOnlyDrawsRestoreNooperMatch?,
    begin: Int,
    end: Int,
  ): Boolean {
    TODO("Implement onMatch")
  }
}
