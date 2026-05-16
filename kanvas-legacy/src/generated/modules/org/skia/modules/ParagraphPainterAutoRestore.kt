package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ParagraphPainterAutoRestore {
 * public:
 *     explicit ParagraphPainterAutoRestore(ParagraphPainter* painter) : fPainter(painter) {
 *         fPainter->save();
 *     }
 *
 *     ~ParagraphPainterAutoRestore() {
 *         fPainter->restore();
 *     }
 *
 * private:
 *     ParagraphPainter*   fPainter;
 * }
 * ```
 */
public data class ParagraphPainterAutoRestore public constructor(
  /**
   * C++ original:
   * ```cpp
   * ParagraphPainter*   fPainter
   * ```
   */
  private var fPainter: Int?,
)
