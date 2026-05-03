package org.skia.tools

import kotlin.Boolean
import kotlin.Double
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkFlagInfo {
 * public:
 *     enum FlagTypes {
 *         kBool_FlagType,
 *         kString_FlagType,
 *         kInt_FlagType,
 *         kDouble_FlagType,
 *     };
 *
 *     /**
 *      *  Each Create<Type>Flag function creates an SkFlagInfo of the specified type. The SkFlagInfo
 *      *  object is appended to a list, which is deleted when CommandLineFlags::Parse is called.
 *      *  Therefore, each call should be made before the call to ::Parse. They are not intended
 *      *  to be called directly. Instead, use the macros described above.
 *      *  @param name Long version (at least 2 characters) of the name of the flag. This name can
 *      *      be referenced on the command line as "--name" to set the value of this flag.
 *      *  @param shortName Short version (one character) of the name of the flag. This name can
 *      *      be referenced on the command line as "-shortName" to set the value of this flag.
 *      *  @param p<Type> Pointer to a global variable which holds the value set by CommandLineFlags.
 *      *  @param defaultValue The default value of this flag. The variable pointed to by p<Type> will
 *      *      be set to this value initially. This is also displayed as part of the help output.
 *      *  @param helpString Explanation of what this flag changes in the program.
 *      */
 *     static bool CreateBoolFlag(const char* name,
 *                                const char* shortName,
 *                                bool*       pBool,
 *                                bool        defaultValue,
 *                                const char* helpString) {
 *         SkFlagInfo* info  = new SkFlagInfo(name, shortName, kBool_FlagType, helpString, nullptr);
 *         info->fBoolValue  = pBool;
 *         *info->fBoolValue = info->fDefaultBool = defaultValue;
 *         return true;
 *     }
 *
 *     /**
 *      *  See comments for CreateBoolFlag.
 *      *  @param pStrings Unlike the others, this is a pointer to an array of values.
 *      *  @param defaultValue Thise default will be parsed so that strings separated by spaces
 *      *      will be added to pStrings.
 *      */
 *     static bool CreateStringFlag(const char*                    name,
 *                                  const char*                    shortName,
 *                                  CommandLineFlags::StringArray* pStrings,
 *                                  const char*                    defaultValue,
 *                                  const char*                    helpString,
 *                                  const char*                    extendedHelpString);
 *
 *     /**
 *      *  See comments for CreateBoolFlag.
 *      */
 *     static bool CreateIntFlag(const char* name,
 *                               int*    pInt,
 *                               int     defaultValue,
 *                               const char* helpString) {
 *         SkFlagInfo* info = new SkFlagInfo(name, nullptr, kInt_FlagType, helpString, nullptr);
 *         info->fIntValue  = pInt;
 *         *info->fIntValue = info->fDefaultInt = defaultValue;
 *         return true;
 *     }
 *
 *     static bool CreateIntFlag(const char* name,
 *                               const char* shortName,
 *                               int*    pInt,
 *                               int     defaultValue,
 *                               const char* helpString) {
 *         SkFlagInfo* info = new SkFlagInfo(name, shortName, kInt_FlagType, helpString, nullptr);
 *         info->fIntValue  = pInt;
 *         *info->fIntValue = info->fDefaultInt = defaultValue;
 *         return true;
 *     }
 *
 *     /**
 *      *  See comments for CreateBoolFlag.
 *      */
 *     static bool CreateDoubleFlag(const char* name,
 *                                  double*     pDouble,
 *                                  double      defaultValue,
 *                                  const char* helpString) {
 *         SkFlagInfo* info    = new SkFlagInfo(name, nullptr, kDouble_FlagType, helpString, nullptr);
 *         info->fDoubleValue  = pDouble;
 *         *info->fDoubleValue = info->fDefaultDouble = defaultValue;
 *         return true;
 *     }
 *
 *     /**
 *      *  Returns true if the string matches this flag.
 *      *  For a boolean flag, also sets the value, since a boolean flag can be set in a number of ways
 *      *  without looking at the following string:
 *      *      --name
 *      *      --noname
 *      *      --name=true
 *      *      --name=false
 *      *      --name=1
 *      *      --name=0
 *      *      --name=TRUE
 *      *      --name=FALSE
 *      */
 *     bool match(const char* string);
 *
 *     FlagTypes getFlagType() const { return fFlagType; }
 *
 *     void resetStrings() {
 *         if (kString_FlagType == fFlagType) {
 *             fStrings->reset();
 *         } else {
 *             SkDEBUGFAIL("Can only call resetStrings on kString_FlagType");
 *         }
 *     }
 *
 *     void append(const char* string) {
 *         if (kString_FlagType == fFlagType) {
 *             fStrings->append(string);
 *         } else {
 *             SkDEBUGFAIL("Can only append to kString_FlagType");
 *         }
 *     }
 *
 *     void setInt(int value) {
 *         if (kInt_FlagType == fFlagType) {
 *             *fIntValue = value;
 *         } else {
 *             SkDEBUGFAIL("Can only call setInt on kInt_FlagType");
 *         }
 *     }
 *
 *     void setDouble(double value) {
 *         if (kDouble_FlagType == fFlagType) {
 *             *fDoubleValue = value;
 *         } else {
 *             SkDEBUGFAIL("Can only call setDouble on kDouble_FlagType");
 *         }
 *     }
 *
 *     void setBool(bool value) {
 *         if (kBool_FlagType == fFlagType) {
 *             *fBoolValue = value;
 *         } else {
 *             SkDEBUGFAIL("Can only call setBool on kBool_FlagType");
 *         }
 *     }
 *
 *     SkFlagInfo* next() { return fNext; }
 *
 *     const SkString& name() const { return fName; }
 *
 *     const SkString& shortName() const { return fShortName; }
 *
 *     const SkString& help() const { return fHelpString; }
 *     const SkString& extendedHelp() const { return fExtendedHelpString; }
 *
 *     SkString defaultValue() const {
 *         SkString result;
 *         switch (fFlagType) {
 *             case SkFlagInfo::kBool_FlagType:
 *                 result.printf("%s", fDefaultBool ? "true" : "false");
 *                 break;
 *             case SkFlagInfo::kString_FlagType: return fDefaultString;
 *             case SkFlagInfo::kInt_FlagType: result.printf("%i", fDefaultInt); break;
 *             case SkFlagInfo::kDouble_FlagType: result.printf("%2.2f", fDefaultDouble); break;
 *             default: SkDEBUGFAIL("Invalid flag type");
 *         }
 *         return result;
 *     }
 *
 *     SkString typeAsString() const {
 *         switch (fFlagType) {
 *             case SkFlagInfo::kBool_FlagType: return SkString("bool");
 *             case SkFlagInfo::kString_FlagType: return SkString("string");
 *             case SkFlagInfo::kInt_FlagType: return SkString("int");
 *             case SkFlagInfo::kDouble_FlagType: return SkString("double");
 *             default: SkDEBUGFAIL("Invalid flag type"); return SkString();
 *         }
 *     }
 *
 * private:
 *     SkFlagInfo(const char* name,
 *                const char* shortName,
 *                FlagTypes   type,
 *                const char* helpString,
 *                const char* extendedHelpString)
 *             : fName(name)
 *             , fShortName(shortName)
 *             , fFlagType(type)
 *             , fHelpString(helpString)
 *             , fExtendedHelpString(extendedHelpString)
 *             , fBoolValue(nullptr)
 *             , fDefaultBool(false)
 *             , fIntValue(nullptr)
 *             , fDefaultInt(0)
 *             , fDoubleValue(nullptr)
 *             , fDefaultDouble(0)
 *             , fStrings(nullptr) {
 *         fNext                   = CommandLineFlags::gHead;
 *         CommandLineFlags::gHead = this;
 *         SkASSERT(name && strlen(name) > 1);
 *         SkASSERT(nullptr == shortName || 1 == strlen(shortName));
 *     }
 *
 *     /**
 *      *  Set a StringArray to hold the values stored in defaultStrings.
 *      *  @param array The StringArray to modify.
 *      *  @param defaultStrings Space separated list of strings that should be inserted into array
 *      *      individually.
 *      */
 *     static void SetDefaultStrings(CommandLineFlags::StringArray* array, const char* defaultStrings);
 *
 *     // Name of the flag, without initial dashes
 *     SkString                       fName;
 *     SkString                       fShortName;
 *     FlagTypes                      fFlagType;
 *     SkString                       fHelpString;
 *     SkString                       fExtendedHelpString;
 *     bool*                          fBoolValue;
 *     bool                           fDefaultBool;
 *     int*                           fIntValue;
 *     int                            fDefaultInt;
 *     double*                        fDoubleValue;
 *     double                         fDefaultDouble;
 *     CommandLineFlags::StringArray* fStrings;
 *     // Both for the help string and in case fStrings is empty.
 *     SkString fDefaultString;
 *
 *     // In order to keep a linked list.
 *     SkFlagInfo* fNext;
 * }
 * ```
 */
public data class SkFlagInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkString                       fName
   * ```
   */
  private var fName: String,
  /**
   * C++ original:
   * ```cpp
   * SkString                       fShortName
   * ```
   */
  private var fShortName: String,
  /**
   * C++ original:
   * ```cpp
   * FlagTypes                      fFlagType
   * ```
   */
  private var fFlagType: FlagTypes,
  /**
   * C++ original:
   * ```cpp
   * SkString                       fHelpString
   * ```
   */
  private var fHelpString: String,
  /**
   * C++ original:
   * ```cpp
   * SkString                       fExtendedHelpString
   * ```
   */
  private var fExtendedHelpString: String,
  /**
   * C++ original:
   * ```cpp
   * bool*                          fBoolValue
   * ```
   */
  private var fBoolValue: Boolean?,
  /**
   * C++ original:
   * ```cpp
   * bool                           fDefaultBool
   * ```
   */
  private var fDefaultBool: Boolean,
  /**
   * C++ original:
   * ```cpp
   * int*                           fIntValue
   * ```
   */
  private var fIntValue: Int?,
  /**
   * C++ original:
   * ```cpp
   * int                            fDefaultInt
   * ```
   */
  private var fDefaultInt: Int,
  /**
   * C++ original:
   * ```cpp
   * double*                        fDoubleValue
   * ```
   */
  private var fDoubleValue: Double?,
  /**
   * C++ original:
   * ```cpp
   * double                         fDefaultDouble
   * ```
   */
  private var fDefaultDouble: Double,
  /**
   * C++ original:
   * ```cpp
   * CommandLineFlags::StringArray* fStrings
   * ```
   */
  private var fStrings: CommandLineFlags.StringArray?,
  /**
   * C++ original:
   * ```cpp
   * SkString fDefaultString
   * ```
   */
  private var fDefaultString: String,
  /**
   * C++ original:
   * ```cpp
   * SkFlagInfo* fNext
   * ```
   */
  private var fNext: SkFlagInfo?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkFlagInfo::match(const char* string) {
   *     if (SkStrStartsWith(string, '-') && strlen(string) > 1) {
   *         string++;
   *         const SkString* compareName;
   *         if (SkStrStartsWith(string, '-') && strlen(string) > 1) {
   *             string++;
   *             // There were two dashes. Compare against full name.
   *             compareName = &fName;
   *         } else {
   *             // One dash. Compare against the short name.
   *             compareName = &fShortName;
   *         }
   *         if (kBool_FlagType == fFlagType) {
   *             // In this case, go ahead and set the value.
   *             if (compareName->equals(string)) {
   *                 *fBoolValue = true;
   *                 return true;
   *             }
   *             if (SkStrStartsWith(string, "no") && strlen(string) > 2) {
   *                 string += 2;
   *                 // Only allow "no" to be prepended to the full name.
   *                 if (fName.equals(string)) {
   *                     *fBoolValue = false;
   *                     return true;
   *                 }
   *                 return false;
   *             }
   *             int equalIndex = SkStrFind(string, "=");
   *             if (equalIndex > 0) {
   *                 // The string has an equal sign. Check to see if the string matches.
   *                 SkString flag(string, equalIndex);
   *                 if (flag.equals(*compareName)) {
   *                     // Check to see if the remainder beyond the equal sign is true or false:
   *                     string += equalIndex + 1;
   *                     parse_bool_arg(string, fBoolValue);
   *                     return true;
   *                 } else {
   *                     return false;
   *                 }
   *             }
   *         }
   *         return compareName->equals(string);
   *     }
   *
   *     // Has no dash
   *     return false;
   * }
   * ```
   */
  public fun match(string: String?): Boolean {
    TODO("Implement match")
  }

  /**
   * C++ original:
   * ```cpp
   * FlagTypes getFlagType() const { return fFlagType; }
   * ```
   */
  public fun getFlagType(): FlagTypes {
    TODO("Implement getFlagType")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetStrings() {
   *         if (kString_FlagType == fFlagType) {
   *             fStrings->reset();
   *         } else {
   *             SkDEBUGFAIL("Can only call resetStrings on kString_FlagType");
   *         }
   *     }
   * ```
   */
  public fun resetStrings() {
    TODO("Implement resetStrings")
  }

  /**
   * C++ original:
   * ```cpp
   * void append(const char* string) {
   *         if (kString_FlagType == fFlagType) {
   *             fStrings->append(string);
   *         } else {
   *             SkDEBUGFAIL("Can only append to kString_FlagType");
   *         }
   *     }
   * ```
   */
  public fun append(string: String?) {
    TODO("Implement append")
  }

  /**
   * C++ original:
   * ```cpp
   * void setInt(int value) {
   *         if (kInt_FlagType == fFlagType) {
   *             *fIntValue = value;
   *         } else {
   *             SkDEBUGFAIL("Can only call setInt on kInt_FlagType");
   *         }
   *     }
   * ```
   */
  public fun setInt(`value`: Int) {
    TODO("Implement setInt")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDouble(double value) {
   *         if (kDouble_FlagType == fFlagType) {
   *             *fDoubleValue = value;
   *         } else {
   *             SkDEBUGFAIL("Can only call setDouble on kDouble_FlagType");
   *         }
   *     }
   * ```
   */
  public fun setDouble(`value`: Double) {
    TODO("Implement setDouble")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBool(bool value) {
   *         if (kBool_FlagType == fFlagType) {
   *             *fBoolValue = value;
   *         } else {
   *             SkDEBUGFAIL("Can only call setBool on kBool_FlagType");
   *         }
   *     }
   * ```
   */
  public fun setBool(`value`: Boolean) {
    TODO("Implement setBool")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlagInfo* next() { return fNext; }
   * ```
   */
  public fun next(): SkFlagInfo {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& name() const { return fName; }
   * ```
   */
  public fun name(): String {
    TODO("Implement name")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& shortName() const { return fShortName; }
   * ```
   */
  public fun shortName(): String {
    TODO("Implement shortName")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& help() const { return fHelpString; }
   * ```
   */
  public fun help(): String {
    TODO("Implement help")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkString& extendedHelp() const { return fExtendedHelpString; }
   * ```
   */
  public fun extendedHelp(): String {
    TODO("Implement extendedHelp")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString defaultValue() const {
   *         SkString result;
   *         switch (fFlagType) {
   *             case SkFlagInfo::kBool_FlagType:
   *                 result.printf("%s", fDefaultBool ? "true" : "false");
   *                 break;
   *             case SkFlagInfo::kString_FlagType: return fDefaultString;
   *             case SkFlagInfo::kInt_FlagType: result.printf("%i", fDefaultInt); break;
   *             case SkFlagInfo::kDouble_FlagType: result.printf("%2.2f", fDefaultDouble); break;
   *             default: SkDEBUGFAIL("Invalid flag type");
   *         }
   *         return result;
   *     }
   * ```
   */
  public fun defaultValue(): String {
    TODO("Implement defaultValue")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString typeAsString() const {
   *         switch (fFlagType) {
   *             case SkFlagInfo::kBool_FlagType: return SkString("bool");
   *             case SkFlagInfo::kString_FlagType: return SkString("string");
   *             case SkFlagInfo::kInt_FlagType: return SkString("int");
   *             case SkFlagInfo::kDouble_FlagType: return SkString("double");
   *             default: SkDEBUGFAIL("Invalid flag type"); return SkString();
   *         }
   *     }
   * ```
   */
  public fun typeAsString(): String {
    TODO("Implement typeAsString")
  }

  public enum class FlagTypes {
    kBool_FlagType,
    kString_FlagType,
    kInt_FlagType,
    kDouble_FlagType,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool CreateBoolFlag(const char* name,
     *                                const char* shortName,
     *                                bool*       pBool,
     *                                bool        defaultValue,
     *                                const char* helpString) {
     *         SkFlagInfo* info  = new SkFlagInfo(name, shortName, kBool_FlagType, helpString, nullptr);
     *         info->fBoolValue  = pBool;
     *         *info->fBoolValue = info->fDefaultBool = defaultValue;
     *         return true;
     *     }
     * ```
     */
    public fun createBoolFlag(
      name: String?,
      shortName: String?,
      pBool: Boolean?,
      defaultValue: Boolean,
      helpString: String?,
    ): Boolean {
      TODO("Implement createBoolFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * template <typename T> static void ignore_result(const T&) {}
     *
     * bool SkFlagInfo::CreateStringFlag(const char*                    name,
     *                                   const char*                    shortName,
     *                                   CommandLineFlags::StringArray* pStrings,
     *                                   const char*                    defaultValue,
     *                                   const char*                    helpString,
     *                                   const char*                    extendedHelpString) {
     *     SkFlagInfo* info =
     *             new SkFlagInfo(name, shortName, kString_FlagType, helpString, extendedHelpString);
     *     info->fDefaultString.set(defaultValue);
     *
     *     info->fStrings = pStrings;
     *     SetDefaultStrings(pStrings, defaultValue);
     *     return true;
     * }
     * ```
     */
    public fun createStringFlag(
      name: String?,
      shortName: String?,
      pStrings: CommandLineFlags.StringArray?,
      defaultValue: String?,
      helpString: String?,
      extendedHelpString: String?,
    ): Boolean {
      TODO("Implement createStringFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool CreateIntFlag(const char* name,
     *                               int*    pInt,
     *                               int     defaultValue,
     *                               const char* helpString) {
     *         SkFlagInfo* info = new SkFlagInfo(name, nullptr, kInt_FlagType, helpString, nullptr);
     *         info->fIntValue  = pInt;
     *         *info->fIntValue = info->fDefaultInt = defaultValue;
     *         return true;
     *     }
     * ```
     */
    public fun createIntFlag(
      name: String?,
      pInt: Int?,
      defaultValue: Int,
      helpString: String?,
    ): Boolean {
      TODO("Implement createIntFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool CreateIntFlag(const char* name,
     *                               const char* shortName,
     *                               int*    pInt,
     *                               int     defaultValue,
     *                               const char* helpString) {
     *         SkFlagInfo* info = new SkFlagInfo(name, shortName, kInt_FlagType, helpString, nullptr);
     *         info->fIntValue  = pInt;
     *         *info->fIntValue = info->fDefaultInt = defaultValue;
     *         return true;
     *     }
     * ```
     */
    public fun createIntFlag(
      name: String?,
      shortName: String?,
      pInt: Int?,
      defaultValue: Int,
      helpString: String?,
    ): Boolean {
      TODO("Implement createIntFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * static bool CreateDoubleFlag(const char* name,
     *                                  double*     pDouble,
     *                                  double      defaultValue,
     *                                  const char* helpString) {
     *         SkFlagInfo* info    = new SkFlagInfo(name, nullptr, kDouble_FlagType, helpString, nullptr);
     *         info->fDoubleValue  = pDouble;
     *         *info->fDoubleValue = info->fDefaultDouble = defaultValue;
     *         return true;
     *     }
     * ```
     */
    public fun createDoubleFlag(
      name: String?,
      pDouble: Double?,
      defaultValue: Double,
      helpString: String?,
    ): Boolean {
      TODO("Implement createDoubleFlag")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkFlagInfo::SetDefaultStrings(CommandLineFlags::StringArray* pStrings,
     *                                    const char*                    defaultValue) {
     *     pStrings->reset();
     *     if (nullptr == defaultValue) {
     *         return;
     *     }
     *     // If default is "", leave the array empty.
     *     size_t defaultLength = strlen(defaultValue);
     *     if (defaultLength > 0) {
     *         const char* const defaultEnd = defaultValue + defaultLength;
     *         const char*       begin      = defaultValue;
     *         while (true) {
     *             while (begin < defaultEnd && ' ' == *begin) {
     *                 begin++;
     *             }
     *             if (begin < defaultEnd) {
     *                 const char* end = begin + 1;
     *                 while (end < defaultEnd && ' ' != *end) {
     *                     end++;
     *                 }
     *                 size_t length = end - begin;
     *                 pStrings->append(begin, length);
     *                 begin = end + 1;
     *             } else {
     *                 break;
     *             }
     *         }
     *     }
     * }
     * ```
     */
    private fun setDefaultStrings(array: CommandLineFlags.StringArray?, defaultStrings: String?) {
      TODO("Implement setDefaultStrings")
    }
  }
}
