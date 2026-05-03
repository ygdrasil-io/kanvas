package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class ConditionalUploadContext {
 * public:
 *     virtual ~ConditionalUploadContext() {}
 *
 *     // Return true if the upload needs to occur; false if it should be skipped this time.
 *     virtual bool needsUpload(Context*) = 0;
 *
 *     // Return true if the upload should be kept in the task (and possibly re-executed on replay
 *     // depending on needsUpload()'s return value), or false if it should be discarded and never
 *     // attempt to be uploaded on any replay.
 *     virtual bool uploadSubmitted() { return true; }
 * }
 * ```
 */
public abstract class ConditionalUploadContext {
  /**
   * C++ original:
   * ```cpp
   * virtual bool needsUpload(Context*) = 0
   * ```
   */
  public abstract fun needsUpload(param0: Context?): Boolean

  /**
   * C++ original:
   * ```cpp
   * virtual bool uploadSubmitted() { return true; }
   * ```
   */
  public open fun uploadSubmitted(): Boolean {
    TODO("Implement uploadSubmitted")
  }
}
