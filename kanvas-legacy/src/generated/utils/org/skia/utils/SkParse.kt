package org.skia.utils

import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkColor
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SK_API SkParse {
 * public:
 *     static int Count(const char str[]); // number of scalars or int values
 *     static int Count(const char str[], char separator);
 *     static const char* FindColor(const char str[], SkColor* value);
 *     static const char* FindHex(const char str[], uint32_t* value);
 *     static const char* FindNamedColor(const char str[], size_t len, SkColor* color);
 *     static const char* FindS32(const char str[], int32_t* value);
 *     static const char* FindScalar(const char str[], SkScalar* value);
 *     static const char* FindScalars(const char str[], SkScalar value[], int count);
 *
 *     static bool FindBool(const char str[], bool* value);
 *     // return the index of str in list[], or -1 if not found
 *     static int  FindList(const char str[], const char list[]);
 * }
 * ```
 */
public open class SkParse {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * int SkParse::Count(const char str[])
     * {
     *     char c;
     *     int count = 0;
     *     goto skipLeading;
     *     do {
     *         count++;
     *         do {
     *             if ((c = *str++) == '\0')
     *                 goto goHome;
     *         } while (is_sep(c) == false);
     * skipLeading:
     *         do {
     *             if ((c = *str++) == '\0')
     *                 goto goHome;
     *         } while (is_sep(c));
     *     } while (true);
     * goHome:
     *     return count;
     * }
     * ```
     */
    public fun count(str: CharArray): Int {
      TODO("Implement count")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkParse::Count(const char str[], char separator)
     * {
     *     char c;
     *     int count = 0;
     *     goto skipLeading;
     *     do {
     *         count++;
     *         do {
     *             if ((c = *str++) == '\0')
     *                 goto goHome;
     *         } while (c != separator);
     * skipLeading:
     *         do {
     *             if ((c = *str++) == '\0')
     *                 goto goHome;
     *         } while (c == separator);
     *     } while (true);
     * goHome:
     *     return count;
     * }
     * ```
     */
    public fun count(str: CharArray, separator: Char): Int {
      TODO("Implement count")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindColor(const char* value, SkColor* colorPtr) {
     *     unsigned int oldAlpha = SkColorGetA(*colorPtr);
     *     if (value[0] == '#') {
     *         uint32_t    hex;
     *         const char* end = SkParse::FindHex(value + 1, &hex);
     * //      SkASSERT(end);
     *         if (end == nullptr)
     *             return end;
     *         size_t len = end - value - 1;
     *         if (len == 3 || len == 4) {
     *             unsigned a = len == 4 ? nib2byte(hex >> 12) : oldAlpha;
     *             unsigned r = nib2byte((hex >> 8) & 0xF);
     *             unsigned g = nib2byte((hex >> 4) & 0xF);
     *             unsigned b = nib2byte(hex & 0xF);
     *             *colorPtr = SkColorSetARGB(a, r, g, b);
     *             return end;
     *         } else if (len == 6 || len == 8) {
     *             if (len == 6)
     *                 hex |= oldAlpha << 24;
     *             *colorPtr = hex;
     *             return end;
     *         } else {
     * //          SkASSERT(0);
     *             return nullptr;
     *         }
     * //  } else if (strchr(value, ',')) {
     * //      SkScalar array[4];
     * //      int count = count_separators(value, ",") + 1; // !!! count commas, add 1
     * //      SkASSERT(count == 3 || count == 4);
     * //      array[0] = SK_Scalar1 * 255;
     * //      const char* end = SkParse::FindScalars(value, &array[4 - count], count);
     * //      if (end == nullptr)
     * //          return nullptr;
     *         // !!! range check for errors?
     * //      *colorPtr = SkColorSetARGB(SkScalarRoundToInt(array[0]), SkScalarRoundToInt(array[1]),
     * //          SkScalarRoundToInt(array[2]), SkScalarRoundToInt(array[3]));
     * //      return end;
     *     } else
     *         return FindNamedColor(value, strlen(value), colorPtr);
     * }
     * ```
     */
    public fun findColor(str: CharArray, `value`: SkColor?): Char {
      TODO("Implement findColor")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindHex(const char str[], uint32_t* value)
     * {
     *     SkASSERT(str);
     *     str = skip_ws(str);
     *
     *     if (!is_hex(*str))
     *         return nullptr;
     *
     *     uint32_t n = 0;
     *     int max_digits = 8;
     *     int digit;
     *
     *     while ((digit = to_hex(*str)) >= 0)
     *     {
     *         if (--max_digits < 0)
     *             return nullptr;
     *         n = (n << 4) | digit;
     *         str += 1;
     *     }
     *
     *     if (*str == 0 || is_ws(*str))
     *     {
     *         if (value)
     *             *value = n;
     *         return str;
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public fun findHex(str: CharArray, `value`: UInt?): Char {
      TODO("Implement findHex")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindNamedColor(const char* name, size_t len, SkColor* color) {
     *     const auto rec = std::lower_bound(std::begin(gColorNames),
     *                                       std::end  (gColorNames),
     *                                       name, // key
     *                                       [](const char* name, const char* key) {
     *                                           return strcmp(name, key) < 0;
     *                                       });
     *
     *     if (rec == std::end(gColorNames) || 0 != strcmp(name, *rec)) {
     *         return nullptr;
     *     }
     *
     *     if (color) {
     *         int index = rec - gColorNames;
     *         *color = SkColorSetRGB(gColors[index].r, gColors[index].g, gColors[index].b);
     *     }
     *
     *     return name + strlen(*rec);
     * }
     * ```
     */
    public fun findNamedColor(
      str: CharArray,
      len: ULong,
      color: SkColor?,
    ): Char {
      TODO("Implement findNamedColor")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindS32(const char str[], int32_t* value)
     * {
     *     SkASSERT(str);
     *     str = skip_ws(str);
     *
     *     int sign = 1;
     *     int64_t maxAbsValue = std::numeric_limits<int>::max();
     *     if (*str == '-')
     *     {
     *         sign = -1;
     *         maxAbsValue = -static_cast<int64_t>(std::numeric_limits<int>::min());
     *         str += 1;
     *     }
     *
     *     if (!is_digit(*str)) {
     *         return nullptr;
     *     }
     *
     *     int64_t n = 0;
     *     while (is_digit(*str))
     *     {
     *         n = 10*n + *str - '0';
     *         if (n > maxAbsValue) {
     *             return nullptr;
     *         }
     *
     *         str += 1;
     *     }
     *     if (value) {
     *         *value = SkToS32(sign*n);
     *     }
     *     return str;
     * }
     * ```
     */
    public fun findS32(str: CharArray, `value`: Int?): Char {
      TODO("Implement findS32")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindScalar(const char str[], SkScalar* value) {
     *     SkASSERT(str);
     *     str = skip_ws(str);
     *
     *     char* stop;
     *     float v = (float)strtod(str, &stop);
     *     if (str == stop) {
     *         return nullptr;
     *     }
     *     if (value) {
     *         *value = v;
     *     }
     *     return stop;
     * }
     * ```
     */
    public fun findScalar(str: CharArray, `value`: SkScalar?): Char {
      TODO("Implement findScalar")
    }

    /**
     * C++ original:
     * ```cpp
     * const char* SkParse::FindScalars(const char str[], SkScalar value[], int count)
     * {
     *     SkASSERT(count >= 0);
     *
     *     if (count > 0)
     *     {
     *         for (;;)
     *         {
     *             str = SkParse::FindScalar(str, value);
     *             if (--count == 0 || str == nullptr)
     *                 break;
     *
     *             // keep going
     *             str = skip_sep(str);
     *             if (value)
     *                 value += 1;
     *         }
     *     }
     *     return str;
     * }
     * ```
     */
    public fun findScalars(
      str: CharArray,
      `value`: Array<SkScalar>,
      count: Int,
    ): Char {
      TODO("Implement findScalars")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkParse::FindBool(const char str[], bool* value)
     * {
     *     static const char* gYes[] = { "yes", "1", "true" };
     *     static const char* gNo[] = { "no", "0", "false" };
     *
     *     if (lookup_str(str, gYes, std::size(gYes)))
     *     {
     *         if (value) *value = true;
     *         return true;
     *     }
     *     else if (lookup_str(str, gNo, std::size(gNo)))
     *     {
     *         if (value) *value = false;
     *         return true;
     *     }
     *     return false;
     * }
     * ```
     */
    public fun findBool(str: CharArray, `value`: Boolean?): Boolean {
      TODO("Implement findBool")
    }

    /**
     * C++ original:
     * ```cpp
     * int SkParse::FindList(const char target[], const char list[])
     * {
     *     size_t  len = strlen(target);
     *     int     index = 0;
     *
     *     for (;;)
     *     {
     *         const char* end = strchr(list, ',');
     *         size_t      entryLen;
     *
     *         if (end == nullptr) // last entry
     *             entryLen = strlen(list);
     *         else
     *             entryLen = end - list;
     *
     *         if (entryLen == len && memcmp(target, list, len) == 0)
     *             return index;
     *         if (end == nullptr)
     *             break;
     *
     *         list = end + 1; // skip the ','
     *         index += 1;
     *     }
     *     return -1;
     * }
     * ```
     */
    public fun findList(str: CharArray, list: CharArray): Int {
      TODO("Implement findList")
    }
  }
}
