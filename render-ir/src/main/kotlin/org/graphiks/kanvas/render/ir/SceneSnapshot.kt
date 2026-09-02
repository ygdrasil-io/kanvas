package org.graphiks.kanvas.render.ir

import org.graphiks.kanvas.color.ColorSpace

/** Pixel extent of a scene or logical render target. */
public data class SceneExtent(
    public val width: Int,
    public val height: Int,
) : CanonicalValue {
    init {
        require(width > 0) { "SceneExtent.width must be positive" }
        require(height > 0) { "SceneExtent.height must be positive" }
    }

    override val canonicalId: CanonicalId = canonicalId("scene-extent-v1", width.toString(), height.toString())
}

/** Immutable, ordered scene input for a [RenderBackend]. */
public class SceneSnapshot private constructor(
    public val extent: SceneExtent,
    public val colorSpace: ColorSpace,
    commands: Collection<SceneCommand>,
) : Iterable<SceneCommand>, CanonicalValue {
    private val values: List<SceneCommand> = immutableList(commands)

    public val commandCount: Int get() = values.size

    public fun commandAt(index: Int): SceneCommand = values[index]

    override fun iterator(): Iterator<SceneCommand> = values.iterator()

    override val canonicalId: CanonicalId = CanonicalSceneEncoder.encode(this)

    public companion object {
        public fun of(
            extent: SceneExtent,
            colorSpace: ColorSpace,
            commands: Collection<SceneCommand>,
        ): SceneSnapshot = SceneSnapshot(extent, colorSpace, commands)
    }
}
