package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoCanvasRestore {
 * public:
 *
 *     /** Preserves SkCanvas::save() count. Optionally saves SkCanvas clip and SkCanvas matrix.
 *
 *         @param canvas  SkCanvas to guard
 *         @param doSave  call SkCanvas::save()
 *         @return        utility to restore SkCanvas state on destructor
 *     */
 *     SkAutoCanvasRestore(SkCanvas* canvas, bool doSave) : fCanvas(canvas), fSaveCount(0) {
 *         if (fCanvas) {
 *             fSaveCount = canvas->getSaveCount();
 *             if (doSave) {
 *                 canvas->save();
 *             }
 *         }
 *     }
 *
 *     /** Restores SkCanvas to saved state. Destructor is called when container goes out of
 *         scope.
 *     */
 *     ~SkAutoCanvasRestore() {
 *         if (fCanvas) {
 *             fCanvas->restoreToCount(fSaveCount);
 *         }
 *     }
 *
 *     /** Restores SkCanvas to saved state immediately. Subsequent calls and
 *         ~SkAutoCanvasRestore() have no effect.
 *     */
 *     void restore() {
 *         if (fCanvas) {
 *             fCanvas->restoreToCount(fSaveCount);
 *             fCanvas = nullptr;
 *         }
 *     }
 *
 * private:
 *     SkCanvas*   fCanvas;
 *     int         fSaveCount;
 *
 *     SkAutoCanvasRestore(SkAutoCanvasRestore&&) = delete;
 *     SkAutoCanvasRestore(const SkAutoCanvasRestore&) = delete;
 *     SkAutoCanvasRestore& operator=(SkAutoCanvasRestore&&) = delete;
 *     SkAutoCanvasRestore& operator=(const SkAutoCanvasRestore&) = delete;
 * }
 * ```
 */
public data class SkAutoCanvasRestore public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkCanvas*   fCanvas
   * ```
   */
  private var fCanvas: SkCanvas?,
  /**
   * C++ original:
   * ```cpp
   * int         fSaveCount
   * ```
   */
  private var fSaveCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void restore() {
   *         if (fCanvas) {
   *             fCanvas->restoreToCount(fSaveCount);
   *             fCanvas = nullptr;
   *         }
   *     }
   * ```
   */
  public fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAutoCanvasRestore& operator=(SkAutoCanvasRestore&&) = delete
   * ```
   */
  private fun assign(param0: SkAutoCanvasRestore) {
    TODO("Implement assign")
  }
}
