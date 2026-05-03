package org.skia.core

import kotlin.CharArray
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkFontMgr : public SkRefCnt {
 * public:
 *     int countFamilies() const;
 *     void getFamilyName(int index, SkString* familyName) const;
 *     sk_sp<SkFontStyleSet> createStyleSet(int index) const;
 *
 *     /**
 *      *  The caller must call unref() on the returned object.
 *      *  Never returns NULL; will return an empty set if the name is not found.
 *      *
 *      *  Passing nullptr as the parameter will return the default system family.
 *      *  Note that most systems don't have a default system family, so passing nullptr will often
 *      *  result in the empty set.
 *      *
 *      *  It is possible that this will return a style set not accessible from
 *      *  createStyleSet(int) due to hidden or auto-activated fonts.
 *      */
 *     sk_sp<SkFontStyleSet> matchFamily(const char familyName[]) const;
 *
 *     /**
 *      *  Find the closest matching typeface to the specified familyName and style
 *      *  and return a ref to it. The caller must call unref() on the returned
 *      *  object. Will return nullptr if no 'good' match is found.
 *      *
 *      *  Passing |nullptr| as the parameter for |familyName| will return the
 *      *  default system font.
 *      *
 *      *  It is possible that this will return a style set not accessible from
 *      *  createStyleSet(int) or matchFamily(const char[]) due to hidden or
 *      *  auto-activated fonts.
 *      */
 *     sk_sp<SkTypeface> matchFamilyStyle(const char familyName[], const SkFontStyle&) const;
 *
 *     /**
 *      *  Use the system fallback to find a typeface for the given character.
 *      *  Note that bcp47 is a combination of ISO 639, 15924, and 3166-1 codes,
 *      *  so it is fine to just pass a ISO 639 here.
 *      *
 *      *  Will return NULL if no family can be found for the character
 *      *  in the system fallback.
 *      *
 *      *  Passing |nullptr| as the parameter for |familyName| will return the
 *      *  default system font.
 *      *
 *      *  bcp47[0] is the least significant fallback, bcp47[bcp47Count-1] is the
 *      *  most significant. If no specified bcp47 codes match, any font with the
 *      *  requested character will be matched.
 *      */
 *     sk_sp<SkTypeface> matchFamilyStyleCharacter(const char familyName[], const SkFontStyle&,
 *                                                 const char* bcp47[], int bcp47Count,
 *                                                 SkUnichar character) const;
 *
 *     /**
 *      *  Create a typeface for the specified data and TTC index (pass 0 for none)
 *      *  or NULL if the data is not recognized. The caller must call unref() on
 *      *  the returned object if it is not null.
 *      */
 *     sk_sp<SkTypeface> makeFromData(sk_sp<SkData>, int ttcIndex = 0) const;
 *
 *     /**
 *      *  Create a typeface for the specified stream and TTC index
 *      *  (pass 0 for none) or NULL if the stream is not recognized. The caller
 *      *  must call unref() on the returned object if it is not null.
 *      */
 *     sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, int ttcIndex = 0) const;
 *
 *     /* Experimental, API subject to change. */
 *     sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&) const;
 *
 *     /**
 *      *  Create a typeface for the specified fileName and TTC index
 *      *  (pass 0 for none) or NULL if the file is not found, or its contents are
 *      *  not recognized. The caller must call unref() on the returned object
 *      *  if it is not null.
 *      */
 *     sk_sp<SkTypeface> makeFromFile(const char path[], int ttcIndex = 0) const;
 *
 *     sk_sp<SkTypeface> legacyMakeTypeface(const char familyName[], SkFontStyle style) const;
 *
 *     /* Returns an empty font manager without any typeface dependencies */
 *     static sk_sp<SkFontMgr> RefEmpty();
 *
 * protected:
 *     virtual int onCountFamilies() const = 0;
 *     virtual void onGetFamilyName(int index, SkString* familyName) const = 0;
 *     virtual sk_sp<SkFontStyleSet> onCreateStyleSet(int index)const  = 0;
 *
 *     /** May return NULL if the name is not found. */
 *     virtual sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const = 0;
 *
 *     virtual sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[],
 *                                                  const SkFontStyle&) const = 0;
 *     virtual sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[],
 *                                                           const SkFontStyle&,
 *                                                           const char* bcp47[], int bcp47Count,
 *                                                           SkUnichar character) const = 0;
 *
 *     virtual sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const = 0;
 *     virtual sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
 *                                                     int ttcIndex) const = 0;
 *     virtual sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
 *                                                    const SkFontArguments&) const = 0;
 *     virtual sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const = 0;
 *
 *     virtual sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const = 0;
 * }
 * ```
 */
public abstract class SkFontMgr : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * int SkFontMgr::countFamilies() const {
   *     return this->onCountFamilies();
   * }
   * ```
   */
  public fun countFamilies(): Int {
    TODO("Implement countFamilies")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkFontMgr::getFamilyName(int index, SkString* familyName) const {
   *     this->onGetFamilyName(index, familyName);
   * }
   * ```
   */
  public fun getFamilyName(index: Int, familyName: String?) {
    TODO("Implement getFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> SkFontMgr::createStyleSet(int index) const {
   *     return emptyOnNull(this->onCreateStyleSet(index));
   * }
   * ```
   */
  public fun createStyleSet(index: Int): Int {
    TODO("Implement createStyleSet")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontStyleSet> SkFontMgr::matchFamily(const char familyName[]) const {
   *     return emptyOnNull(this->onMatchFamily(familyName));
   * }
   * ```
   */
  public fun matchFamily(familyName: CharArray): Int {
    TODO("Implement matchFamily")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontMgr::matchFamilyStyle(const char familyName[],
   *                                         const SkFontStyle& fs) const {
   *     return this->onMatchFamilyStyle(familyName, fs);
   * }
   * ```
   */
  public fun matchFamilyStyle(familyName: CharArray, fs: SkFontStyle): Int {
    TODO("Implement matchFamilyStyle")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontMgr::matchFamilyStyleCharacter(const char familyName[], const SkFontStyle& style,
   *                                                  const char* bcp47[], int bcp47Count,
   *                                                  SkUnichar character) const {
   *     return this->onMatchFamilyStyleCharacter(familyName, style, bcp47, bcp47Count, character);
   * }
   * ```
   */
  public fun matchFamilyStyleCharacter(
    familyName: CharArray,
    style: SkFontStyle,
    bcp47: Int,
    bcp47Count: Int,
    character: SkUnichar,
  ): Int {
    TODO("Implement matchFamilyStyleCharacter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontMgr::makeFromData(sk_sp<SkData> data, int ttcIndex) const {
   *     if (nullptr == data) {
   *         return nullptr;
   *     }
   *     return this->onMakeFromData(std::move(data), ttcIndex);
   * }
   * ```
   */
  public abstract fun makeFromData(`data`: SkSp<SkData>, ttcIndex: Int = 0): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, int ttcIndex = 0) const
   * ```
   */
  public abstract fun makeFromStream(param0: SkStreamAsset?, ttcIndex: Int = 0): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&) const
   * ```
   */
  public fun makeFromStream(param0: SkStreamAsset?, param1: SkFontArguments): Int {
    TODO("Implement makeFromStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontMgr::makeFromFile(const char path[], int ttcIndex) const {
   *     if (nullptr == path) {
   *         return nullptr;
   *     }
   *     return this->onMakeFromFile(path, ttcIndex);
   * }
   * ```
   */
  public abstract fun makeFromFile(path: CharArray, ttcIndex: Int = 0): Int

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkFontMgr::legacyMakeTypeface(const char familyName[], SkFontStyle style) const {
   *     return this->onLegacyMakeTypeface(familyName, style);
   * }
   * ```
   */
  public fun legacyMakeTypeface(familyName: CharArray, style: SkFontStyle): Int {
    TODO("Implement legacyMakeTypeface")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual int onCountFamilies() const = 0
   * ```
   */
  protected abstract fun onCountFamilies(): Int

  /**
   * C++ original:
   * ```cpp
   * virtual void onGetFamilyName(int index, SkString* familyName) const = 0
   * ```
   */
  protected abstract fun onGetFamilyName(index: Int, familyName: String?)

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkFontStyleSet> onCreateStyleSet(int index)const  = 0
   * ```
   */
  protected abstract fun onCreateStyleSet(index: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkFontStyleSet> onMatchFamily(const char familyName[]) const = 0
   * ```
   */
  protected abstract fun onMatchFamily(familyName: CharArray): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMatchFamilyStyle(const char familyName[],
   *                                                  const SkFontStyle&) const = 0
   * ```
   */
  protected abstract fun onMatchFamilyStyle(familyName: CharArray, param1: SkFontStyle): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMatchFamilyStyleCharacter(const char familyName[],
   *                                                           const SkFontStyle&,
   *                                                           const char* bcp47[], int bcp47Count,
   *                                                           SkUnichar character) const = 0
   * ```
   */
  protected abstract fun onMatchFamilyStyleCharacter(
    familyName: CharArray,
    param1: SkFontStyle,
    bcp47: Int,
    bcp47Count: Int,
    character: SkUnichar,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMakeFromData(sk_sp<SkData>, int ttcIndex) const = 0
   * ```
   */
  protected abstract fun onMakeFromData(param0: SkSp<SkData>, ttcIndex: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMakeFromStreamIndex(std::unique_ptr<SkStreamAsset>,
   *                                                     int ttcIndex) const = 0
   * ```
   */
  protected abstract fun onMakeFromStreamIndex(param0: SkStreamAsset?, ttcIndex: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMakeFromStreamArgs(std::unique_ptr<SkStreamAsset>,
   *                                                    const SkFontArguments&) const = 0
   * ```
   */
  protected abstract fun onMakeFromStreamArgs(param0: SkStreamAsset?, param1: SkFontArguments): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onMakeFromFile(const char path[], int ttcIndex) const = 0
   * ```
   */
  protected abstract fun onMakeFromFile(path: CharArray, ttcIndex: Int): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkTypeface> onLegacyMakeTypeface(const char familyName[], SkFontStyle) const = 0
   * ```
   */
  protected abstract fun onLegacyMakeTypeface(familyName: CharArray, param1: SkFontStyle): Int

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFontMgr> SkFontMgr::RefEmpty() {
     *     static SkFontMgr* singleton = new SkEmptyFontMgr();
     *     return sk_ref_sp(singleton);
     * }
     * ```
     */
    public fun refEmpty(): Int {
      TODO("Implement refEmpty")
    }
  }
}
