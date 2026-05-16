package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * class SubsetVerbs : public SubsetPath {
 * public:
 *     explicit SubsetVerbs(const SkPath& path);
 *
 * protected:
 *     SkPath getSubsetPath() const override;
 * }
 * ```
 */
public open class SubsetVerbs public constructor(
  path: SkPath,
) : SubsetPath() {
  /**
   * C++ original:
   * ```cpp
   * SkPath SubsetVerbs::getSubsetPath() const {
   *     SkPathBuilder result;
   *     result.setFillType(fPath.getFillType());
   *     if (!fSelected.size()) {
   *         return result.detach();
   *     }
   *     int verbIndex = 0;
   *     bool addMoveTo = true;
   *     bool addLineTo = false;
   *     for (auto [verb, pts, w] : SkPathPriv::Iterate(fPath)) {
   *         bool enabled = SkPathVerb::kLine <= verb && verb <= SkPathVerb::kCubic
   *             ? fSelected[verbIndex++] : false;
   *         if (enabled) {
   *             if (addMoveTo) {
   *                 result.moveTo(pts[0]);
   *                 addMoveTo = false;
   *             } else if (addLineTo) {
   *                 result.lineTo(pts[0]);
   *                 addLineTo = false;
   *             }
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
   *                 result.close();
   *                 addMoveTo = true;
   *                 addLineTo = false;
   *                 continue;
   *             default:
   *                 SkDEBUGFAIL("bad verb");
   *                 return result.detach();
   *         }
   *         addLineTo = !enabled;
   *     }
   *     return result.detach();
   * }
   * ```
   */
  protected override fun getSubsetPath(): Int {
    TODO("Implement getSubsetPath")
  }
}
