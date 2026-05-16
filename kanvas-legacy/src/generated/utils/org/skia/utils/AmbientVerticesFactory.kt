package org.skia.utils

import kotlin.Boolean
import org.skia.core.SkVertices
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct AmbientVerticesFactory {
 *     SkScalar fOccluderHeight = SK_ScalarNaN;  // NaN so that isCompatible will fail until init'ed.
 *     bool fTransparent;
 *     SkVector fOffset;
 *
 *     bool isCompatible(const AmbientVerticesFactory& that, SkVector* translate) const {
 *         if (fOccluderHeight != that.fOccluderHeight || fTransparent != that.fTransparent) {
 *             return false;
 *         }
 *         *translate = that.fOffset;
 *         return true;
 *     }
 *
 *     sk_sp<SkVertices> makeVertices(const SkPath& path, const SkMatrix& ctm,
 *                                    SkVector* translate) const {
 *         SkPoint3 zParams = SkPoint3::Make(0, 0, fOccluderHeight);
 *         // pick a canonical place to generate shadow
 *         SkMatrix noTrans(ctm);
 *         if (!ctm.hasPerspective()) {
 *             noTrans[SkMatrix::kMTransX] = 0;
 *             noTrans[SkMatrix::kMTransY] = 0;
 *         }
 *         *translate = fOffset;
 *         return SkShadowTessellator::MakeAmbient(path, noTrans, zParams, fTransparent);
 *     }
 * }
 * ```
 */
public data class AmbientVerticesFactory public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fOccluderHeight = SK_ScalarNaN
   * ```
   */
  public var fOccluderHeight: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * bool fTransparent
   * ```
   */
  public var fTransparent: Boolean,
  /**
   * C++ original:
   * ```cpp
   * SkVector fOffset
   * ```
   */
  public var fOffset: SkVector,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isCompatible(const AmbientVerticesFactory& that, SkVector* translate) const {
   *         if (fOccluderHeight != that.fOccluderHeight || fTransparent != that.fTransparent) {
   *             return false;
   *         }
   *         *translate = that.fOffset;
   *         return true;
   *     }
   * ```
   */
  public fun isCompatible(that: AmbientVerticesFactory, translate: SkVector?): Boolean {
    TODO("Implement isCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> makeVertices(const SkPath& path, const SkMatrix& ctm,
   *                                    SkVector* translate) const {
   *         SkPoint3 zParams = SkPoint3::Make(0, 0, fOccluderHeight);
   *         // pick a canonical place to generate shadow
   *         SkMatrix noTrans(ctm);
   *         if (!ctm.hasPerspective()) {
   *             noTrans[SkMatrix::kMTransX] = 0;
   *             noTrans[SkMatrix::kMTransY] = 0;
   *         }
   *         *translate = fOffset;
   *         return SkShadowTessellator::MakeAmbient(path, noTrans, zParams, fTransparent);
   *     }
   * ```
   */
  public fun makeVertices(
    path: SkPath,
    ctm: SkMatrix,
    translate: SkVector?,
  ): SkSp<SkVertices> {
    TODO("Implement makeVertices")
  }
}
