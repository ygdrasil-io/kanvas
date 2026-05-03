package org.skia.utils

import kotlin.Char
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkOSPath {
 * public:
 * #ifdef _WIN32
 *     static constexpr char SEPARATOR = '\\';
 * #else
 *     static constexpr char SEPARATOR = '/';
 * #endif
 *
 *     /**
 *      * Assembles rootPath and relativePath into a single path, like this:
 *      * rootPath/relativePath.
 *      * It is okay to call with a NULL rootPath and/or relativePath. A path
 *      * separator will still be inserted.
 *      *
 *      * Uses SkPATH_SEPARATOR, to work on all platforms.
 *      */
 *     static SkString Join(const char* rootPath, const char* relativePath);
 *
 *     /**
 *      *  Return the name of the file, ignoring the directory structure.
 *      *  Behaves like python's os.path.basename. If the fullPath is
 *      *  /dir/subdir/, an empty string is returned.
 *      *  @param fullPath Full path to the file.
 *      *  @return SkString The basename of the file - anything beyond the
 *      *      final slash, or the full name if there is no slash.
 *      */
 *     static SkString Basename(const char* fullPath);
 *
 *     /**
 *      *  Given a qualified file name returns the directory.
 *      *  Behaves like python's os.path.dirname. If the fullPath is
 *      *  /dir/subdir/ the return will be /dir/subdir/
 *      *  @param fullPath Full path to the file.
 *      *  @return SkString The dir containing the file - anything preceding the
 *      *      final slash, or the full name if ending in a slash.
 *      */
 *     static SkString Dirname(const char* fullPath);
 * }
 * ```
 */
public open class SkOSPath {
  public companion object {
    public val separator: Char = TODO("Initialize separator")

    /**
     * C++ original:
     * ```cpp
     * SkString SkOSPath::Join(const char *rootPath, const char *relativePath) {
     *     SkString result(rootPath);
     *     if (!result.endsWith(SEPARATOR) && !result.isEmpty()) {
     *         result.appendUnichar(SEPARATOR);
     *     }
     *     result.append(relativePath);
     *     return result;
     * }
     * ```
     */
    public fun join(rootPath: String?, relativePath: String?): String {
      TODO("Implement join")
    }

    /**
     * C++ original:
     * ```cpp
     * SkString SkOSPath::Basename(const char* fullPath) {
     *     if (!fullPath) {
     *         return SkString();
     *     }
     *     const char* filename = strrchr(fullPath, SEPARATOR);
     *     if (nullptr == filename) {
     *         filename = fullPath;
     *     } else {
     *         ++filename;
     *     }
     *     return SkString(filename);
     * }
     * ```
     */
    public fun basename(fullPath: String?): String {
      TODO("Implement basename")
    }

    /**
     * C++ original:
     * ```cpp
     * SkString SkOSPath::Dirname(const char* fullPath) {
     *     if (!fullPath) {
     *         return SkString();
     *     }
     *     const char* end = strrchr(fullPath, SEPARATOR);
     *     if (nullptr == end) {
     *         return SkString();
     *     }
     *     if (end == fullPath) {
     *         SkASSERT(fullPath[0] == SEPARATOR);
     *         ++end;
     *     }
     *     return SkString(fullPath, end - fullPath);
     * }
     * ```
     */
    public fun dirname(fullPath: String?): String {
      TODO("Implement dirname")
    }
  }
}
