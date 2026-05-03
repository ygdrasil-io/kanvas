package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class FractalNoiseAdapter final : public DiscardableAdapterBase<FractalNoiseAdapter,
 *                                                                 FractalNoiseNode> {
 * public:
 *     FractalNoiseAdapter(const skjson::ArrayValue& jprops,
 *                         const AnimationBuilder* abuilder,
 *                         sk_sp<FractalNoiseNode> node)
 *         : INHERITED(std::move(node))
 *     {
 *         EffectBinder(jprops, *abuilder, this)
 *             .bind( 0, fFractalType     )
 *             .bind( 1, fNoiseType       )
 *             .bind( 2, fInvert          )
 *             .bind( 3, fContrast        )
 *             .bind( 4, fBrightness      )
 *              // 5 -- overflow
 *              // 6 -- transform begin-group
 *             .bind( 7, fRotation        )
 *             .bind( 8, fUniformScaling  )
 *             .bind( 9, fScale           )
 *             .bind(10, fScaleWidth      )
 *             .bind(11, fScaleHeight     )
 *             .bind(12, fOffset          )
 *              // 13 -- TODO: perspective offset
 *              // 14 -- transform end-group
 *             .bind(15, fComplexity      )
 *              // 16 -- sub settings begin-group
 *             .bind(17, fSubInfluence    )
 *             .bind(18, fSubScale        )
 *             .bind(19, fSubRotation     )
 *             .bind(20, fSubOffset       )
 *              // 21 -- center subscale
 *              // 22 -- sub settings end-group
 *             .bind(23, fEvolution       )
 *              // 24 -- evolution options begin-group
 *             .bind(25, fCycleEvolution  )
 *             .bind(26, fCycleRevolutions)
 *             .bind(27, fRandomSeed      )
 *              // 28 -- evolution options end-group
 *             .bind(29, fOpacity         );
 *             // 30 -- TODO: blending mode
 *     }
 *
 * private:
 *     std::tuple<SkV2, float> noise() const {
 *         // Constant chosen to visually match AE's evolution rate.
 *         static constexpr auto kEvolutionScale = 0.25f;
 *
 *         // Evolution inputs:
 *         //
 *         //   * evolution         - main evolution control (degrees)
 *         //   * cycle evolution   - flag controlling whether evolution cycles
 *         //   * cycle revolutions - number of revolutions after which evolution cycles (period)
 *         //   * random seed       - determines an arbitrary starting plane (evolution offset)
 *         //
 *         // The shader uses evolution floor/ceil to select two noise planes, and the fractional part
 *         // to interpolate between the two -> in order to wrap around smoothly, the cycle/period
 *         // must be integral.
 *         const float
 *             evo_rad = SkDegreesToRadians(fEvolution),
 *             rev_rad = std::max(fCycleRevolutions, 1.0f)*SK_FloatPI*2,
 *             cycle   = fCycleEvolution
 *                           ? SkScalarRoundToScalar(rev_rad*kEvolutionScale)
 *                           : SK_ScalarMax,
 *             // Adjust scale when cycling to ensure an integral period (post scaling).
 *             scale   = fCycleEvolution
 *                           ? cycle/rev_rad
 *                           : kEvolutionScale,
 *             offset  = SkRandom(static_cast<uint32_t>(fRandomSeed)).nextRangeU(0, 100),
 *             evo     = evo_rad*scale,
 *             evo_    = std::floor(evo),
 *             weight  = evo - evo_;
 *
 *         // We want the GLSL mod() flavor.
 *         auto glsl_mod = [](float x, float y) {
 *             return x - y*std::floor(x/y);
 *         };
 *
 *         const SkV2 noise_planes = {
 *             glsl_mod(evo_ + 0, cycle) + offset,
 *             glsl_mod(evo_ + 1, cycle) + offset,
 *         };
 *
 *         return std::make_tuple(noise_planes, weight);
 *     }
 *
 *     SkMatrix shaderMatrix() const {
 *         static constexpr float kGridSize = 64;
 *
 *         const auto scale = (SkScalarRoundToInt(fUniformScaling) == 1)
 *                 ? SkV2{fScale, fScale}
 *                 : SkV2{fScaleWidth, fScaleHeight};
 *
 *         return SkMatrix::Translate(fOffset.x, fOffset.y)
 *              * SkMatrix::Scale(SkTPin(scale.x, 1.0f, 10000.0f) * 0.01f,
 *                                SkTPin(scale.y, 1.0f, 10000.0f) * 0.01f)
 *              * SkMatrix::RotateDeg(fRotation)
 *              * SkMatrix::Scale(kGridSize, kGridSize);
 *     }
 *
 *     SkMatrix subMatrix() const {
 *         const auto scale = 100 / SkTPin(fSubScale, 10.0f, 10000.0f);
 *
 *         return SkMatrix::Translate(-fSubOffset.x * 0.01f, -fSubOffset.y * 0.01f)
 *              * SkMatrix::RotateDeg(-fSubRotation)
 *              * SkMatrix::Scale(scale, scale);
 *     }
 *
 *     NoiseFilter noiseFilter() const {
 *         switch (SkScalarRoundToInt(fNoiseType)) {
 *             case 1:  return NoiseFilter::kNearest;
 *             case 2:  return NoiseFilter::kLinear;
 *             default: return NoiseFilter::kSoftLinear;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     NoiseFractal noiseFractal() const {
 *         switch (SkScalarRoundToInt(fFractalType)) {
 *             case 1:  return NoiseFractal::kBasic;
 *             case 3:  return NoiseFractal::kTurbulentSmooth;
 *             case 4:  return NoiseFractal::kTurbulentBasic;
 *             default: return NoiseFractal::kTurbulentSharp;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     void onSync() override {
 *         const auto& n = this->node();
 *
 *         const auto [noise_planes, noise_weight] = this->noise();
 *
 *         n->setOctaves(SkTPin(fComplexity, 1.0f, 20.0f));
 *         n->setPersistence(SkTPin(fSubInfluence * 0.01f, 0.0f, 100.0f));
 *         n->setNoisePlanes(noise_planes);
 *         n->setNoiseWeight(noise_weight);
 *         n->setNoiseFilter(this->noiseFilter());
 *         n->setNoiseFractal(this->noiseFractal());
 *         n->setMatrix(this->shaderMatrix());
 *         n->setSubMatrix(this->subMatrix());
 *     }
 *
 *     Vec2Value   fOffset           = {0,0},
 *                 fSubOffset        = {0,0};
 *
 *     ScalarValue fFractalType      =     0,
 *                 fNoiseType        =     0,
 *
 *                 fRotation         =     0,
 *                 fUniformScaling   =     0,
 *                 fScale            =   100,  // used when uniform scaling is selected
 *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
 *                 fScaleHeight      =   100,  // ^
 *
 *                 fComplexity       =     1,
 *                 fSubInfluence     =   100,
 *                 fSubScale         =    50,
 *                 fSubRotation      =     0,
 *
 *                 fEvolution        =     0,
 *                 fCycleEvolution   =     0,
 *                 fCycleRevolutions =     0,
 *                 fRandomSeed       =     0,
 *
 *                 fOpacity          =   100, // TODO
 *                 fInvert           =     0, // TODO
 *                 fContrast         =   100, // TODO
 *                 fBrightness       =     0; // TODO
 *
 *     using INHERITED = DiscardableAdapterBase<FractalNoiseAdapter, FractalNoiseNode>;
 * }
 * ```
 */
public class FractalNoiseAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder?,
  node: SkSp<FractalNoiseNode>,
) : DiscardableAdapterBase(TODO()),
    FractalNoiseAdapter,
    FractalNoiseNode {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fOffset           = {0,0}
   * ```
   */
  private var fOffset: Vec2Value = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fOffset           = {0,0},
   *                 fSubOffset        = {0,0}
   * ```
   */
  private var fSubOffset: Vec2Value = TODO("Initialize fSubOffset")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0
   * ```
   */
  private var fFractalType: ScalarValue = TODO("Initialize fFractalType")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0
   * ```
   */
  private var fNoiseType: ScalarValue = TODO("Initialize fNoiseType")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0
   * ```
   */
  private var fRotation: ScalarValue = TODO("Initialize fRotation")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0
   * ```
   */
  private var fUniformScaling: ScalarValue = TODO("Initialize fUniformScaling")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100
   * ```
   */
  private var fScale: ScalarValue = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100
   * ```
   */
  private var fScaleWidth: ScalarValue = TODO("Initialize fScaleWidth")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100
   * ```
   */
  private var fScaleHeight: ScalarValue = TODO("Initialize fScaleHeight")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1
   * ```
   */
  private var fComplexity: ScalarValue = TODO("Initialize fComplexity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100
   * ```
   */
  private var fSubInfluence: ScalarValue = TODO("Initialize fSubInfluence")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50
   * ```
   */
  private var fSubScale: ScalarValue = TODO("Initialize fSubScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0
   * ```
   */
  private var fSubRotation: ScalarValue = TODO("Initialize fSubRotation")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0
   * ```
   */
  private var fEvolution: ScalarValue = TODO("Initialize fEvolution")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0
   * ```
   */
  private var fCycleEvolution: ScalarValue = TODO("Initialize fCycleEvolution")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0
   * ```
   */
  private var fCycleRevolutions: ScalarValue = TODO("Initialize fCycleRevolutions")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0,
   *                 fRandomSeed       =     0
   * ```
   */
  private var fRandomSeed: ScalarValue = TODO("Initialize fRandomSeed")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0,
   *                 fRandomSeed       =     0,
   *
   *                 fOpacity          =   100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0,
   *                 fRandomSeed       =     0,
   *
   *                 fOpacity          =   100, // TODO
   *                 fInvert           =     0
   * ```
   */
  private var fInvert: ScalarValue = TODO("Initialize fInvert")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0,
   *                 fRandomSeed       =     0,
   *
   *                 fOpacity          =   100, // TODO
   *                 fInvert           =     0, // TODO
   *                 fContrast         =   100
   * ```
   */
  private var fContrast: ScalarValue = TODO("Initialize fContrast")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fFractalType      =     0,
   *                 fNoiseType        =     0,
   *
   *                 fRotation         =     0,
   *                 fUniformScaling   =     0,
   *                 fScale            =   100,  // used when uniform scaling is selected
   *                 fScaleWidth       =   100,  // used when uniform scaling is not selected
   *                 fScaleHeight      =   100,  // ^
   *
   *                 fComplexity       =     1,
   *                 fSubInfluence     =   100,
   *                 fSubScale         =    50,
   *                 fSubRotation      =     0,
   *
   *                 fEvolution        =     0,
   *                 fCycleEvolution   =     0,
   *                 fCycleRevolutions =     0,
   *                 fRandomSeed       =     0,
   *
   *                 fOpacity          =   100, // TODO
   *                 fInvert           =     0, // TODO
   *                 fContrast         =   100, // TODO
   *                 fBrightness       =     0
   * ```
   */
  private var fBrightness: ScalarValue = TODO("Initialize fBrightness")

  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkV2, float> noise() const {
   *         // Constant chosen to visually match AE's evolution rate.
   *         static constexpr auto kEvolutionScale = 0.25f;
   *
   *         // Evolution inputs:
   *         //
   *         //   * evolution         - main evolution control (degrees)
   *         //   * cycle evolution   - flag controlling whether evolution cycles
   *         //   * cycle revolutions - number of revolutions after which evolution cycles (period)
   *         //   * random seed       - determines an arbitrary starting plane (evolution offset)
   *         //
   *         // The shader uses evolution floor/ceil to select two noise planes, and the fractional part
   *         // to interpolate between the two -> in order to wrap around smoothly, the cycle/period
   *         // must be integral.
   *         const float
   *             evo_rad = SkDegreesToRadians(fEvolution),
   *             rev_rad = std::max(fCycleRevolutions, 1.0f)*SK_FloatPI*2,
   *             cycle   = fCycleEvolution
   *                           ? SkScalarRoundToScalar(rev_rad*kEvolutionScale)
   *                           : SK_ScalarMax,
   *             // Adjust scale when cycling to ensure an integral period (post scaling).
   *             scale   = fCycleEvolution
   *                           ? cycle/rev_rad
   *                           : kEvolutionScale,
   *             offset  = SkRandom(static_cast<uint32_t>(fRandomSeed)).nextRangeU(0, 100),
   *             evo     = evo_rad*scale,
   *             evo_    = std::floor(evo),
   *             weight  = evo - evo_;
   *
   *         // We want the GLSL mod() flavor.
   *         auto glsl_mod = [](float x, float y) {
   *             return x - y*std::floor(x/y);
   *         };
   *
   *         const SkV2 noise_planes = {
   *             glsl_mod(evo_ + 0, cycle) + offset,
   *             glsl_mod(evo_ + 1, cycle) + offset,
   *         };
   *
   *         return std::make_tuple(noise_planes, weight);
   *     }
   * ```
   */
  public override fun noise(): Int {
    TODO("Implement noise")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix shaderMatrix() const {
   *         static constexpr float kGridSize = 64;
   *
   *         const auto scale = (SkScalarRoundToInt(fUniformScaling) == 1)
   *                 ? SkV2{fScale, fScale}
   *                 : SkV2{fScaleWidth, fScaleHeight};
   *
   *         return SkMatrix::Translate(fOffset.x, fOffset.y)
   *              * SkMatrix::Scale(SkTPin(scale.x, 1.0f, 10000.0f) * 0.01f,
   *                                SkTPin(scale.y, 1.0f, 10000.0f) * 0.01f)
   *              * SkMatrix::RotateDeg(fRotation)
   *              * SkMatrix::Scale(kGridSize, kGridSize);
   *     }
   * ```
   */
  public override fun shaderMatrix(): SkMatrix {
    TODO("Implement shaderMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMatrix subMatrix() const {
   *         const auto scale = 100 / SkTPin(fSubScale, 10.0f, 10000.0f);
   *
   *         return SkMatrix::Translate(-fSubOffset.x * 0.01f, -fSubOffset.y * 0.01f)
   *              * SkMatrix::RotateDeg(-fSubRotation)
   *              * SkMatrix::Scale(scale, scale);
   *     }
   * ```
   */
  public override fun subMatrix(): SkMatrix {
    TODO("Implement subMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * NoiseFilter noiseFilter() const {
   *         switch (SkScalarRoundToInt(fNoiseType)) {
   *             case 1:  return NoiseFilter::kNearest;
   *             case 2:  return NoiseFilter::kLinear;
   *             default: return NoiseFilter::kSoftLinear;
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public override fun noiseFilter(): NoiseFilter {
    TODO("Implement noiseFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * NoiseFractal noiseFractal() const {
   *         switch (SkScalarRoundToInt(fFractalType)) {
   *             case 1:  return NoiseFractal::kBasic;
   *             case 3:  return NoiseFractal::kTurbulentSmooth;
   *             case 4:  return NoiseFractal::kTurbulentBasic;
   *             default: return NoiseFractal::kTurbulentSharp;
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  public override fun noiseFractal(): NoiseFractal {
    TODO("Implement noiseFractal")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto& n = this->node();
   *
   *         const auto [noise_planes, noise_weight] = this->noise();
   *
   *         n->setOctaves(SkTPin(fComplexity, 1.0f, 20.0f));
   *         n->setPersistence(SkTPin(fSubInfluence * 0.01f, 0.0f, 100.0f));
   *         n->setNoisePlanes(noise_planes);
   *         n->setNoiseWeight(noise_weight);
   *         n->setNoiseFilter(this->noiseFilter());
   *         n->setNoiseFractal(this->noiseFractal());
   *         n->setMatrix(this->shaderMatrix());
   *         n->setSubMatrix(this->subMatrix());
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
