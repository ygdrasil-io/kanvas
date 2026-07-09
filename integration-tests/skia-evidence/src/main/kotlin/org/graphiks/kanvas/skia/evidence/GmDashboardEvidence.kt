package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

data class GmDashboard(
    val generatedAt: String?,
    val rows: List<GmDashboardRow>,
)

data class GmRgbaInt(
    val r: Int,
    val g: Int,
    val b: Int,
    val a: Int,
)

data class GmRgbaDouble(
    val r: Double,
    val g: Double,
    val b: Double,
    val a: Double,
)

data class GmDashboardRow(
    val name: String,
    val family: String,
    val similarity: Double?,
    val minSimilarity: Double?,
    val isPassing: Boolean?,
    val width: Int?,
    val height: Int?,
    val maxDiff: GmRgbaInt?,
    val meanDiff: GmRgbaDouble?,
    val matchingPixels: Long?,
    val totalPixels: Long?,
    val noReference: Boolean,
    val renderFailed: Boolean,
    val sizeMismatch: Boolean,
    val hasDiff: Boolean,
    val referenceUntrustable: Boolean = false,
    val noScoreCause: String? = null,
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
            width = row.int("width"),
            height = row.int("height"),
            maxDiff = row.rgbaInt("maxDiff"),
            meanDiff = row.rgbaDouble("meanDiff"),
            matchingPixels = row.long("matchingPixels"),
            totalPixels = row.long("totalPixels"),
            noReference = row.boolean("noReference") ?: false,
            renderFailed = row.boolean("renderFailed") ?: false,
            sizeMismatch = row.boolean("sizeMismatch") ?: false,
            hasDiff = row.boolean("hasDiff") ?: false,
            referenceUntrustable = row.boolean("referenceUntrustable") ?: false,
            noScoreCause = row.string("noScoreCause"),
        )
}

internal fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

internal fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

internal fun JsonObject.long(key: String): Long? =
    this[key]?.jsonPrimitive?.longOrNull

internal fun JsonObject.childObject(key: String): JsonObject? =
    this[key] as? JsonObject

internal fun JsonObject.rgbaInt(key: String): GmRgbaInt? =
    childObject(key)?.let { value ->
        GmRgbaInt(
            r = value.int("r") ?: error("Missing `$key.r`"),
            g = value.int("g") ?: error("Missing `$key.g`"),
            b = value.int("b") ?: error("Missing `$key.b`"),
            a = value.int("a") ?: error("Missing `$key.a`"),
        )
    }

internal fun JsonObject.rgbaDouble(key: String): GmRgbaDouble? =
    childObject(key)?.let { value ->
        GmRgbaDouble(
            r = value.double("r") ?: error("Missing `$key.r`"),
            g = value.double("g") ?: error("Missing `$key.g`"),
            b = value.double("b") ?: error("Missing `$key.b`"),
            a = value.double("a") ?: error("Missing `$key.a`"),
        )
    }

internal fun JsonObject.stringArray(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { element -> element.jsonPrimitive.contentOrNull }
