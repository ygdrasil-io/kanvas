package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.IntArray
import kotlin.Unit
import org.skia.gpu.BackendTexture

/**
 * C++ original:
 * ```cpp
 * struct PromiseTextureChecker {
 *     PromiseTextureChecker() = default;
 *
 *     explicit PromiseTextureChecker(const BackendTexture& backendTex) {
 *         fBackendTextures[0] = backendTex;
 *     }
 *
 *     explicit PromiseTextureChecker(const BackendTexture& backendTex0,
 *                                    const BackendTexture& backendTex1)
 *             : fHasTwoBackendTextures(true) {
 *         fBackendTextures[0] = backendTex0;
 *         fBackendTextures[1] = backendTex1;
 *     }
 *
 *     int totalReleaseCount() const { return fTextureReleaseCounts[0] + fTextureReleaseCounts[1]; }
 *
 *     bool fHasTwoBackendTextures = false;
 *     BackendTexture fBackendTextures[2];
 *     int fFulfillCount = 0;
 *     int fTextureReleaseCounts[2] = { 0, 0 };
 *
 *     static std::tuple<BackendTexture, void*> Fulfill(void* self) {
 *         auto checker = reinterpret_cast<PromiseTextureChecker*>(self);
 *
 *         checker->fFulfillCount++;
 *
 *         if (checker->fHasTwoBackendTextures) {
 *             int whichToUse = checker->fFulfillCount % 2;
 *             return { checker->fBackendTextures[whichToUse],
 *                      &checker->fTextureReleaseCounts[whichToUse] };
 *         } else {
 *             return { checker->fBackendTextures[0], &checker->fTextureReleaseCounts[0] };
 *         }
 *     }
 *
 *     static void TextureRelease(void* context) {
 *         int* releaseCount = reinterpret_cast<int*>(context);
 *
 *         (*releaseCount)++;
 *     }
 * }
 * ```
 */
public data class PromiseTextureChecker public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fHasTwoBackendTextures = false
   * ```
   */
  public var fHasTwoBackendTextures: Boolean,
  /**
   * C++ original:
   * ```cpp
   * BackendTexture fBackendTextures[2]
   * ```
   */
  public var fBackendTextures: Array<BackendTexture>,
  /**
   * C++ original:
   * ```cpp
   * int fFulfillCount = 0
   * ```
   */
  public var fFulfillCount: Int,
  /**
   * C++ original:
   * ```cpp
   * int fTextureReleaseCounts[2] = { 0, 0 }
   * ```
   */
  public var fTextureReleaseCounts: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   * int totalReleaseCount() const { return fTextureReleaseCounts[0] + fTextureReleaseCounts[1]; }
   * ```
   */
  public fun totalReleaseCount(): Int {
    TODO("Implement totalReleaseCount")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::tuple<BackendTexture, void*> Fulfill(void* self) {
     *         auto checker = reinterpret_cast<PromiseTextureChecker*>(self);
     *
     *         checker->fFulfillCount++;
     *
     *         if (checker->fHasTwoBackendTextures) {
     *             int whichToUse = checker->fFulfillCount % 2;
     *             return { checker->fBackendTextures[whichToUse],
     *                      &checker->fTextureReleaseCounts[whichToUse] };
     *         } else {
     *             return { checker->fBackendTextures[0], &checker->fTextureReleaseCounts[0] };
     *         }
     *     }
     * ```
     */
    public fun fulfill(self: Unit?): Int {
      TODO("Implement fulfill")
    }

    /**
     * C++ original:
     * ```cpp
     * static void TextureRelease(void* context) {
     *         int* releaseCount = reinterpret_cast<int*>(context);
     *
     *         (*releaseCount)++;
     *     }
     * ```
     */
    public fun textureRelease(context: Unit?) {
      TODO("Implement textureRelease")
    }
  }
}
