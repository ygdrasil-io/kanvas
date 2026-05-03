package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API Metadata {
 *   public:
 *     /**
 *      * Return a container with no metadata.
 *      */
 *     static Metadata MakeEmpty();
 *
 *     /**
 *      * If there does not exists Content Light Level Information metadata, then return false.
 *      * Otherwise return true and if `clli` is non-nullptr then write the metadata to `clli`.
 *      */
 *     bool getContentLightLevelInformation(ContentLightLevelInformation* clli) const;
 *
 *     /**
 *      * Set the Content Light Level Information metadata.
 *      */
 *     void setContentLightLevelInformation(const ContentLightLevelInformation& clli);
 *
 *     /**
 *      * If there does not exists Mastering Display Color Volume metadata, then return false.
 *      * Otherwise return true and if `mdcv` is non-nullptr then write the metadata to `mdcv`.
 *      */
 *     bool getMasteringDisplayColorVolume(MasteringDisplayColorVolume* mdcv) const;
 *
 *     /**
 *      * Set the Mastering Display Color Volume metadata.
 *      */
 *     void setMasteringDisplayColorVolume(const MasteringDisplayColorVolume& mdcv);
 *
 *     /**
 *      * Return the serialized Adaptive Global Tone Mapping metadata, or nullptr if none has been set.
 *      */
 *     sk_sp<const SkData> getSerializedAgtm() const;
 *
 *     /**
 *      * Set the serialized Adaptive Global Tone Mapping metadata.
 *      */
 *     void setSerializedAgtm(sk_sp<const SkData>);
 *
 *     /**
 *      * Return a human-readable description.
 *      */
 *     SkString toString() const;
 *
 *     bool operator==(const Metadata& other) const;
 *     bool operator!=(const Metadata& other) const {
 *       return !(*this == other);
 *     }
 *
 *   private:
 *     std::optional<ContentLightLevelInformation> fContentLightLevelInformation;
 *     std::optional<MasteringDisplayColorVolume> fMasteringDisplayColorVolume;
 *     sk_sp<const SkData> fAgtm;
 * }
 * ```
 */
public data class Metadata public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::optional<ContentLightLevelInformation> fContentLightLevelInformation
   * ```
   */
  private var fContentLightLevelInformation: ContentLightLevelInformation?,
  /**
   * C++ original:
   * ```cpp
   * std::optional<MasteringDisplayColorVolume> fMasteringDisplayColorVolume
   * ```
   */
  private var fMasteringDisplayColorVolume: MasteringDisplayColorVolume?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> fAgtm
   * ```
   */
  private var fAgtm: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool Metadata::getContentLightLevelInformation(ContentLightLevelInformation* clli) const {
   *     if (!fContentLightLevelInformation.has_value()) {
   *         return false;
   *     }
   *     if (clli) {
   *         *clli = fContentLightLevelInformation.value();
   *     }
   *     return true;
   * }
   * ```
   */
  public fun getContentLightLevelInformation(clli: ContentLightLevelInformation?): Boolean {
    TODO("Implement getContentLightLevelInformation")
  }

  /**
   * C++ original:
   * ```cpp
   * void Metadata::setContentLightLevelInformation(const ContentLightLevelInformation& clli) {
   *     fContentLightLevelInformation = clli;
   * }
   * ```
   */
  public fun setContentLightLevelInformation(clli: ContentLightLevelInformation) {
    TODO("Implement setContentLightLevelInformation")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Metadata::getMasteringDisplayColorVolume(MasteringDisplayColorVolume* mdcv) const {
   *     if (!fMasteringDisplayColorVolume.has_value()) {
   *         return false;
   *     }
   *     if (mdcv) {
   *         *mdcv = fMasteringDisplayColorVolume.value();
   *     }
   *     return true;
   * }
   * ```
   */
  public fun getMasteringDisplayColorVolume(mdcv: MasteringDisplayColorVolume?): Boolean {
    TODO("Implement getMasteringDisplayColorVolume")
  }

  /**
   * C++ original:
   * ```cpp
   * void Metadata::setMasteringDisplayColorVolume(const MasteringDisplayColorVolume& mdcv) {
   *     fMasteringDisplayColorVolume = mdcv;
   * }
   * ```
   */
  public fun setMasteringDisplayColorVolume(mdcv: MasteringDisplayColorVolume) {
    TODO("Implement setMasteringDisplayColorVolume")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> Metadata::getSerializedAgtm() const {
   *     return fAgtm;
   * }
   * ```
   */
  public fun getSerializedAgtm(): Int {
    TODO("Implement getSerializedAgtm")
  }

  /**
   * C++ original:
   * ```cpp
   * void Metadata::setSerializedAgtm(sk_sp<const SkData> agtm) {
   *     fAgtm = agtm;
   * }
   * ```
   */
  public fun setSerializedAgtm(agtm: SkSp<SkData>) {
    TODO("Implement setSerializedAgtm")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString Metadata::toString() const {
   *     auto agtm = Agtm::Make(fAgtm.get());
   *     return SkStringPrintf("{clli:%s, mdcv:%s, agtm:%s}",
   *         fContentLightLevelInformation.has_value() ?
   *             fContentLightLevelInformation->toString().c_str() : "None",
   *         fMasteringDisplayColorVolume.has_value() ?
   *             fMasteringDisplayColorVolume->toString().c_str() : "None",
   *         agtm ? agtm->toString().c_str() : "None");
   * }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Metadata::operator==(const Metadata& other) const {
   *     return fContentLightLevelInformation == other.fContentLightLevelInformation &&
   *            fMasteringDisplayColorVolume == other.fMasteringDisplayColorVolume &&
   *            SkData::Equals(fAgtm.get(), other.fAgtm.get());
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * Metadata Metadata::MakeEmpty() {
     *     return Metadata();
     * }
     * ```
     */
    public fun makeEmpty(): Metadata {
      TODO("Implement makeEmpty")
    }
  }
}
