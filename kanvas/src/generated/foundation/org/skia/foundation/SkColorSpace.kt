package org.skia.foundation

import kotlin.Any
import kotlin.Boolean
import kotlin.FloatArray
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.modules.SkcmsICCProfile
import org.skia.modules.SkcmsMatrix3x3
import org.skia.modules.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorSpace : public SkNVRefCnt<SkColorSpace> {
 * public:
 *     /**
 *      *  Create the sRGB color space.
 *      */
 *     static sk_sp<SkColorSpace> MakeSRGB();
 *
 *     /**
 *      *  Colorspace with the sRGB primaries, but a linear (1.0) gamma.
 *      */
 *     static sk_sp<SkColorSpace> MakeSRGBLinear();
 *
 *     /**
 *      *  Create an SkColorSpace from a transfer function and a row-major 3x3 transformation to XYZ.
 *      */
 *     static sk_sp<SkColorSpace> MakeRGB(const skcms_TransferFunction& transferFn,
 *                                        const skcms_Matrix3x3& toXYZ);
 *
 *     /**
 *      *  Create an SkColorSpace from code points specified in Rec. ITU-T H.273.
 *      *  Null will be returned for invalid or unsupported combination of code
 *      *  points.
 *      *
 *      *  Parameters:
 *      *
 *      * - `color_primaries` identifies an entry in Rec. ITU-T H.273, Table 2.
 *      * - `transfer_characteristics` identifies an entry in Rec. ITU-T H.273, Table 3.
 *      *
 *      * `SkColorSpace` (and the underlying `skcms_ICCProfile`) only supports RGB
 *      * color spaces and therefore this function does not take a
 *      * `matrix_coefficients` parameter - the caller is expected to verify that
 *      * `matrix_coefficients` is `0`.
 *      *
 *      * Narrow range images are extremely rare - see
 *      * https://github.com/w3c/png/issues/312#issuecomment-2327349614.  Therefore
 *      * this function doesn't take a `video_full_range_flag` - the caller is
 *      * expected to verify that it is `1` (indicating a full range image).
 *      */
 *     static sk_sp<SkColorSpace> MakeCICP(SkNamedPrimaries::CicpId color_primaries,
 *                                         SkNamedTransferFn::CicpId transfer_characteristics);
 *
 *     /**
 *      *  Create an SkColorSpace from a parsed (skcms) ICC profile.
 *      */
 *     static sk_sp<SkColorSpace> Make(const skcms_ICCProfile&);
 *
 *     /**
 *      *  Convert this color space to an skcms ICC profile struct.
 *      */
 *     void toProfile(skcms_ICCProfile*) const;
 *
 *     /**
 *      *  Returns true if the color space gamma is near enough to be approximated as sRGB.
 *      */
 *     bool gammaCloseToSRGB() const;
 *
 *     /**
 *      *  Returns true if the color space gamma is linear.
 *      */
 *     bool gammaIsLinear() const;
 *
 *     /**
 *      *  Sets |fn| to the transfer function from this color space. Returns true if the transfer
 *      *  function can be represented as coefficients to the standard ICC 7-parameter equation.
 *      *  Returns false otherwise (eg, PQ, HLG).
 *      */
 *     bool isNumericalTransferFn(skcms_TransferFunction* fn) const;
 *
 *     /**
 *      *  Returns true and sets |toXYZD50|.
 *      */
 *     bool toXYZD50(skcms_Matrix3x3* toXYZD50) const;
 *
 *     /**
 *      *  Returns a hash of the gamut transformation to XYZ D50. Allows for fast equality checking
 *      *  of gamuts, at the (very small) risk of collision.
 *      */
 *     uint32_t toXYZD50Hash() const { return fToXYZD50Hash; }
 *
 *     /**
 *      *  Returns a color space with the same gamut as this one, but with a linear gamma.
 *      */
 *     sk_sp<SkColorSpace> makeLinearGamma() const;
 *
 *     /**
 *      *  Returns a color space with the same gamut as this one, but with the sRGB transfer
 *      *  function.
 *      */
 *     sk_sp<SkColorSpace> makeSRGBGamma() const;
 *
 *     /**
 *      *  Returns a color space with the same transfer function as this one, but with the primary
 *      *  colors rotated. In other words, this produces a new color space that maps RGB to GBR
 *      *  (when applied to a source), and maps RGB to BRG (when applied to a destination).
 *      *
 *      *  This is used for testing, to construct color spaces that have severe and testable behavior.
 *      */
 *     sk_sp<SkColorSpace> makeColorSpin() const;
 *
 *     /**
 *      *  Returns true if the color space is sRGB.
 *      *  Returns false otherwise.
 *      *
 *      *  This allows a little bit of tolerance, given that we might see small numerical error
 *      *  in some cases: converting ICC fixed point to float, converting white point to D50,
 *      *  rounding decisions on transfer function and matrix.
 *      *
 *      *  This does not consider a 2.2f exponential transfer function to be sRGB. While these
 *      *  functions are similar (and it is sometimes useful to consider them together), this
 *      *  function checks for logical equality.
 *      */
 *     bool isSRGB() const;
 *
 *     /**
 *      *  Returns a serialized representation of this color space.
 *      */
 *     sk_sp<SkData> serialize() const;
 *
 *     /**
 *      *  If |memory| is nullptr, returns the size required to serialize.
 *      *  Otherwise, serializes into |memory| and returns the size.
 *      */
 *     size_t writeToMemory(void* memory) const;
 *
 *     static sk_sp<SkColorSpace> Deserialize(const void* data, size_t length);
 *
 *     /**
 *      *  If both are null, we return true. If one is null and the other is not, we return false.
 *      *  If both are non-null, we do a deeper compare.
 *      */
 *     static bool Equals(const SkColorSpace*, const SkColorSpace*);
 *
 *     void       transferFn(float gabcdef[7]) const;  // DEPRECATED: Remove when webview usage is gone
 *     void       transferFn(skcms_TransferFunction* fn) const;
 *     void    invTransferFn(skcms_TransferFunction* fn) const;
 *     void gamutTransformTo(const SkColorSpace* dst, skcms_Matrix3x3* src_to_dst) const;
 *
 *     uint32_t transferFnHash() const { return fTransferFnHash; }
 *     uint64_t           hash() const { return (uint64_t)fTransferFnHash << 32 | fToXYZD50Hash; }
 *
 * private:
 *     friend class SkColorSpaceSingletonFactory;
 *
 *     SkColorSpace(const skcms_TransferFunction& transferFn, const skcms_Matrix3x3& toXYZ);
 *
 *     void computeLazyDstFields() const;
 *
 *     uint32_t                            fTransferFnHash;
 *     uint32_t                            fToXYZD50Hash;
 *
 *     skcms_TransferFunction              fTransferFn;
 *     skcms_Matrix3x3                     fToXYZD50;
 *
 *     mutable skcms_TransferFunction      fInvTransferFn;
 *     mutable skcms_Matrix3x3             fFromXYZD50;
 *     mutable SkOnce                      fLazyDstFieldsOnce;
 * }
 * ```
 */
public open class SkColorSpace public constructor(
  transferFn: SkcmsTransferFunction,
  toXYZ: SkcmsMatrix3x3,
) : SkNVRefCnt(),
    SkColorSpace {
  /**
   * C++ original:
   * ```cpp
   * uint32_t                            fTransferFnHash
   * ```
   */
  private var fTransferFnHash: UInt = TODO("Initialize fTransferFnHash")

  /**
   * C++ original:
   * ```cpp
   * uint32_t                            fToXYZD50Hash
   * ```
   */
  private var fToXYZD50Hash: UInt = TODO("Initialize fToXYZD50Hash")

  /**
   * C++ original:
   * ```cpp
   * skcms_TransferFunction              fTransferFn
   * ```
   */
  private var fTransferFn: Int = TODO("Initialize fTransferFn")

  /**
   * C++ original:
   * ```cpp
   * skcms_Matrix3x3                     fToXYZD50
   * ```
   */
  private var fToXYZD50: Int = TODO("Initialize fToXYZD50")

  /**
   * C++ original:
   * ```cpp
   * mutable skcms_TransferFunction      fInvTransferFn
   * ```
   */
  private var fInvTransferFn: Int = TODO("Initialize fInvTransferFn")

  /**
   * C++ original:
   * ```cpp
   * mutable skcms_Matrix3x3             fFromXYZD50
   * ```
   */
  private var fFromXYZD50: Int = TODO("Initialize fFromXYZD50")

  /**
   * C++ original:
   * ```cpp
   * mutable SkOnce                      fLazyDstFieldsOnce
   * ```
   */
  private var fLazyDstFieldsOnce: Int = TODO("Initialize fLazyDstFieldsOnce")

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpace::toProfile(skcms_ICCProfile* profile) const {
   *     skcms_Init               (profile);
   *     // TODO(https://issues.skia.org/issues/420956739): This value should only be
   *     // set for sRGB-ish transfer functions. All other values are invalid.
   *     skcms_SetTransferFunction(profile, &fTransferFn);
   *     skcms_SetXYZD50          (profile, &fToXYZD50);
   *
   *     switch (skcms_TransferFunction_getType(&fTransferFn)) {
   *         case skcms_TFType_PQ:
   *         case skcms_TFType_PQish:
   *             profile->has_CICP = true;
   *             profile->CICP.transfer_characteristics =
   *                 static_cast<uint8_t>(SkNamedTransferFn::CicpId::kPQ);
   *             break;
   *         case skcms_TFType_HLG:
   *         case skcms_TFType_HLGish:
   *             profile->has_CICP = true;
   *             profile->CICP.transfer_characteristics =
   *                 static_cast<uint8_t>(SkNamedTransferFn::CicpId::kHLG);
   *             break;
   *         default:
   *             break;
   *     }
   *     if (profile->has_CICP) {
   *         profile->CICP.matrix_coefficients = 0;
   *         profile->CICP.video_full_range_flag = 1;
   *         SkNamedPrimaries::CicpId primaries_id = SkNamedPrimaries::CicpId::kRec709;
   *         if (SkNamedPrimaries::GetCicpFromMatrix(fToXYZD50, primaries_id)) {
   *             profile->CICP.color_primaries = static_cast<uint8_t>(primaries_id);
   *         } else {
   *             profile->CICP.color_primaries = SkNamedPrimaries::kCicpIdApplicationDefined;
   *         }
   *     }
   * }
   * ```
   */
  public override fun toProfile(profile: SkcmsICCProfile?) {
    TODO("Implement toProfile")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpace::gammaCloseToSRGB() const {
   *     // Nearly-equal transfer functions were snapped at construction time, so just do an exact test
   *     return memcmp(&fTransferFn, &SkNamedTransferFn::kSRGB, 7*sizeof(float)) == 0;
   * }
   * ```
   */
  public override fun gammaCloseToSRGB(): Boolean {
    TODO("Implement gammaCloseToSRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpace::gammaIsLinear() const {
   *     // Nearly-equal transfer functions were snapped at construction time, so just do an exact test
   *     return memcmp(&fTransferFn, &SkNamedTransferFn::kLinear, 7*sizeof(float)) == 0;
   * }
   * ```
   */
  public override fun gammaIsLinear(): Boolean {
    TODO("Implement gammaIsLinear")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpace::isNumericalTransferFn(skcms_TransferFunction* coeffs) const {
   *     // TODO: Change transferFn/invTransferFn to just operate on skcms_TransferFunction (all callers
   *     // already pass pointers to an skcms struct). Then remove this function, and update the two
   *     // remaining callers to do the right thing with transferFn and classify.
   *     this->transferFn(coeffs);
   *     return skcms_TransferFunction_getType(coeffs) == skcms_TFType_sRGBish;
   * }
   * ```
   */
  public override fun isNumericalTransferFn(fn: SkcmsTransferFunction?): Boolean {
    TODO("Implement isNumericalTransferFn")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpace::toXYZD50(skcms_Matrix3x3* toXYZD50) const {
   *     *toXYZD50 = fToXYZD50;
   *     return true;
   * }
   * ```
   */
  public override fun toXYZD50(toXYZD50: SkcmsMatrix3x3?): Boolean {
    TODO("Implement toXYZD50")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t toXYZD50Hash() const { return fToXYZD50Hash; }
   * ```
   */
  public override fun toXYZD50Hash(): UInt {
    TODO("Implement toXYZD50Hash")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkColorSpace::makeLinearGamma() const {
   *     if (this->gammaIsLinear()) {
   *         return sk_ref_sp(const_cast<SkColorSpace*>(this));
   *     }
   *     return SkColorSpace::MakeRGB(SkNamedTransferFn::kLinear, fToXYZD50);
   * }
   * ```
   */
  public override fun makeLinearGamma(): Int {
    TODO("Implement makeLinearGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkColorSpace::makeSRGBGamma() const {
   *     if (this->gammaCloseToSRGB()) {
   *         return sk_ref_sp(const_cast<SkColorSpace*>(this));
   *     }
   *     return SkColorSpace::MakeRGB(SkNamedTransferFn::kSRGB, fToXYZD50);
   * }
   * ```
   */
  public override fun makeSRGBGamma(): Int {
    TODO("Implement makeSRGBGamma")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> SkColorSpace::makeColorSpin() const {
   *     skcms_Matrix3x3 spin = {{
   *         { 0, 0, 1 },
   *         { 1, 0, 0 },
   *         { 0, 1, 0 },
   *     }};
   *
   *     skcms_Matrix3x3 spun = skcms_Matrix3x3_concat(&fToXYZD50, &spin);
   *
   *     return sk_sp<SkColorSpace>(new SkColorSpace(fTransferFn, spun));
   * }
   * ```
   */
  public override fun makeColorSpin(): Int {
    TODO("Implement makeColorSpin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkColorSpace::isSRGB() const {
   *     return sk_srgb_singleton() == this;
   * }
   * ```
   */
  public override fun isSRGB(): Boolean {
    TODO("Implement isSRGB")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkColorSpace::serialize() const {
   *     sk_sp<SkData> data = SkData::MakeUninitialized(this->writeToMemory(nullptr));
   *     this->writeToMemory(data->writable_data());
   *     return data;
   * }
   * ```
   */
  public override fun serialize(): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkColorSpace::writeToMemory(void* memory) const {
   *     if (memory) {
   *         *((ColorSpaceHeader*) memory) = ColorSpaceHeader();
   *         memory = SkTAddOffset<void>(memory, sizeof(ColorSpaceHeader));
   *
   *         memcpy(memory, &fTransferFn, 7 * sizeof(float));
   *         memory = SkTAddOffset<void>(memory, 7 * sizeof(float));
   *
   *         memcpy(memory, &fToXYZD50, 9 * sizeof(float));
   *     }
   *
   *     return sizeof(ColorSpaceHeader) + 16 * sizeof(float);
   * }
   * ```
   */
  public override fun writeToMemory(memory: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpace::transferFn(float gabcdef[7]) const {
   *     memcpy(gabcdef, &fTransferFn, 7*sizeof(float));
   * }
   * ```
   */
  public override fun transferFn(gabcdef: FloatArray) {
    TODO("Implement transferFn")
  }

  /**
   * C++ original:
   * ```cpp
   * void       transferFn(skcms_TransferFunction* fn) const
   * ```
   */
  public override fun transferFn(fn: SkcmsTransferFunction?) {
    TODO("Implement transferFn")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpace::invTransferFn(skcms_TransferFunction* fn) const {
   *     this->computeLazyDstFields();
   *     *fn = fInvTransferFn;
   * }
   * ```
   */
  public override fun invTransferFn(fn: SkcmsTransferFunction?) {
    TODO("Implement invTransferFn")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpace::gamutTransformTo(const SkColorSpace* dst, skcms_Matrix3x3* src_to_dst) const {
   *     dst->computeLazyDstFields();
   *     *src_to_dst = skcms_Matrix3x3_concat(&dst->fFromXYZD50, &fToXYZD50);
   * }
   * ```
   */
  public override fun gamutTransformTo(dst: SkColorSpace?, srcToDst: SkcmsMatrix3x3?) {
    TODO("Implement gamutTransformTo")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t transferFnHash() const { return fTransferFnHash; }
   * ```
   */
  public override fun transferFnHash(): UInt {
    TODO("Implement transferFnHash")
  }

  /**
   * C++ original:
   * ```cpp
   * uint64_t           hash() const { return (uint64_t)fTransferFnHash << 32 | fToXYZD50Hash; }
   * ```
   */
  public override fun hash(): ULong {
    TODO("Implement hash")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorSpace::computeLazyDstFields() const {
   *     fLazyDstFieldsOnce([this] {
   *
   *         // Invert 3x3 gamut, defaulting to sRGB if we can't.
   *         {
   *             if (!skcms_Matrix3x3_invert(&fToXYZD50, &fFromXYZD50)) {
   *                 SkAssertResult(skcms_Matrix3x3_invert(&skcms_sRGB_profile()->toXYZD50,
   *                                                       &fFromXYZD50));
   *             }
   *         }
   *
   *         // Invert transfer function, defaulting to sRGB if we can't.
   *         {
   *             if (!skcms_TransferFunction_invert(&fTransferFn, &fInvTransferFn)) {
   *                 fInvTransferFn = *skcms_sRGB_Inverse_TransferFunction();
   *             }
   *         }
   *
   *     });
   * }
   * ```
   */
  public override fun computeLazyDstFields() {
    TODO("Implement computeLazyDstFields")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::MakeSRGB() {
     *     return sk_ref_sp(sk_srgb_singleton());
     * }
     * ```
     */
    public override fun makeSRGB(): Int {
      TODO("Implement makeSRGB")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::MakeSRGBLinear() {
     *     return sk_ref_sp(sk_srgb_linear_singleton());
     * }
     * ```
     */
    public override fun makeSRGBLinear(): Int {
      TODO("Implement makeSRGBLinear")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::MakeRGB(const skcms_TransferFunction& transferFn,
     *                                           const skcms_Matrix3x3& toXYZ) {
     *     if (skcms_TransferFunction_getType(&transferFn) == skcms_TFType_Invalid) {
     *         return nullptr;
     *     }
     *
     *     const skcms_TransferFunction* tf = &transferFn;
     *
     *     if (is_almost_srgb(transferFn)) {
     *         if (xyz_almost_equal(toXYZ, SkNamedGamut::kSRGB)) {
     *             return SkColorSpace::MakeSRGB();
     *         }
     *         tf = &SkNamedTransferFn::kSRGB;
     *     } else if (is_almost_2dot2(transferFn)) {
     *         tf = &SkNamedTransferFn::k2Dot2;
     *     } else if (is_almost_linear(transferFn)) {
     *         if (xyz_almost_equal(toXYZ, SkNamedGamut::kSRGB)) {
     *             return SkColorSpace::MakeSRGBLinear();
     *         }
     *         tf = &SkNamedTransferFn::kLinear;
     *     }
     *
     *     return sk_sp<SkColorSpace>(new SkColorSpace(*tf, toXYZ));
     * }
     * ```
     */
    public override fun makeRGB(transferFn: SkcmsTransferFunction, toXYZ: SkcmsMatrix3x3): Int {
      TODO("Implement makeRGB")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::MakeCICP(SkNamedPrimaries::CicpId color_primaries,
     *                                            SkNamedTransferFn::CicpId transfer_characteristics) {
     *     skcms_TransferFunction trfn;
     *     if (!SkNamedTransferFn::GetCicp(transfer_characteristics, trfn)) {
     *         return nullptr;
     *     }
     *
     *     skcms_Matrix3x3 toXYZD50;
     *     if (!SkNamedPrimaries::GetCicp(color_primaries, toXYZD50)) {
     *         return nullptr;
     *     }
     *
     *     return SkColorSpace::MakeRGB(trfn, toXYZD50);
     * }
     * ```
     */
    public override fun makeCICP(colorPrimaries: CicpId, transferCharacteristics: CicpId): Int {
      TODO("Implement makeCICP")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::Make(const skcms_ICCProfile& profile) {
     *     // The CICP values are only valid for full-range, with no matrix.
     *     bool use_cicp = profile.has_CICP &&
     *                     profile.CICP.matrix_coefficients == 0 &&
     *                     profile.CICP.video_full_range_flag == 1;
     *     auto cicp_color_primaries = static_cast<SkNamedPrimaries::CicpId>(profile.CICP.color_primaries);
     *     auto cicp_transfer_characteristics =
     *         static_cast<SkNamedTransferFn::CicpId>(profile.CICP.transfer_characteristics);
     *
     *     // Early checks for exact sRGB matches.
     *     if (use_cicp &&
     *         cicp_color_primaries == SkNamedPrimaries::CicpId::kRec709 &&
     *         cicp_transfer_characteristics == SkNamedTransferFn::CicpId::kIEC61966_2_4) {
     *         return SkColorSpace::MakeSRGB();
     *     } else if (skcms_ApproximatelyEqualProfiles(&profile, skcms_sRGB_profile())) {
     *         return SkColorSpace::MakeSRGB();
     *     }
     *
     *     // Set the toXYZD50 matrix, preferring CICP over the matrix itself.
     *     skcms_Matrix3x3 toXYZD50;
     *     bool hasSetToXYZD50 = false;
     *     if (use_cicp) {
     *         if (SkNamedPrimaries::GetCicp(cicp_color_primaries, toXYZD50)) {
     *             hasSetToXYZD50 = true;
     *         } else if (profile.CICP.color_primaries != SkNamedPrimaries::kCicpIdApplicationDefined) {
     *             return nullptr;
     *         }
     *     }
     *     if (profile.has_toXYZD50 && !hasSetToXYZD50) {
     *         // TODO: can we save this work and skip lazily inverting the matrix later?
     *         skcms_Matrix3x3 inv;
     *         toXYZD50 = profile.toXYZD50;
     *         if (skcms_Matrix3x3_invert(&toXYZD50, &inv)) {
     *             hasSetToXYZD50 = true;
     *         }
     *     }
     *     if (!hasSetToXYZD50) {
     *         return nullptr;
     *     }
     *
     *     // Set the transfer function, preferring CICP over the curves.
     *     skcms_TransferFunction trfn;
     *     bool hasSetTrfn = false;
     *     if (use_cicp) {
     *         if (SkNamedTransferFn::GetCicp(cicp_transfer_characteristics, trfn)) {
     *             hasSetTrfn = true;
     *         } else if (profile.CICP.transfer_characteristics !=
     *                    SkNamedTransferFn::kCicpIdApplicationDefined) {
     *             return nullptr;
     *         }
     *     }
     *     if (profile.has_trc && !hasSetTrfn) {
     *         // We can't work with tables or mismatched parametric curves.
     *         const skcms_Curve* trc = profile.trc;
     *         if (trc[0].table_entries == 0 &&
     *             trc[1].table_entries == 0 &&
     *             trc[2].table_entries == 0 &&
     *             0 == memcmp(&trc[0].parametric, &trc[1].parametric, sizeof(trc[0].parametric)) &&
     *             0 == memcmp(&trc[0].parametric, &trc[2].parametric, sizeof(trc[0].parametric)))
     *         {
     *             trfn = profile.trc[0].parametric;
     *             hasSetTrfn = true;
     *         } else {
     *             // If all curves look close enough to sRGB, that's fine.
     *             // TODO: should we maybe do this unconditionally to snap near-sRGB parametrics to sRGB?
     *             if (skcms_TRCs_AreApproximateInverse(&profile, skcms_sRGB_Inverse_TransferFunction())) {
     *                 trfn = SkNamedTransferFn::kSRGB;
     *                 hasSetTrfn = true;
     *             }
     *         }
     *     }
     *     if (!hasSetTrfn) {
     *         return nullptr;
     *     }
     *
     *     return SkColorSpace::MakeRGB(trfn, toXYZD50);
     * }
     * ```
     */
    public override fun make(profile: SkcmsICCProfile): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorSpace> SkColorSpace::Deserialize(const void* data, size_t length) {
     *     if (length < sizeof(ColorSpaceHeader)) {
     *         return nullptr;
     *     }
     *
     *     ColorSpaceHeader header = *((const ColorSpaceHeader*) data);
     *     data = SkTAddOffset<const void>(data, sizeof(ColorSpaceHeader));
     *     length -= sizeof(ColorSpaceHeader);
     *     if (header.fVersion != k1_Version) {
     *         return nullptr;
     *     }
     *
     *     if (length < 16 * sizeof(float)) {
     *         return nullptr;
     *     }
     *
     *     skcms_TransferFunction transferFn;
     *     memcpy(&transferFn, data, 7 * sizeof(float));
     *     data = SkTAddOffset<const void>(data, 7 * sizeof(float));
     *
     *     skcms_Matrix3x3 toXYZ;
     *     memcpy(&toXYZ, data, 9 * sizeof(float));
     *     return SkColorSpace::MakeRGB(transferFn, toXYZ);
     * }
     * ```
     */
    public override fun deserialize(`data`: Unit?, length: ULong): Int {
      TODO("Implement deserialize")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkColorSpace::Equals(const SkColorSpace* x, const SkColorSpace* y) {
     *     if (x == y) {
     *         return true;
     *     }
     *
     *     if (!x || !y) {
     *         return false;
     *     }
     *
     *     if (x->hash() == y->hash()) {
     *     #if defined(SK_DEBUG)
     *         // Do these floats function equivalently?
     *         // This returns true more often than simple float comparison   (NaN vs. NaN) and,
     *         // also returns true more often than simple bitwise comparison (+0 vs. -0) and,
     *         // even returns true more often than those two OR'd together   (two different NaNs).
     *         auto equiv = [](float X, float Y) {
     *             return (X==Y)
     *                 || (std::isnan(X) && std::isnan(Y));
     *         };
     *
     *         for (int i = 0; i < 7; i++) {
     *             float X = (&x->fTransferFn.g)[i],
     *                   Y = (&y->fTransferFn.g)[i];
     *             SkASSERTF(equiv(X,Y), "Hash collision at tf[%d], !equiv(%g,%g)\n", i, X,Y);
     *         }
     *         for (int r = 0; r < 3; r++)
     *         for (int c = 0; c < 3; c++) {
     *             float X = x->fToXYZD50.vals[r][c],
     *                   Y = y->fToXYZD50.vals[r][c];
     *             SkASSERTF(equiv(X,Y), "Hash collision at toXYZD50[%d][%d], !equiv(%g,%g)\n", r,c, X,Y);
     *         }
     *     #endif
     *         return true;
     *     }
     *     return false;
     * }
     * ```
     */
    public override fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }
  }
}
