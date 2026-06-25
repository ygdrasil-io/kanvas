package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.recording.GPURecording

class Frame(
    val recording: GPURecording,
) {
    val isEmpty: Boolean get() = recording.taskList.tasks.isEmpty()
}
