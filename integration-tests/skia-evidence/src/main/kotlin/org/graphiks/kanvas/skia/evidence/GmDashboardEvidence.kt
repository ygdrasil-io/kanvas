package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GmDashboard(
    val generatedAt: String?,
    val rows: List<GmDashboardRow>,
)

data class GmDashboardRow(
    val name: String,
    val family: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val noReference: Boolean,
    val renderFailed: Boolean,
    val sizeMismatch: Boolean,
    val hasDiff: Boolean,
)

object GmDashboardJsonReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(path: Path): GmDashboard {
        val root = json.parseToJsonElement(Files.readString(path)).jsonObject
        val rows = root["gms"]?.jsonArray.orEmpty().map { element -> parseRow(element.jsonObject) }
        return GmDashboard(
            generatedAt = root["generatedAt"]?.jsonPrimitive?.contentOrNull,
            rows = rows,
        )
    }

    private fun parseRow(row: JsonObject): GmDashboardRow =
        GmDashboardRow(
            name = row.string("name") ?: error("GM dashboard row missing name"),
            family = row.string("family") ?: error("GM dashboard row missing family"),
            similarity = row.double("similarity"),
            minSimilarity = row.double("minSimilarity"),
            isPassing = row.boolean("isPassing"),
            noReference = row.boolean("noReference") ?: false,
            renderFailed = row.boolean("renderFailed") ?: false,
            sizeMismatch = row.boolean("sizeMismatch") ?: false,
            hasDiff = row.boolean("hasDiff") ?: false,
        )
}

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.stringArray(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element -> element.jsonPrimitive.contentOrNull }
