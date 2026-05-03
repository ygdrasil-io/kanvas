package org.skia.tests

import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkPictureRecorderReplayTester {
 * public:
 *     static sk_sp<SkPicture> Copy(SkPictureRecorder* recorder) {
 *         SkPictureRecorder recorder2;
 *
 *         SkCanvas* canvas = recorder2.beginRecording(10, 10);
 *
 *         recorder->partialReplay(canvas);
 *
 *         return recorder2.finishRecordingAsPicture();
 *     }
 * }
 * ```
 */
public open class SkPictureRecorderReplayTester {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkPicture> Copy(SkPictureRecorder* recorder) {
     *         SkPictureRecorder recorder2;
     *
     *         SkCanvas* canvas = recorder2.beginRecording(10, 10);
     *
     *         recorder->partialReplay(canvas);
     *
     *         return recorder2.finishRecordingAsPicture();
     *     }
     * ```
     */
    public fun copy(recorder: SkPictureRecorder?): SkSp<SkPicture> {
      TODO("Implement copy")
    }
  }
}
