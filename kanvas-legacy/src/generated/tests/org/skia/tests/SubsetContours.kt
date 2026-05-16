package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * class SubsetContours : public SubsetPath {
 * public:
 *     explicit SubsetContours(const SkPath& path);
 *
 * protected:
 *     SkPath getSubsetPath() const override;
 * }
 * ```
 */
public open class SubsetContours public constructor(
  path: SkPath,
) : SubsetPath() {
  /**
   * C++ original:
   * ```cpp
   * SkPath SubsetContours::getSubsetPath() const {
   *     SkPathBuilder result;
   *     result.setFillType(fPath.getFillType());
   *     if (!fSelected.size()) {
   *         return result.detach();
   *     }
   *     int contourCount = 0;
   *     bool enabled = fSelected[0];
   *     bool addMoveTo = true;
   *     for (auto [verb, pts, w] : SkPathPriv::Iterate(fPath)) {
   *         if (enabled && addMoveTo) {
   *             result.moveTo(pts[0]);
   *             addMoveTo = false;
   *         }
   *         switch (verb) {
   *             case SkPathVerb::kMove:
   *                 break;
   *             case SkPathVerb::kLine:
   *                 if (enabled) {
   *                     result.lineTo(pts[1]);
   *                 }
   *                 break;
   *             case SkPathVerb::kQuad:
   *                 if (enabled) {
   *                     result.quadTo(pts[1], pts[2]);
   *                 }
   *                 break;
   *             case SkPathVerb::kConic:
   *                 if (enabled) {
   *                     result.conicTo(pts[1], pts[2], *w);
   *                 }
   *                 break;
   *             case SkPathVerb::kCubic:
   *                  if (enabled) {
   *                     result.cubicTo(pts[1], pts[2], pts[3]);
   *                 }
   *                 break;
   *             case SkPathVerb::kClose:
   *                 if (enabled) {
   *                     result.close();
   *                 }
   *                 if (++contourCount >= fSelected.size()) {
   *                     break;
   *                 }
   *                 enabled = fSelected[contourCount];
   *                 addMoveTo = true;
   *                 continue;
   *             default:
   *                 SkDEBUGFAIL("bad verb");
   *                 return result.detach();
   *         }
   *     }
   *     return result.detach();
   * }
   * ```
   */
  protected override fun getSubsetPath(): Int {
    TODO("Implement getSubsetPath")
  }
}
