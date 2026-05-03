package org.skia.utils

import kotlin.Boolean
import org.skia.core.SkVertices
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SpotVerticesFactory {
 *     enum class OccluderType {
 *         // The umbra cannot be dropped out because either the occluder is not opaque,
 *         // or the center of the umbra is visible. Uses point light.
 *         kPointTransparent,
 *         // The umbra can be dropped where it is occluded. Uses point light.
 *         kPointOpaquePartialUmbra,
 *         // It is known that the entire umbra is occluded. Uses point light.
 *         kPointOpaqueNoUmbra,
 *         // Uses directional light.
 *         kDirectional,
 *         // The umbra can't be dropped out. Uses directional light.
 *         kDirectionalTransparent,
 *     };
 *
 *     SkVector fOffset;
 *     SkPoint  fLocalCenter;
 *     SkScalar fOccluderHeight = SK_ScalarNaN; // NaN so that isCompatible will fail until init'ed.
 *     SkPoint3 fDevLightPos;
 *     SkScalar fLightRadius;
 *     OccluderType fOccluderType;
 *
 *     bool isCompatible(const SpotVerticesFactory& that, SkVector* translate) const {
 *         if (fOccluderHeight != that.fOccluderHeight || fDevLightPos.fZ != that.fDevLightPos.fZ ||
 *             fLightRadius != that.fLightRadius || fOccluderType != that.fOccluderType) {
 *             return false;
 *         }
 *         switch (fOccluderType) {
 *             case OccluderType::kPointTransparent:
 *             case OccluderType::kPointOpaqueNoUmbra:
 *                 // 'this' and 'that' will either both have no umbra removed or both have all the
 *                 // umbra removed.
 *                 *translate = that.fOffset;
 *                 return true;
 *             case OccluderType::kPointOpaquePartialUmbra:
 *                 // In this case we partially remove the umbra differently for 'this' and 'that'
 *                 // if the offsets don't match.
 *                 if (fOffset == that.fOffset) {
 *                     translate->set(0, 0);
 *                     return true;
 *                 }
 *                 return false;
 *             case OccluderType::kDirectional:
 *             case OccluderType::kDirectionalTransparent:
 *                 *translate = that.fOffset - fOffset;
 *                 return true;
 *         }
 *         SK_ABORT("Uninitialized occluder type?");
 *     }
 *
 *     sk_sp<SkVertices> makeVertices(const SkPath& path, const SkMatrix& ctm,
 *                                    SkVector* translate) const {
 *         bool transparent = fOccluderType == OccluderType::kPointTransparent ||
 *                            fOccluderType == OccluderType::kDirectionalTransparent;
 *         bool directional = fOccluderType == OccluderType::kDirectional ||
 *                            fOccluderType == OccluderType::kDirectionalTransparent;
 *         SkPoint3 zParams = SkPoint3::Make(0, 0, fOccluderHeight);
 *         if (directional) {
 *             translate->set(0, 0);
 *             return SkShadowTessellator::MakeSpot(path, ctm, zParams, fDevLightPos, fLightRadius,
 *                                                  transparent, true);
 *         } else if (ctm.hasPerspective() || OccluderType::kPointOpaquePartialUmbra == fOccluderType) {
 *             translate->set(0, 0);
 *             return SkShadowTessellator::MakeSpot(path, ctm, zParams, fDevLightPos, fLightRadius,
 *                                                  transparent, false);
 *         } else {
 *             // pick a canonical place to generate shadow, with light centered over path
 *             SkMatrix noTrans(ctm);
 *             noTrans[SkMatrix::kMTransX] = 0;
 *             noTrans[SkMatrix::kMTransY] = 0;
 *             SkPoint devCenter(fLocalCenter);
 *             devCenter = noTrans.mapPoint(devCenter);
 *             SkPoint3 centerLightPos = SkPoint3::Make(devCenter.fX, devCenter.fY, fDevLightPos.fZ);
 *             *translate = fOffset;
 *             return SkShadowTessellator::MakeSpot(path, noTrans, zParams,
 *                                                  centerLightPos, fLightRadius, transparent, false);
 *         }
 *     }
 * }
 * ```
 */
public data class SpotVerticesFactory public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkVector fOffset
   * ```
   */
  public var fOffset: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fLocalCenter
   * ```
   */
  public var fLocalCenter: SkPoint,
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
   * SkPoint3 fDevLightPos
   * ```
   */
  public var fDevLightPos: SkPoint3,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fLightRadius
   * ```
   */
  public var fLightRadius: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * OccluderType fOccluderType
   * ```
   */
  public var fOccluderType: OccluderType,
) {
  /**
   * C++ original:
   * ```cpp
   * bool isCompatible(const SpotVerticesFactory& that, SkVector* translate) const {
   *         if (fOccluderHeight != that.fOccluderHeight || fDevLightPos.fZ != that.fDevLightPos.fZ ||
   *             fLightRadius != that.fLightRadius || fOccluderType != that.fOccluderType) {
   *             return false;
   *         }
   *         switch (fOccluderType) {
   *             case OccluderType::kPointTransparent:
   *             case OccluderType::kPointOpaqueNoUmbra:
   *                 // 'this' and 'that' will either both have no umbra removed or both have all the
   *                 // umbra removed.
   *                 *translate = that.fOffset;
   *                 return true;
   *             case OccluderType::kPointOpaquePartialUmbra:
   *                 // In this case we partially remove the umbra differently for 'this' and 'that'
   *                 // if the offsets don't match.
   *                 if (fOffset == that.fOffset) {
   *                     translate->set(0, 0);
   *                     return true;
   *                 }
   *                 return false;
   *             case OccluderType::kDirectional:
   *             case OccluderType::kDirectionalTransparent:
   *                 *translate = that.fOffset - fOffset;
   *                 return true;
   *         }
   *         SK_ABORT("Uninitialized occluder type?");
   *     }
   * ```
   */
  public fun isCompatible(that: SpotVerticesFactory, translate: SkVector?): Boolean {
    TODO("Implement isCompatible")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkVertices> makeVertices(const SkPath& path, const SkMatrix& ctm,
   *                                    SkVector* translate) const {
   *         bool transparent = fOccluderType == OccluderType::kPointTransparent ||
   *                            fOccluderType == OccluderType::kDirectionalTransparent;
   *         bool directional = fOccluderType == OccluderType::kDirectional ||
   *                            fOccluderType == OccluderType::kDirectionalTransparent;
   *         SkPoint3 zParams = SkPoint3::Make(0, 0, fOccluderHeight);
   *         if (directional) {
   *             translate->set(0, 0);
   *             return SkShadowTessellator::MakeSpot(path, ctm, zParams, fDevLightPos, fLightRadius,
   *                                                  transparent, true);
   *         } else if (ctm.hasPerspective() || OccluderType::kPointOpaquePartialUmbra == fOccluderType) {
   *             translate->set(0, 0);
   *             return SkShadowTessellator::MakeSpot(path, ctm, zParams, fDevLightPos, fLightRadius,
   *                                                  transparent, false);
   *         } else {
   *             // pick a canonical place to generate shadow, with light centered over path
   *             SkMatrix noTrans(ctm);
   *             noTrans[SkMatrix::kMTransX] = 0;
   *             noTrans[SkMatrix::kMTransY] = 0;
   *             SkPoint devCenter(fLocalCenter);
   *             devCenter = noTrans.mapPoint(devCenter);
   *             SkPoint3 centerLightPos = SkPoint3::Make(devCenter.fX, devCenter.fY, fDevLightPos.fZ);
   *             *translate = fOffset;
   *             return SkShadowTessellator::MakeSpot(path, noTrans, zParams,
   *                                                  centerLightPos, fLightRadius, transparent, false);
   *         }
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

  public enum class OccluderType {
    kPointTransparent,
    kPointOpaquePartialUmbra,
    kPointOpaqueNoUmbra,
    kDirectional,
    kDirectionalTransparent,
  }
}
