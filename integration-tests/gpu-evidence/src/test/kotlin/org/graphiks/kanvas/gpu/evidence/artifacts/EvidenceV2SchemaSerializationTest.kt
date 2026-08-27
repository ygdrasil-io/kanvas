package org.graphiks.kanvas.gpu.evidence.artifacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.graphiks.kanvas.gpu.evidence.catalog.EvidenceAdapter

class EvidenceV2SchemaSerializationTest {
    @Test
    fun `v2 constants keep stable schema identifiers`() {
        assertEquals("gpu-evidence-catalog-v2", GPU_EVIDENCE_CATALOG_SCHEMA_V2)
        assertEquals("gpu-evidence-scene-v2", GPU_EVIDENCE_SCENE_SCHEMA_V2)
        assertEquals("gpu-evidence-promotion-v2", GPU_EVIDENCE_PROMOTION_SCHEMA_V2)
    }

    @Test
    fun `v2 scene manifest serializes canonical scene-only fields`() {
        val manifest = EvidenceManifestV2(
            schemaVersion = GPU_EVIDENCE_SCENE_SCHEMA_V2,
            sceneId = "render-scene",
            expectation = "render",
            observedOutcome = "rendered",
            oracleKind = "checked-in-png",
            oracleId = "reference.png",
            oracleVersion = 1,
            files = mapOf("verdict.json" to "hash-verdict", "gpu.png" to "hash-gpu"),
            oracleProvenance = "release-reference",
            oracleSha256 = "oracle-sha",
        )

        assertEquals(
            """{"schemaVersion":"gpu-evidence-scene-v2","sceneId":"render-scene","expectation":"render","observedOutcome":"rendered","oracleKind":"checked-in-png","oracleId":"reference.png","oracleVersion":1,"oracleProvenance":"release-reference","oracleSha256":"oracle-sha","files":{"gpu.png":"hash-gpu","verdict.json":"hash-verdict"}}""",
            manifest.toJson().canonicalBytes().decodeToString(),
        )

        val json = EvidenceJson.parseToJsonElement(manifest.toJson().canonicalBytes().decodeToString()).jsonObject
        assertFalse("sourceCommit" in json)
        assertFalse("generatedAtUtc" in json)
        assertFalse("environment" in json)
        assertFalse("promotion" in json)
    }

    @Test
    fun `v2 root models serialize deterministically with sorted scene and file data`() {
        val catalog = EvidenceCatalogV2(
            schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
            environment = "environment.json",
            promotion = null,
            scenes = listOf(
                EvidenceCatalogEntry(
                    sceneId = "solid-triangle-path",
                    sourceCommit = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    manifest = "solid-triangle-path/manifest.json",
                    manifestSha256 = "2222222222222222222222222222222222222222222222222222222222222222",
                ),
                EvidenceCatalogEntry(
                    sceneId = "solid-card-stack",
                    sourceCommit = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    manifest = "solid-card-stack/manifest.json",
                    manifestSha256 = "1111111111111111111111111111111111111111111111111111111111111111",
                ),
            ),
        )
        val environment = EvidenceEnvironmentV2(
            schemaVersion = GPU_EVIDENCE_CATALOG_SCHEMA_V2,
            osName = "TestOS",
            osVersion = "42",
            osArchitecture = "arm64",
            javaVersion = "25",
            deviceGeneration = 77L,
            capabilityImplementation = "native",
            available = true,
            adapter = EvidenceAdapter("summary", "vendor", "device", "architecture", "description", false),
        )
        val promotion = EvidencePromotionV2(
            schemaVersion = GPU_EVIDENCE_PROMOTION_SCHEMA_V2,
            promotedAtUtc = "2026-08-26T15:55:32Z",
            reviewer = "reviewer",
            reason = "reason",
            rebaseline = true,
            sceneIds = listOf("solid-triangle-path", "solid-card-stack"),
            priorComparison = "old=100.0",
            newComparison = "new=100.0",
        )

        assertEquals(
            """{"schemaVersion":"gpu-evidence-catalog-v2","environment":"environment.json","promotion":null,"scenes":[{"sceneId":"solid-card-stack","sourceCommit":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","manifest":"solid-card-stack/manifest.json","manifestSha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"sceneId":"solid-triangle-path","sourceCommit":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","manifest":"solid-triangle-path/manifest.json","manifestSha256":"2222222222222222222222222222222222222222222222222222222222222222"}]}""",
            catalog.toJson().canonicalBytes().decodeToString(),
        )
        assertEquals(
            """{"schemaVersion":"gpu-evidence-catalog-v2","osName":"TestOS","osVersion":"42","osArchitecture":"arm64","javaVersion":"25","deviceGeneration":77,"capabilityImplementation":"native","available":true,"adapter":{"summary":"summary","vendor":"vendor","device":"device","architecture":"architecture","description":"description","isFallbackAdapter":false}}""",
            environment.toJson().canonicalBytes().decodeToString(),
        )
        assertEquals(
            """{"schemaVersion":"gpu-evidence-promotion-v2","promotedAtUtc":"2026-08-26T15:55:32Z","reviewer":"reviewer","reason":"reason","rebaseline":true,"sceneIds":["solid-card-stack","solid-triangle-path"],"priorComparison":"old=100.0","newComparison":"new=100.0"}""",
            promotion.toJson().canonicalBytes().decodeToString(),
        )

        val json = EvidenceJson.parseToJsonElement(promotion.toJson().canonicalBytes().decodeToString()).jsonObject
        assertEquals(listOf("solid-card-stack", "solid-triangle-path"), json["sceneIds"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(true, EvidenceJson.parseToJsonElement(environment.toJson().canonicalBytes().decodeToString()).jsonObject["available"]!!.jsonPrimitive.boolean)
    }
}
