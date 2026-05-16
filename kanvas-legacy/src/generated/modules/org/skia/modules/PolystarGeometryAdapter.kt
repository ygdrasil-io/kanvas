package org.skia.modules

import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class PolystarGeometryAdapter final :
 *         public DiscardableAdapterBase<PolystarGeometryAdapter, sksg::Path> {
 * public:
 *     enum class Type {
 *         kStar, kPoly,
 *     };
 *
 *     PolystarGeometryAdapter(const skjson::ObjectValue& jstar,
 *                             const AnimationBuilder* abuilder, Type t)
 *         : fType(t) {
 *         this->bind(*abuilder, jstar["pt"], fPointCount    );
 *         this->bind(*abuilder, jstar["p" ], fPosition      );
 *         this->bind(*abuilder, jstar["r" ], fRotation      );
 *         this->bind(*abuilder, jstar["ir"], fInnerRadius   );
 *         this->bind(*abuilder, jstar["or"], fOuterRadius   );
 *         this->bind(*abuilder, jstar["is"], fInnerRoundness);
 *         this->bind(*abuilder, jstar["os"], fOuterRoundness);
 *     }
 *
 * private:
 *     void onSync() override {
 *         static constexpr int kMaxPointCount = 100000;
 *         const auto count = SkToUInt(SkTPin(SkScalarRoundToInt(fPointCount), 0, kMaxPointCount));
 *         const auto arc   = sk_ieee_float_divide(SK_ScalarPI * 2, count);
 *
 *         const auto pt_on_circle = [](const SkV2& c, SkScalar r, SkScalar a) {
 *             return SkPoint::Make(c.x + r * std::cos(a),
 *                                  c.y + r * std::sin(a));
 *         };
 *
 *         // TODO: inner/outer "roundness"?
 *
 *         SkPathBuilder poly;
 *
 *         auto angle = SkDegreesToRadians(fRotation - 90);
 *         poly.moveTo(pt_on_circle(fPosition, fOuterRadius, angle));
 *         poly.incReserve(fType == Type::kStar ? count * 2 : count);
 *
 *         for (unsigned i = 0; i < count; ++i) {
 *             if (fType == Type::kStar) {
 *                 poly.lineTo(pt_on_circle(fPosition, fInnerRadius, angle + arc * 0.5f));
 *             }
 *             angle += arc;
 *             poly.lineTo(pt_on_circle(fPosition, fOuterRadius, angle));
 *         }
 *
 *         poly.close();
 *         this->node()->setPath(poly.detach());
 *     }
 *
 *     const Type fType;
 *
 *     Vec2Value   fPosition       = {0,0};
 *     ScalarValue fPointCount     = 0,
 *                 fRotation       = 0,
 *                 fInnerRadius    = 0,
 *                 fOuterRadius    = 0,
 *                 fInnerRoundness = 0,
 *                 fOuterRoundness = 0;
 * }
 * ```
 */
public class PolystarGeometryAdapter public constructor(
  jstar: ObjectValue,
  abuilder: AnimationBuilder?,
  t: Type,
) : DiscardableAdapterBase(),
    PolystarGeometryAdapter,
    Path {
  /**
   * C++ original:
   * ```cpp
   * const Type fType
   * ```
   */
  private val fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fPosition       = {0,0}
   * ```
   */
  private var fPosition: Vec2Value = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0
   * ```
   */
  private var fPointCount: ScalarValue = TODO("Initialize fPointCount")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0,
   *                 fRotation       = 0
   * ```
   */
  private var fRotation: ScalarValue = TODO("Initialize fRotation")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0,
   *                 fRotation       = 0,
   *                 fInnerRadius    = 0
   * ```
   */
  private var fInnerRadius: ScalarValue = TODO("Initialize fInnerRadius")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0,
   *                 fRotation       = 0,
   *                 fInnerRadius    = 0,
   *                 fOuterRadius    = 0
   * ```
   */
  private var fOuterRadius: ScalarValue = TODO("Initialize fOuterRadius")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0,
   *                 fRotation       = 0,
   *                 fInnerRadius    = 0,
   *                 fOuterRadius    = 0,
   *                 fInnerRoundness = 0
   * ```
   */
  private var fInnerRoundness: ScalarValue = TODO("Initialize fInnerRoundness")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fPointCount     = 0,
   *                 fRotation       = 0,
   *                 fInnerRadius    = 0,
   *                 fOuterRadius    = 0,
   *                 fInnerRoundness = 0,
   *                 fOuterRoundness = 0
   * ```
   */
  private var fOuterRoundness: ScalarValue = TODO("Initialize fOuterRoundness")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         static constexpr int kMaxPointCount = 100000;
   *         const auto count = SkToUInt(SkTPin(SkScalarRoundToInt(fPointCount), 0, kMaxPointCount));
   *         const auto arc   = sk_ieee_float_divide(SK_ScalarPI * 2, count);
   *
   *         const auto pt_on_circle = [](const SkV2& c, SkScalar r, SkScalar a) {
   *             return SkPoint::Make(c.x + r * std::cos(a),
   *                                  c.y + r * std::sin(a));
   *         };
   *
   *         // TODO: inner/outer "roundness"?
   *
   *         SkPathBuilder poly;
   *
   *         auto angle = SkDegreesToRadians(fRotation - 90);
   *         poly.moveTo(pt_on_circle(fPosition, fOuterRadius, angle));
   *         poly.incReserve(fType == Type::kStar ? count * 2 : count);
   *
   *         for (unsigned i = 0; i < count; ++i) {
   *             if (fType == Type::kStar) {
   *                 poly.lineTo(pt_on_circle(fPosition, fInnerRadius, angle + arc * 0.5f));
   *             }
   *             angle += arc;
   *             poly.lineTo(pt_on_circle(fPosition, fOuterRadius, angle));
   *         }
   *
   *         poly.close();
   *         this->node()->setPath(poly.detach());
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public enum class Type {
    kStar,
    kPoly,
  }
}
