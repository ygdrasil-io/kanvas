package org.graphiks.kanvas.api

import org.graphiks.kanvas.gpu.renderer.recording.GPURecording

class KanvasFrame(
    val recording: GPURecording,
) {
    val isEmpty: Boolean get() = recording.taskList.tasks.isEmpty()
}
