package org.skia.core

/**
 * C++ original:
 * ```cpp
 * class SkOpContourHead : public SkOpContour {
 * public:
 *     SkOpContour* appendContour() {
 *         SkOpContour* contour = this->globalState()->allocator()->make<SkOpContour>();
 *         contour->setNext(nullptr);
 *         SkOpContour* prev = this;
 *         SkOpContour* next;
 *         while ((next = prev->next())) {
 *             prev = next;
 *         }
 *         prev->setNext(contour);
 *         return contour;
 *     }
 *
 *     void joinAllSegments() {
 *         SkOpContour* next = this;
 *         do {
 *             if (!next->count()) {
 *                 continue;
 *             }
 *             next->joinSegments();
 *         } while ((next = next->next()));
 *     }
 *
 *     void remove(SkOpContour* contour) {
 *         if (contour == this) {
 *             SkASSERT(this->count() == 0);
 *             return;
 *         }
 *         SkASSERT(contour->next() == nullptr);
 *         SkOpContour* prev = this;
 *         SkOpContour* next;
 *         while ((next = prev->next()) != contour) {
 *             SkASSERT(next);
 *             prev = next;
 *         }
 *         SkASSERT(prev);
 *         prev->setNext(nullptr);
 *     }
 * }
 * ```
 */
public open class SkOpContourHead : SkOpContour() {
  /**
   * C++ original:
   * ```cpp
   * SkOpContour* appendContour() {
   *         SkOpContour* contour = this->globalState()->allocator()->make<SkOpContour>();
   *         contour->setNext(nullptr);
   *         SkOpContour* prev = this;
   *         SkOpContour* next;
   *         while ((next = prev->next())) {
   *             prev = next;
   *         }
   *         prev->setNext(contour);
   *         return contour;
   *     }
   * ```
   */
  public fun appendContour(): SkOpContour {
    TODO("Implement appendContour")
  }

  /**
   * C++ original:
   * ```cpp
   * void joinAllSegments() {
   *         SkOpContour* next = this;
   *         do {
   *             if (!next->count()) {
   *                 continue;
   *             }
   *             next->joinSegments();
   *         } while ((next = next->next()));
   *     }
   * ```
   */
  public fun joinAllSegments() {
    TODO("Implement joinAllSegments")
  }

  /**
   * C++ original:
   * ```cpp
   * void remove(SkOpContour* contour) {
   *         if (contour == this) {
   *             SkASSERT(this->count() == 0);
   *             return;
   *         }
   *         SkASSERT(contour->next() == nullptr);
   *         SkOpContour* prev = this;
   *         SkOpContour* next;
   *         while ((next = prev->next()) != contour) {
   *             SkASSERT(next);
   *             prev = next;
   *         }
   *         SkASSERT(prev);
   *         prev->setNext(nullptr);
   *     }
   * ```
   */
  public fun remove(contour: SkOpContour?) {
    TODO("Implement remove")
  }
}
