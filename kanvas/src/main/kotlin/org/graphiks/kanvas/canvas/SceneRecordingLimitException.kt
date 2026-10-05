package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.render.ir.RenderDiagnostic

/** A recording budget refusal; the rejected operation is not appended. */
public class SceneRecordingLimitException(
    public val diagnostic: RenderDiagnostic,
    public val limitI32: Int,
    public val requestedI64: Long,
) : IllegalArgumentException(diagnostic.message)
