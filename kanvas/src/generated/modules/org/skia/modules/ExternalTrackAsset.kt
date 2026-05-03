package org.skia.modules

import kotlin.Float
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class ExternalTrackAsset : public SkRefCnt {
 * public:
 *     /**
 *      * Playback control callback, emitted for each corresponding Animation::seek().
 *      *
 *      * @param t  Frame time code, in seconds, relative to the layer's timeline origin
 *      *           (in-point).
 *      *
 *      * Negative |t| values are used to signal off state (stop playback outside layer span).
 *      */
 *     virtual void seek(float t) = 0;
 * }
 * ```
 */
public abstract class ExternalTrackAsset : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void seek(float t) = 0
   * ```
   */
  public abstract fun seek(t: Float)
}
