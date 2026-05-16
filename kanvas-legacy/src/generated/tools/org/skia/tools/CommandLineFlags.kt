package org.skia.tools

import kotlin.Boolean
import kotlin.Char
import kotlin.Int
import kotlin.String
import kotlin.ULong
import org.skia.core.THashMap
import org.skia.memory.SkTDArray

/**
 * C++ original:
 * ```cpp
 * class CommandLineFlags {
 * public:
 *     /**
 *      *  Call to set the help message to be displayed. Should be called before
 *      *  Parse.
 *      */
 *     static void SetUsage(const char* usage);
 *
 *     /**
 *      *  Call this to display the help message. Should be called after SetUsage.
 *      */
 *     static void PrintUsage();
 *
 *     /**
 *      *  Call at the beginning of main to parse flags created by DEFINE_x, above.
 *      *  Must only be called once.
 *      */
 *     static void Parse(int argc, const char* const* argv);
 *
 *     /**
 *      *  Custom class for holding the arguments for a string flag.
 *      *  Publicly only has accessors so the strings cannot be modified.
 *      */
 *     class StringArray {
 *     public:
 *         StringArray() {}
 *         explicit StringArray(const skia_private::TArray<SkString>& strings) : fStrings(strings) {}
 *         const char* operator[](int i) const {
 *             SkASSERT(i >= 0 && i < fStrings.size());
 *             return fStrings[i].c_str();
 *         }
 *
 *         int size() const { return fStrings.size(); }
 *
 *         bool isEmpty() const { return this->size() == 0; }
 *
 *         /**
 *          * Returns true iff string is equal to one of the strings in this array.
 *          */
 *         bool contains(const char* string) const {
 *             for (int i = 0; i < fStrings.size(); i++) {
 *                 if (fStrings[i].equals(string)) {
 *                     return true;
 *                 }
 *             }
 *             return false;
 *         }
 *
 *         void set(int i, const char* str) {
 *             if (i >= fStrings.size()) {
 *                 this->append(str);
 *                 return;
 *             }
 *             fStrings[i].set(str);
 *         }
 *
 *         const SkString* begin() const { return fStrings.begin(); }
 *         const SkString* end() const { return fStrings.end(); }
 *
 *         /**
 *          * Parses and validates a string flag that requires exactly one value out of a set of
 *          * possible values. Returns a non-empty message in the case of errors.
 *          */
 *         template <class E>
 *         SkString parseAndValidate(const char* name,
 *                                   const skia_private::THashMap<SkString, E>& possibleValues,
 *                                   E* out) const {
 *             if (size() == 0) {
 *                 return SkStringPrintf("Flag %s is required.", name);
 *             }
 *             if (size() != 1) {
 *                 return SkStringPrintf("Flag %s takes 1 value, got %d.", name, size());
 *             }
 *             E* found = possibleValues.find(SkString(operator[](0)));
 *             if (found != nullptr) {
 *                 *out = *found;
 *                 return SkString();
 *             }
 *             return SkStringPrintf("Unknown value for flag %s: %s.", name, operator[](0));
 *         }
 *
 *     private:
 *         void reset() { fStrings.clear(); }
 *
 *         void append(const char* string) { fStrings.push_back().set(string); }
 *
 *         void append(const char* string, size_t length) { fStrings.push_back().set(string, length); }
 *
 *         skia_private::TArray<SkString> fStrings;
 *
 *         friend class SkFlagInfo;
 *     };
 *
 *     /* Takes a list of the form [~][^]match[$]
 *      ~ causes a matching test to always be skipped
 *      ^ requires the start of the test to match
 *      $ requires the end of the test to match
 *      ^ and $ requires an exact match
 *      If a test does not match any list entry, it is skipped unless some list entry starts with ~
 *     */
 *     static bool ShouldSkip(const SkTDArray<const char*>& strings, const char* name);
 *     static bool ShouldSkip(const StringArray& strings, const char* name);
 *
 * private:
 *     static SkFlagInfo* gHead;
 *     static SkString    gUsage;
 *
 *     // For access to gHead.
 *     friend class SkFlagInfo;
 * }
 * ```
 */
public open class CommandLineFlags {
  public data class StringArray public constructor(
    private var fStrings: Int,
  ) {
    public operator fun `get`(i: Int): Char {
      TODO("Implement get")
    }

    public fun size(): Int {
      TODO("Implement size")
    }

    public fun isEmpty(): Boolean {
      TODO("Implement isEmpty")
    }

    public fun contains(string: String?): Boolean {
      TODO("Implement contains")
    }

    public fun `set`(i: Int, str: String?) {
      TODO("Implement set")
    }

    public fun begin(): String {
      TODO("Implement begin")
    }

    public fun end(): String {
      TODO("Implement end")
    }

    public fun <E> parseAndValidate(
      name: String?,
      possibleValues: THashMap<String, E>,
      `out`: E?,
    ): String {
      TODO("Implement parseAndValidate")
    }

    private fun reset() {
      TODO("Implement reset")
    }

    private fun append(string: String?) {
      TODO("Implement append")
    }

    private fun append(string: String?, length: ULong) {
      TODO("Implement append")
    }
  }

  public companion object {
    private var gHead: SkFlagInfo? = TODO("Initialize gHead")

    private var gUsage: String = TODO("Initialize gUsage")

    /**
     * C++ original:
     * ```cpp
     * void CommandLineFlags::SetUsage(const char* usage) { gUsage.set(usage); }
     * ```
     */
    public fun setUsage(usage: String?) {
      TODO("Implement setUsage")
    }

    /**
     * C++ original:
     * ```cpp
     * void CommandLineFlags::PrintUsage() { SkDebugf("%s", gUsage.c_str()); }
     * ```
     */
    public fun printUsage() {
      TODO("Implement printUsage")
    }

    /**
     * C++ original:
     * ```cpp
     * void CommandLineFlags::Parse(int argc, const char* const* argv) {
     *     // Only allow calling this function once.
     *     static bool gOnce;
     *     if (gOnce) {
     *         SkDebugf("Parse should only be called once at the beginning of main!\n");
     *         SkASSERT(false);
     *         return;
     *     }
     *     gOnce = true;
     *
     *     bool helpPrinted  = false;
     *     bool flagsPrinted = false;
     *     // Loop over argv, starting with 1, since the first is just the name of the program.
     *     for (int i = 1; i < argc; i++) {
     *         if (0 == strcmp("-h", argv[i]) || 0 == strcmp("--help", argv[i])) {
     *             // Print help message.
     *             SkTDArray<const char*> helpFlags;
     *             for (int j = i + 1; j < argc; j++) {
     *                 if (SkStrStartsWith(argv[j], '-')) {
     *                     break;
     *                 }
     *                 helpFlags.append(1, &argv[j]);
     *             }
     *             if (0 == helpFlags.size()) {
     *                 // Only print general help message if help for specific flags is not requested.
     *                 SkDebugf("%s\n%s\n", argv[0], gUsage.c_str());
     *             }
     *             if (!flagsPrinted) {
     *                 SkDebugf("Flags:\n");
     *                 flagsPrinted = true;
     *             }
     *             if (0 == helpFlags.size()) {
     *                 // If no flags followed --help, print them all
     *                 SkTDArray<SkFlagInfo*> allFlags;
     *                 for (SkFlagInfo* flag = CommandLineFlags::gHead; flag; flag = flag->next()) {
     *                     allFlags.push_back(flag);
     *                 }
     *                 SkTQSort(allFlags.begin(), allFlags.end(), CompareFlagsByName());
     *                 for (SkFlagInfo* flag : allFlags) {
     *                     print_help_for_flag(flag);
     *                     if (flag->extendedHelp().size() > 0) {
     *                         SkDebugf("        Use '--help %s' for more information.\n",
     *                                  flag->name().c_str());
     *                     }
     *                 }
     *             } else {
     *                 for (SkFlagInfo* flag = CommandLineFlags::gHead; flag; flag = flag->next()) {
     *                     for (int k = 0; k < helpFlags.size(); k++) {
     *                         if (flag->name().equals(helpFlags[k]) ||
     *                             flag->shortName().equals(helpFlags[k])) {
     *                             print_extended_help_for_flag(flag);
     *                             helpFlags.remove(k);
     *                             break;
     *                         }
     *                     }
     *                 }
     *             }
     *             if (helpFlags.size() > 0) {
     *                 SkDebugf("Requested help for unrecognized flags:\n");
     *                 for (int k = 0; k < helpFlags.size(); k++) {
     *                     SkDebugf("    --%s\n", helpFlags[k]);
     *                 }
     *             }
     *             helpPrinted = true;
     *         }
     *         if (!helpPrinted) {
     *             SkFlagInfo* matchedFlag = nullptr;
     *             SkFlagInfo* flag        = gHead;
     *             int         startI      = i;
     *             while (flag != nullptr) {
     *                 if (flag->match(argv[startI])) {
     *                     i = startI;
     *                     if (matchedFlag) {
     *                         // Don't redefine the same flag with different types.
     *                         SkASSERT(matchedFlag->getFlagType() == flag->getFlagType());
     *                     } else {
     *                         matchedFlag = flag;
     *                     }
     *                     switch (flag->getFlagType()) {
     *                         case SkFlagInfo::kBool_FlagType:
     *                             // Can be handled by match, above, but can also be set by the next
     *                             // string.
     *                             if (i + 1 < argc && !SkStrStartsWith(argv[i + 1], '-')) {
     *                                 i++;
     *                                 bool value;
     *                                 if (parse_bool_arg(argv[i], &value)) {
     *                                     flag->setBool(value);
     *                                 }
     *                             }
     *                             break;
     *                         case SkFlagInfo::kString_FlagType:
     *                             flag->resetStrings();
     *                             // Add all arguments until another flag is reached.
     *                             while (i + 1 < argc) {
     *                                 char* end = nullptr;
     *                                 // Negative numbers aren't flags.
     *                                 ignore_result(strtod(argv[i + 1], &end));
     *                                 if (end == argv[i + 1] && SkStrStartsWith(argv[i + 1], '-')) {
     *                                     break;
     *                                 }
     *                                 i++;
     *                                 flag->append(argv[i]);
     *                             }
     *                             break;
     *                         case SkFlagInfo::kInt_FlagType:
     *                             i++;
     *                             flag->setInt(atoi(argv[i]));
     *                             break;
     *                         case SkFlagInfo::kDouble_FlagType:
     *                             i++;
     *                             flag->setDouble(atof(argv[i]));
     *                             break;
     *                         default: SkDEBUGFAIL("Invalid flag type");
     *                     }
     *                 }
     *                 flag = flag->next();
     *             }
     *             if (!matchedFlag) {
     * #if defined(SK_BUILD_FOR_MAC)
     *                 if (SkStrStartsWith(argv[i], "NSDocumentRevisions") ||
     *                     SkStrStartsWith(argv[i], "-NSDocumentRevisions")) {
     *                     i++;  // skip YES
     *                 } else
     * #endif
     *                     SkDebugf("Got unknown flag '%s'. Exiting.\n", argv[i]);
     *                 exit(-1);
     *             }
     *         }
     *     }
     *     // Since all of the flags have been set, release the memory used by each
     *     // flag. FLAGS_x can still be used after this.
     *     SkFlagInfo* flag = gHead;
     *     gHead            = nullptr;
     *     while (flag != nullptr) {
     *         SkFlagInfo* next = flag->next();
     *         delete flag;
     *         flag = next;
     *     }
     *     if (helpPrinted) {
     *         exit(0);
     *     }
     * }
     * ```
     */
    public fun parse(argc: Int, argv: Int?) {
      TODO("Implement parse")
    }

    /**
     * C++ original:
     * ```cpp
     * bool CommandLineFlags::ShouldSkip(const SkTDArray<const char*>& strings, const char* name) {
     *     return ShouldSkipImpl(strings, name);
     * }
     * ```
     */
    private fun shouldSkip(strings: SkTDArray<String?>, name: String?): Boolean {
      TODO("Implement shouldSkip")
    }

    /**
     * C++ original:
     * ```cpp
     * bool CommandLineFlags::ShouldSkip(const StringArray& strings, const char* name) {
     *     return ShouldSkipImpl(strings, name);
     * }
     * ```
     */
    private fun shouldSkip(strings: StringArray, name: String?): Boolean {
      TODO("Implement shouldSkip")
    }
  }
}
