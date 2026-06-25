package org.graphiks.kanvas.api

import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.recording.GPURecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID

class KanvasSurface(
    val width: Int,
    val height: Int,
    val format: KanvasPixelFormat = KanvasPixelFormat.RGBA8,
) {
    internal val recorder: GPURecorder = GPURecorder(
        recordingId = GPURecordingID("kanvas-surface-${System.identityHashCode(this)}"),
    )

    internal val targetFacts: GPUTargetFacts = GPUTargetFacts(
        width = width,
        height = height,
        colorFormat = format.label,
    )

    fun flush(): KanvasFrame {
        val recording: GPURecording = recorder.close()
        return KanvasFrame(recording = recording)
    }
}
