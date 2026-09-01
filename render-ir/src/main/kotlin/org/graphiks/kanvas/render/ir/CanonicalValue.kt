package org.graphiks.kanvas.render.ir

/** A stable content identity for a backend-neutral value. */
@JvmInline
public value class CanonicalId(public val value: String) {
    init {
        require(value.isNotBlank()) { "CanonicalId.value must not be blank" }
    }
}

/** A value whose identity depends only on its public rendering semantics. */
public interface CanonicalValue {
    public val canonicalId: CanonicalId
}

/** Encodes a scene independently of a particular renderer or native resource. */
public object CanonicalSceneEncoder {
    public fun encode(scene: SceneSnapshot): CanonicalId = canonicalId(
        "scene-v1",
        scene.extent.canonicalId.value,
        scene.colorSpace.name,
        scene.colorSpace.transferFunction.name,
        scene.colorSpace.gamut.name,
        *scene.map { it.canonicalId.value }.toTypedArray(),
    )
}

internal fun canonicalId(tag: String, vararg fields: String): CanonicalId = CanonicalId(
    buildString {
        append(tag.length).append(':').append(tag)
        fields.forEach { field -> append('|').append(field.length).append(':').append(field) }
    },
)

internal fun Float.canonicalBits(): String = toBits().toString()
