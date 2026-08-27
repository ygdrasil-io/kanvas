package org.graphiks.kanvas.gpu.evidence.artifacts

import java.nio.file.Files
import java.nio.file.Path
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceCase

sealed interface EvidenceSelection {
    data object All : EvidenceSelection

    class Explicit(sceneIds: List<String>) : EvidenceSelection {
        val sceneIds: List<String> = normalize(sceneIds)

        override fun equals(other: Any?): Boolean = other is Explicit && sceneIds == other.sceneIds

        override fun hashCode(): Int = sceneIds.hashCode()

        override fun toString(): String = "Explicit(sceneIds=$sceneIds)"

        companion object {
            private fun normalize(sceneIds: List<String>): List<String> {
                require(sceneIds.isNotEmpty()) { "explicit evidence selection must not be empty" }
                val duplicates = sceneIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
                require(duplicates.isEmpty()) { "duplicate evidence scene ids: ${duplicates.joinToString(",")}" }
                return sceneIds.toList().sorted()
            }
        }
    }
}

object EvidenceSelectionParser {
    fun from(sceneIds: List<String>, all: Boolean): EvidenceSelection {
        val normalized = sceneIds.map(String::trim).filter(String::isNotBlank)
        require(!(all && normalized.isNotEmpty())) { "--all cannot be combined with explicit scene ids" }
        if (all) return EvidenceSelection.All
        require(normalized.isNotEmpty()) { "explicit evidence selection must not be empty" }
        val duplicates = normalized.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        require(duplicates.isEmpty()) { "duplicate evidence scene ids: ${duplicates.joinToString(",")}" }
        return EvidenceSelection.Explicit(normalized.toList().sorted())
    }

    fun readSceneFile(path: Path): List<String> {
        val sceneIds = Files.readAllLines(path).map(String::trim).filter(String::isNotBlank)
        require(sceneIds.isNotEmpty()) { "scene file must contain at least one evidence scene id" }
        val duplicates = sceneIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        require(duplicates.isEmpty()) { "duplicate evidence scene ids in file: ${duplicates.joinToString(",")}" }
        return sceneIds
    }
}

fun EvidenceSelection.resolve(cases: List<EvidenceCase>): List<EvidenceCase> = when (this) {
    EvidenceSelection.All -> cases
    is EvidenceSelection.Explicit -> {
        val byId = cases.associateBy { it.descriptor.id.value }
        sceneIds.map { id -> byId[id] ?: error("unknown evidence scene: $id") }
    }
}
