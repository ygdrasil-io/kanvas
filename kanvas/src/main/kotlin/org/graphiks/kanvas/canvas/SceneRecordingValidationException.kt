package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.render.ir.RenderDiagnostic

/** A rejected append retains neither payload nor pending recording budget. */
public class SceneRecordingValidationException(public val diagnostic: RenderDiagnostic) :
    IllegalArgumentException("${diagnostic.code.value}: ${diagnostic.message}")
