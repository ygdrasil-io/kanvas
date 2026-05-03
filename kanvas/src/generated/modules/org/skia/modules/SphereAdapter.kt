package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class SphereAdapter final : public DiscardableAdapterBase<SphereAdapter, SphereNode> {
 * public:
 *     SphereAdapter(const skjson::ArrayValue& jprops,
 *                   const AnimationBuilder* abuilder,
 *                   sk_sp<SphereNode> node)
 *         : INHERITED(std::move(node))
 *     {
 *         enum : size_t {
 *             //      kRotGrp_Index =  0,
 *                       kRotX_Index =  1,
 *                       kRotY_Index =  2,
 *                       kRotZ_Index =  3,
 *                   kRotOrder_Index =  4,
 *             // ???                =  5,
 *                     kRadius_Index =  6,
 *                     kOffset_Index =  7,
 *                     kRender_Index =  8,
 *
 *             //       kLight_Index =  9,
 *             kLightIntensity_Index = 10,
 *                 kLightColor_Index = 11,
 *                kLightHeight_Index = 12,
 *             kLightDirection_Index = 13,
 *             // ???                = 14,
 *             //     kShading_Index = 15,
 *                    kAmbient_Index = 16,
 *                    kDiffuse_Index = 17,
 *                   kSpecular_Index = 18,
 *                  kRoughness_Index = 19,
 *         };
 *
 *         EffectBinder(jprops, *abuilder, this)
 *             .bind(  kOffset_Index, fOffset  )
 *             .bind(  kRadius_Index, fRadius  )
 *             .bind(    kRotX_Index, fRotX    )
 *             .bind(    kRotY_Index, fRotY    )
 *             .bind(    kRotZ_Index, fRotZ    )
 *             .bind(kRotOrder_Index, fRotOrder)
 *             .bind(  kRender_Index, fRender  )
 *
 *             .bind(kLightIntensity_Index, fLightIntensity)
 *             .bind(    kLightColor_Index, fLightColor    )
 *             .bind(   kLightHeight_Index, fLightHeight   )
 *             .bind(kLightDirection_Index, fLightDirection)
 *             .bind(       kAmbient_Index, fAmbient       )
 *             .bind(       kDiffuse_Index, fDiffuse       )
 *             .bind(      kSpecular_Index, fSpecular      )
 *             .bind(     kRoughness_Index, fRoughness     );
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto side = [](ScalarValue s) {
 *             switch (SkScalarRoundToInt(s)) {
 *                 case 1:  return SphereNode::RenderSide::kFull;
 *                 case 2:  return SphereNode::RenderSide::kOutside;
 *                 case 3:
 *                 default: return SphereNode::RenderSide::kInside;
 *             }
 *             SkUNREACHABLE;
 *         };
 *
 *         const auto rotation = [](ScalarValue order,
 *                                  ScalarValue x, ScalarValue y, ScalarValue z) {
 *             const SkM44 rx = SkM44::Rotate({1,0,0}, SkDegreesToRadians( x)),
 *                         ry = SkM44::Rotate({0,1,0}, SkDegreesToRadians( y)),
 *                         rz = SkM44::Rotate({0,0,1}, SkDegreesToRadians(-z));
 *
 *             switch (SkScalarRoundToInt(order)) {
 *                 case 1: return rx * ry * rz;
 *                 case 2: return rx * rz * ry;
 *                 case 3: return ry * rx * rz;
 *                 case 4: return ry * rz * rx;
 *                 case 5: return rz * rx * ry;
 *                 case 6:
 *                default: return rz * ry * rx;
 *             }
 *             SkUNREACHABLE;
 *         };
 *
 *         const auto light_vec = [](float height, float direction) {
 *             float z = std::sin(height * SK_ScalarPI / 2),
 *                   r = std::sqrt(1 - z*z),
 *                   x = std::cos(direction) * r,
 *                   y = std::sin(direction) * r;
 *
 *             return SkV3{x,y,z};
 *         };
 *
 *         const auto& sph = this->node();
 *
 *         sph->setCenter({fOffset.x, fOffset.y});
 *         sph->setRadius(fRadius);
 *         sph->setSide(side(fRender));
 *         sph->setRotation(rotation(fRotOrder, fRotX, fRotY, fRotZ));
 *
 *         sph->setAmbientLight (SkTPin(fAmbient * 0.01f, 0.0f, 2.0f));
 *
 *         const auto intensity = SkTPin(fLightIntensity * 0.01f,  0.0f, 10.0f);
 *         sph->setDiffuseLight (SkTPin(fDiffuse * 0.01f, 0.0f, 1.0f) * intensity);
 *         sph->setSpecularLight(SkTPin(fSpecular* 0.01f, 0.0f, 1.0f) * intensity);
 *
 *         sph->setLightVec(light_vec(
 *             SkTPin(fLightHeight    * 0.01f, -1.0f,  1.0f),
 *             SkDegreesToRadians(fLightDirection - 90)
 *         ));
 *
 *         const auto lc = static_cast<SkColor4f>(fLightColor);
 *         sph->setLightColor({lc.fR, lc.fG, lc.fB});
 *
 *         sph->setSpecularExp(1/SkTPin(fRoughness, 0.001f, 0.5f));
 *     }
 *
 *     Vec2Value   fOffset   = {0,0};
 *     ScalarValue fRadius   = 0,
 *                 fRotX     = 0,
 *                 fRotY     = 0,
 *                 fRotZ     = 0,
 *                 fRotOrder = 1,
 *                 fRender   = 1;
 *
 *     ColorValue  fLightColor;
 *     ScalarValue fLightIntensity =   0,
 *                 fLightHeight    =   0,
 *                 fLightDirection =   0,
 *                 fAmbient        = 100,
 *                 fDiffuse        =   0,
 *                 fSpecular       =   0,
 *                 fRoughness      =   0.5f;
 *
 *     using INHERITED = DiscardableAdapterBase<SphereAdapter, SphereNode>;
 * }
 * ```
 */
public class SphereAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder?,
  node: SkSp<SphereNode>,
) : DiscardableAdapterBase(TODO()),
    SphereAdapter,
    SphereNode {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fOffset   = {0,0}
   * ```
   */
  private var fOffset: Vec2Value = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0
   * ```
   */
  private var fRadius: ScalarValue = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0,
   *                 fRotX     = 0
   * ```
   */
  private var fRotX: ScalarValue = TODO("Initialize fRotX")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0,
   *                 fRotX     = 0,
   *                 fRotY     = 0
   * ```
   */
  private var fRotY: ScalarValue = TODO("Initialize fRotY")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0,
   *                 fRotX     = 0,
   *                 fRotY     = 0,
   *                 fRotZ     = 0
   * ```
   */
  private var fRotZ: ScalarValue = TODO("Initialize fRotZ")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0,
   *                 fRotX     = 0,
   *                 fRotY     = 0,
   *                 fRotZ     = 0,
   *                 fRotOrder = 1
   * ```
   */
  private var fRotOrder: ScalarValue = TODO("Initialize fRotOrder")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius   = 0,
   *                 fRotX     = 0,
   *                 fRotY     = 0,
   *                 fRotZ     = 0,
   *                 fRotOrder = 1,
   *                 fRender   = 1
   * ```
   */
  private var fRender: ScalarValue = TODO("Initialize fRender")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fLightColor
   * ```
   */
  private var fLightColor: ColorValue = TODO("Initialize fLightColor")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0
   * ```
   */
  private var fLightIntensity: ScalarValue = TODO("Initialize fLightIntensity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0
   * ```
   */
  private var fLightHeight: ScalarValue = TODO("Initialize fLightHeight")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0,
   *                 fLightDirection =   0
   * ```
   */
  private var fLightDirection: ScalarValue = TODO("Initialize fLightDirection")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0,
   *                 fLightDirection =   0,
   *                 fAmbient        = 100
   * ```
   */
  private var fAmbient: ScalarValue = TODO("Initialize fAmbient")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0,
   *                 fLightDirection =   0,
   *                 fAmbient        = 100,
   *                 fDiffuse        =   0
   * ```
   */
  private var fDiffuse: ScalarValue = TODO("Initialize fDiffuse")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0,
   *                 fLightDirection =   0,
   *                 fAmbient        = 100,
   *                 fDiffuse        =   0,
   *                 fSpecular       =   0
   * ```
   */
  private var fSpecular: ScalarValue = TODO("Initialize fSpecular")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLightIntensity =   0,
   *                 fLightHeight    =   0,
   *                 fLightDirection =   0,
   *                 fAmbient        = 100,
   *                 fDiffuse        =   0,
   *                 fSpecular       =   0,
   *                 fRoughness      =   0.5f
   * ```
   */
  private var fRoughness: ScalarValue = TODO("Initialize fRoughness")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto side = [](ScalarValue s) {
   *             switch (SkScalarRoundToInt(s)) {
   *                 case 1:  return SphereNode::RenderSide::kFull;
   *                 case 2:  return SphereNode::RenderSide::kOutside;
   *                 case 3:
   *                 default: return SphereNode::RenderSide::kInside;
   *             }
   *             SkUNREACHABLE;
   *         };
   *
   *         const auto rotation = [](ScalarValue order,
   *                                  ScalarValue x, ScalarValue y, ScalarValue z) {
   *             const SkM44 rx = SkM44::Rotate({1,0,0}, SkDegreesToRadians( x)),
   *                         ry = SkM44::Rotate({0,1,0}, SkDegreesToRadians( y)),
   *                         rz = SkM44::Rotate({0,0,1}, SkDegreesToRadians(-z));
   *
   *             switch (SkScalarRoundToInt(order)) {
   *                 case 1: return rx * ry * rz;
   *                 case 2: return rx * rz * ry;
   *                 case 3: return ry * rx * rz;
   *                 case 4: return ry * rz * rx;
   *                 case 5: return rz * rx * ry;
   *                 case 6:
   *                default: return rz * ry * rx;
   *             }
   *             SkUNREACHABLE;
   *         };
   *
   *         const auto light_vec = [](float height, float direction) {
   *             float z = std::sin(height * SK_ScalarPI / 2),
   *                   r = std::sqrt(1 - z*z),
   *                   x = std::cos(direction) * r,
   *                   y = std::sin(direction) * r;
   *
   *             return SkV3{x,y,z};
   *         };
   *
   *         const auto& sph = this->node();
   *
   *         sph->setCenter({fOffset.x, fOffset.y});
   *         sph->setRadius(fRadius);
   *         sph->setSide(side(fRender));
   *         sph->setRotation(rotation(fRotOrder, fRotX, fRotY, fRotZ));
   *
   *         sph->setAmbientLight (SkTPin(fAmbient * 0.01f, 0.0f, 2.0f));
   *
   *         const auto intensity = SkTPin(fLightIntensity * 0.01f,  0.0f, 10.0f);
   *         sph->setDiffuseLight (SkTPin(fDiffuse * 0.01f, 0.0f, 1.0f) * intensity);
   *         sph->setSpecularLight(SkTPin(fSpecular* 0.01f, 0.0f, 1.0f) * intensity);
   *
   *         sph->setLightVec(light_vec(
   *             SkTPin(fLightHeight    * 0.01f, -1.0f,  1.0f),
   *             SkDegreesToRadians(fLightDirection - 90)
   *         ));
   *
   *         const auto lc = static_cast<SkColor4f>(fLightColor);
   *         sph->setLightColor({lc.fR, lc.fG, lc.fB});
   *
   *         sph->setSpecularExp(1/SkTPin(fRoughness, 0.001f, 0.5f));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
