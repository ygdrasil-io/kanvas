package org.skia.modules

import kotlin.Float
import org.skia.foundation.SkSp
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class ForwardingPlaybackController final : public Animator {
 * public:
 *     ForwardingPlaybackController(sk_sp<skresources::ExternalTrackAsset> track,
 *                                  float in_point,
 *                                  float out_point,
 *                                  float fps )
 *         : fTrack(std::move(track))
 *         , fInPoint(in_point)
 *         , fOutPoint(out_point)
 *         , fFps(fps) {}
 *
 * private:
 *     StateChanged onSeek(float t) override {
 *         // Adjust t relative to the track time (s).
 *         if (t < fInPoint || t > fOutPoint) {
 *             t = -1;
 *         } else {
 *             t = (t - fInPoint) / fFps;
 *         }
 *
 *         fTrack->seek(t);
 *
 *         // does not interact with the render tree.
 *         return false;
 *     }
 *
 *     const sk_sp<skresources::ExternalTrackAsset> fTrack;
 *     const float                                  fInPoint,
 *                                                  fOutPoint,
 *                                                  fFps;
 * }
 * ```
 */
public class ForwardingPlaybackController public constructor(
  track: SkSp<ExternalTrackAsset>,
  inPoint: Float,
  outPoint: Float,
  fps: Float,
) : Animator() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skresources::ExternalTrackAsset> fTrack
   * ```
   */
  private val fTrack: SkSp<ExternalTrackAsset> = TODO("Initialize fTrack")

  /**
   * C++ original:
   * ```cpp
   * const float                                  fInPoint
   * ```
   */
  private val fInPoint: Float = TODO("Initialize fInPoint")

  /**
   * C++ original:
   * ```cpp
   * const float                                  fInPoint,
   *                                                  fOutPoint
   * ```
   */
  private val fOutPoint: Float = TODO("Initialize fOutPoint")

  /**
   * C++ original:
   * ```cpp
   * const float                                  fInPoint,
   *                                                  fOutPoint,
   *                                                  fFps
   * ```
   */
  private val fFps: Float = TODO("Initialize fFps")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         // Adjust t relative to the track time (s).
   *         if (t < fInPoint || t > fOutPoint) {
   *             t = -1;
   *         } else {
   *             t = (t - fInPoint) / fFps;
   *         }
   *
   *         fTrack->seek(t);
   *
   *         // does not interact with the render tree.
   *         return false;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
