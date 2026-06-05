#!/usr/bin/env python3
"""Validate the FOR-394 M60 F16 AA stencil-cover band metadata transport."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-394"
DECISION = "M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED"
CLASSIFICATION = "diagnostic-transport-added-not-connected"
METADATA_NAME = "sourceFacingLocalBandLane"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
RUNTIME_GUARD = "kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled"
OLD_PROBE_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-transporter-metadata-de-bande-aa-stencil-cover-apres-for-393"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-393-conclut-que-source-facing-local-band-lane-exige-de-nouveaux-uniformes-shader-lane-safe"
)

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-aa-stencil-cover-band-metadata-transport-for394.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-394-m60-f16-aa-stencil-cover-band-metadata-transport.md"
)
FOR393_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-shader-metadata-for393/"
    "m60-f16-source-facing-lane-shader-metadata-for393.json"
)
FOR392_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-runtime-opt-in-for392/"
    "m60-f16-source-facing-lane-runtime-opt-in-for392.json"
)
FOR391_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391/"
    "m60-f16-source-facing-lane-metadata-for391.json"
)
RENDERER_SOURCE = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
AA_STENCIL_COVER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

VALIDATION_COMMANDS = [
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    "rtk python3 scripts/validate_for394_m60_f16_aa_stencil_cover_band_metadata_transport.py",
    "rtk python3 scripts/validate_for393_m60_f16_source_facing_lane_shader_metadata.py",
    "rtk python3 scripts/validate_for392_m60_f16_source_facing_lane_runtime_opt_in.py",
    "rtk python3 scripts/validate_for391_m60_f16_source_facing_lane_metadata.py",
    "rtk python3 scripts/validate_for390_m60_f16_full_scene_regression_discriminator.py",
    "rtk python3 scripts/validate_for389_m60_f16_source_coverage_full_scene_candidate.py",
    "rtk python3 scripts/validate_for388_m60_f16_composition_metadata_audit.py",
    "rtk python3 scripts/validate_for387_m60_f16_residual_fringe_discriminator_audit.py",
    "rtk python3 scripts/validate_for386_m60_f16_coverage_regression_discriminator_audit.py",
    "rtk python3 scripts/validate_for385_m60_f16_generalized_coverage_metadata_predicate_audit.py",
    "rtk python3 scripts/validate_for384_m60_f16_pre_correction_geometry_coverage_metadata_audit.py",
    "rtk python3 scripts/validate_for383_m60_f16_pre_probe_predicate_audit.py",
    "rtk python3 scripts/validate_for382_m60_f16_coverage_composition_membership_audit.py",
    "rtk python3 scripts/validate_for381_m60_f16_source_color_subzone_audit.py",
    "rtk python3 scripts/validate_for380_m60_f16_source_color_correction_probe.py",
    "rtk python3 scripts/validate_for379_m60_f16_effective_source_color_path.py",
    "rtk python3 scripts/validate_for378_m60_f16_direct_source_color_evidence.py",
    "rtk python3 scripts/validate_for377_m60_f16_linear_srgb_plausibility_audit.py",
    "rtk python3 scripts/validate_for376_m60_f16_composition_quantization_candidate.py",
    "rtk python3 scripts/validate_for375_m60_f16_effective_destination_candidate.py",
    "rtk python3 scripts/validate_for374_m60_f16_candidate_regression_audit.py",
    "rtk python3 scripts/validate_for373_m60_f16_candidate_policy_rgba_probe.py",
    "rtk python3 scripts/validate_for372_m60_f16_effective_coverage_export.py",
    "rtk python3 scripts/validate_for371_m60_f16_effective_coverage_access_audit.py",
    "rtk python3 scripts/validate_for370_m60_f16_source_paint_capture_extension.py",
    "rtk python3 scripts/validate_for369_m60_f16_source_candidate_coordinate_probe.py",
    "rtk python3 scripts/validate_for368_m60_f16_candidate_metadata_capture.py",
    "rtk python3 scripts/validate_for367_m60_bounded_stroke_cap_join_comparable_f16_evidence.py",
    "rtk python3 scripts/validate_for366_f16_positive_residual_target_inventory.py",
    "rtk python3 scripts/validate_for365_f16_constrained_candidate_evaluation.py",
    "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
    "rtk git diff --check",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-394 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(path)


def read_source(path: Path) -> str:
    require(path.is_file(), f"missing source file: {rel(path)}")
    return path.read_text(encoding="utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def function_body(source: str, name: str) -> str:
    marker = f"fn {name}("
    start = source.find(marker)
    require(start >= 0, f"missing WGSL function {name}")
    brace = source.find("{", start)
    require(brace >= 0, f"missing WGSL function body for {name}")
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    fail(f"unterminated WGSL function {name}")


def validate_sources() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any]]:
    for393 = load_json(FOR393_ARTIFACT)
    require(for393.get("decision") == "M60_F16_SOURCE_FACING_LANE_SHADER_METADATA_AUDITED",
            "FOR-393 decision changed")
    require(for393.get("classification") == "requires-new-uniforms", "FOR-393 classification changed")
    audit = for393.get("shaderMetadataAudit")
    require(isinstance(audit, dict), "FOR-393 shader metadata audit missing")
    require(audit.get("safeToWireRuntimePredicate") is False, "FOR-393 runtime safety changed")
    require(audit.get("refusalReason") == "source-facing-local-band-lane-requires-band-metadata-uniforms",
            "FOR-393 refusal reason changed")

    for392 = load_json(FOR392_ARTIFACT)
    require(for392.get("decision") == "M60_F16_SOURCE_FACING_LANE_RUNTIME_OPT_IN_EVALUATED",
            "FOR-392 decision changed")
    require(for392.get("classification") == "runtime-hook-refused-missing-per-fragment-lane-metadata",
            "FOR-392 classification changed")
    guard = for392.get("explicitOptInGuard")
    require(isinstance(guard, dict), "FOR-392 guard missing")
    require(guard.get("guardId") == RUNTIME_GUARD, "FOR-392 runtime guard id changed")
    require(guard.get("enabledByDefault") is False, "FOR-392 runtime guard default changed")
    require(guard.get("oldDrawWideProbeBlockedWhenFor392GuardRequested") is True,
            "FOR-392 draw-wide probe block changed")

    for391 = load_json(FOR391_ARTIFACT)
    require(for391.get("decision") == "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED",
            "FOR-391 decision changed")
    require(for391.get("classification") == "source-facing-local-band-lane-stable-diagnostic-only",
            "FOR-391 classification changed")
    metadata = for391.get("metadata")
    require(isinstance(metadata, dict), "FOR-391 metadata missing")
    require(metadata.get("name") == METADATA_NAME, "FOR-391 metadata name changed")
    require(metadata.get("derivationFields") == ["strokeBand", "bandLocalX"],
            "FOR-391 derivation fields changed")
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixels") == 8, "FOR-391 selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "FOR-391 improved count changed")
    require(selection.get("regressionsIncluded") == 0, "FOR-391 regression count changed")

    renderer = read_source(RENDERER_SOURCE)
    shader = read_source(AA_STENCIL_COVER_SHADER)

    for needle in (
        'WEBGPU_M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_FLAG: String =',
        f'"{TRANSPORT_GUARD}"',
        'private data class M60F16AaStencilCoverBandMetadata(',
        'fun packInto(out: FloatArray, offset: Int)',
        'private fun m60F16AaStencilCoverBandMetadataTransportEnabled(): Boolean =',
        'System.getProperty(WEBGPU_M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_FLAG, "false").toBoolean()',
        'private fun m60F16AaStencilCoverBandMetadata(paint: SkPaint): M60F16AaStencilCoverBandMetadata?',
        'if (!m60F16AaStencilCoverBandMetadataTransportEnabled()) return null',
        'if (width != 192 || height != 128) return null',
        'if (paint.shader != null || paint.colorFilter != null || paint.maskFilter != null || paint.pathEffect != null)',
        'style.cap == "butt" && style.join == "bevel" && sourceColor == 0xFF0066CC.toInt()',
        'style.cap == "round" && style.join == "round" && sourceColor == 0xFF008A4C.toInt()',
        'style.cap == "square" && style.join == "bevel" && sourceColor == 0xFFB33C00.toInt()',
        'val m60F16BandMetadata: M60F16AaStencilCoverBandMetadata? = null',
        'm60F16BandMetadata = m60F16AaStencilCoverBandMetadata(paint)',
        'val packed = FloatArray(12 + MAX_AA_EDGES * 4 + 8 + 24 + 8)',
        'd.m60F16BandMetadata?.packInto(packed, colorFilterBase + 24)',
        'const val AA_POLYGON_UNIFORM_SIZE: ULong = 4304uL',
        'if (m60F16SourceFacingLaneRuntimeCandidateRequested()) return false',
    ):
        require(needle in renderer, f"renderer source missing proof: {needle}")

    for needle in (
        'm60F16BandMetadata0: vec4f',
        'm60F16BandMetadata1: vec4f',
        'fn m60_f16_band_metadata_enabled() -> bool',
        'fn m60_f16_local_x(pixel: vec2f) -> f32',
        'fn m60_f16_candidate_lane(pixel: vec2f) -> bool',
        'floor(pixel.x) - uniforms.m60F16BandMetadata0.y',
        'band_id == 1u && local_x <= left_max',
        'band_id == 2u && local_x >= right_min',
    ):
        require(needle in shader, f"shader source missing proof: {needle}")

    for entry_point in ("fs_inside", "fs_outside"):
        body = function_body(shader, entry_point)
        require("m60_f16_" not in body, f"{entry_point} must not consume FOR-394 metadata")
        require("m60F16BandMetadata" not in body, f"{entry_point} must not read FOR-394 uniforms")

    require(OLD_PROBE_GUARD in renderer, "FOR-380 guard no longer present")
    require(RUNTIME_GUARD in renderer, "FOR-392 guard no longer present")
    require(TRANSPORT_GUARD in renderer, "FOR-394 transport guard missing")

    layout_comment = re.search(r"m60F16BandMetadata0.*offset 4272.*m60F16BandMetadata1.*offset 4288", shader, re.S)
    require(layout_comment is not None, "shader layout offsets for metadata slots missing")

    return for393, for392, for391, {
        "renderer": renderer,
        "shader": shader,
    }


def build_artifact() -> dict[str, Any]:
    for393, for392, for391, _sources = validate_sources()
    selection = for391["selection"]
    ideal = selection["estimatedFullSceneResidualIfAppliedToThisSubset"]
    guard_enabled = for392["guardEnabledEvaluation"]
    artifact = {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-aa-stencil-cover-band-metadata-transport-for394",
        "sourceArtifact": rel(FOR393_ARTIFACT),
        "secondarySourceArtifact": rel(FOR392_ARTIFACT),
        "tertiarySourceArtifact": rel(FOR391_ARTIFACT),
        "adapter": for391["adapter"],
        "producer": "scripts/validate_for394_m60_f16_aa_stencil_cover_band_metadata_transport.py",
        "producerInput": "FOR-393 required-new-uniforms finding plus FOR-392/FOR-391 guard and predicate evidence",
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "requiredFor393Decision": for393["decision"],
        "requiredFor393Classification": for393["classification"],
        "requiredFor392Decision": for392["decision"],
        "requiredFor392Classification": for392["classification"],
        "requiredFor391Decision": for391["decision"],
        "requiredFor391Classification": for391["classification"],
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "status": CLASSIFICATION,
        "allowedClassifications": [
            "diagnostic-transport-added-not-connected",
            "diagnostic-transport-refused-layout-blocker",
            "diagnostic-transport-added-but-not-lane-derivable",
        ],
        "candidateOnly": True,
        "supportClaim": False,
        "promoted": False,
        "limitedTo": "M60 F16 bounded stroke cap/join solid AA stencil-cover diagnostic path",
        "correctionAppliedByDefault": False,
        "correctionPredicateEnabledByDefault": False,
        "runtimeHookInstalled": False,
        "defaultBehaviorChanged": False,
        "renderedPixelsChanged": False,
        "wgslChanged": True,
        "fallbackChanged": False,
        "scoringChanged": False,
        "thresholdChanged": False,
        "generalizedOutsideM60F16": False,
        "oldDrawWideProbeUsed": False,
        "oldDrawWideProbeBlockedWhenFor392GuardRequested": True,
        "explicitDiagnosticTransportGuard": {
            "guardId": TRANSPORT_GUARD,
            "enabledByDefault": False,
            "recognizedByRenderer": True,
            "transportWritesWhenDisabled": False,
            "transportWritesOnlyWhenM60F16FixtureRecognized": True,
            "conditions": [
                "System property enabled",
                "RGBA16Float intermediate",
                "targetColorSpaceBlend active",
                "experimental stroke cap/join renderer active",
                "surface size 192x128",
                "solid SrcOver paint without shader/colorFilter/maskFilter/pathEffect",
                "strokeWidth == 10",
                "expected M60 cap/join/source-color tuple",
            ],
        },
        "for392RuntimeGuardPreserved": {
            "guardId": RUNTIME_GUARD,
            "enabledByDefault": False,
            "runtimeHookInstalled": guard_enabled["runtimeHookInstalled"],
            "runtimeCorrectionApplied": guard_enabled["runtimeCorrectionApplied"],
            "residualWithGuardEnabled": guard_enabled["residualWithGuardEnabled"],
            "residualDeltaWithGuardEnabled": guard_enabled["residualDeltaWithGuardEnabled"],
            "oldDrawWideProbeGuard": OLD_PROBE_GUARD,
            "oldDrawWideProbeBlockedWhenFor392GuardRequested": True,
        },
        "uniformLayout": {
            "owner": "StencilCoverAaPolygonDraw",
            "packer": "buildStencilCoverAaDrawResources",
            "shader": "aa_stencil_cover.wgsl",
            "bindGroupLayout": "aaPolygonBindGroupLayout",
            "uniformSizeBeforeBytes": 4272,
            "uniformSizeAfterBytes": 4304,
            "compatibility": "append-only trailing uniform extension; default zeros do not feed fs_inside/fs_outside output",
            "transportSlots": [
                {
                    "name": "m60F16BandMetadata0",
                    "offsetBytes": 4272,
                    "type": "vec4f",
                    "fields": ["enabled", "bandXStart", "bandXEnd", "strokeBandId"],
                },
                {
                    "name": "m60F16BandMetadata1",
                    "offsetBytes": 4288,
                    "type": "vec4f",
                    "fields": ["capId", "joinId", "sourceFacingLeftMaxLocalX", "sourceFacingRightMinLocalX"],
                },
            ],
        },
        "transportedFields": {
            "status": "shader-observable-diagnostic-only",
            "diagnosticOnly": True,
            "defaultEnabled": False,
            "fields": [
                "enabled",
                "bandXStart",
                "bandXEnd",
                "strokeBandId",
                "capId",
                "joinId",
                "sourceFacingLeftMaxLocalX",
                "sourceFacingRightMinLocalX",
            ],
            "strokeBandIdMapping": {
                "0": "disabled-or-unknown",
                "1": "butt-bevel",
                "2": "round-round",
                "3": "square-bevel",
            },
            "capIdMapping": {
                "1": "butt",
                "2": "round",
                "3": "square",
            },
            "joinIdMapping": {
                "1": "bevel",
                "2": "round",
            },
            "m60Bands": [
                {"strokeBand": "butt-bevel", "xStart": 0, "xEnd": 48, "strokeBandId": 1, "capId": 1, "joinId": 1},
                {"strokeBand": "round-round", "xStart": 48, "xEnd": 96, "strokeBandId": 2, "capId": 2, "joinId": 2},
                {"strokeBand": "square-bevel", "xStart": 96, "xEnd": 192, "strokeBandId": 3, "capId": 3, "joinId": 1},
            ],
        },
        "shaderObservability": {
            "metadataName": METADATA_NAME,
            "bandLocalXDerivableInShaderWhenTransportEnabled": True,
            "bandLocalXFormula": "floor(frag.xy.x) - m60F16BandMetadata0.bandXStart",
            "sourceFacingLocalBandLaneObservableInShader": True,
            "sourceFacingLocalBandLaneFormula": (
                "(strokeBandId == 1 && bandLocalX <= sourceFacingLeftMaxLocalX) || "
                "(strokeBandId == 2 && bandLocalX >= sourceFacingRightMinLocalX)"
            ),
            "connectedToFinalColor": False,
            "connectedToCoverage": False,
            "connectedToFallback": False,
            "shaderEntryPointsConsumingMetadata": [],
            "helperFunctions": [
                "m60_f16_band_metadata_enabled",
                "m60_f16_local_x",
                "m60_f16_candidate_lane",
            ],
        },
        "candidatePredicate": {
            "id": "source-facing-local-band-lane",
            "metadataName": METADATA_NAME,
            "selectionMethod": "sourceFacingLocalBandLane == true",
            "derivationFormula": "(strokeBand == round-round && bandLocalX >= 39) || (strokeBand == butt-bevel && bandLocalX <= 17)",
            "selectedPixels": selection["selectedPixels"],
            "improvedPixelsRecovered": selection["improvedPixelsRecovered"],
            "regressionsIncluded": selection["regressionsIncluded"],
            "idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable": {
                "baseUncorrectedFullSceneResidual": ideal["baseUncorrectedFullSceneResidual"],
                "simulatedAfterResidual": ideal["simulatedAfterResidual"],
                "deltaVsCurrent": ideal["deltaVsCurrent"],
                "gainVsCurrent": ideal["gainVsCurrent"],
            },
        },
        "nonGoalsPreserved": {
            "m60F16CorrectionEnabled": False,
            "runtimePredicateActivated": False,
            "sourceFacingLocalBandLaneConnectedToFinalColor": False,
            "for380ProbeRouteUsed": False,
            "scoringChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "generalFallbackChanged": False,
            "unrelatedScenesChanged": False,
            "generalizedOutsideM60F16": False,
        },
        "risk": {
            "remainingRisk": "Transport is observable but not yet proven by a shader readback route; future activation still needs a lane-specific runtime validation.",
            "nextMinimalStep": "add diagnostic readback or a non-color side-channel proving the shader helper observes the expected lane before any correction hook",
        },
        "validationCommands": VALIDATION_COMMANDS,
    }
    return artifact


def validate_artifact(artifact: dict[str, Any]) -> None:
    require(artifact.get("decision") == DECISION, "decision changed")
    require(artifact.get("classification") == CLASSIFICATION, "classification changed")
    require(artifact.get("status") in artifact.get("allowedClassifications", []), "classification not allowed")
    require(artifact.get("correctionAppliedByDefault") is False, "correction default changed")
    require(artifact.get("runtimeHookInstalled") is False, "runtime hook changed")
    require(artifact.get("defaultBehaviorChanged") is False, "default behavior changed")
    require(artifact.get("renderedPixelsChanged") is False, "rendered pixels changed")
    require(artifact.get("fallbackChanged") is False, "fallback changed")
    require(artifact.get("scoringChanged") is False, "scoring changed")
    require(artifact.get("thresholdChanged") is False, "threshold changed")
    require(artifact.get("oldDrawWideProbeBlockedWhenFor392GuardRequested") is True,
            "FOR-392 old probe block not preserved")

    transport_guard = artifact.get("explicitDiagnosticTransportGuard")
    require(isinstance(transport_guard, dict), "transport guard missing")
    require(transport_guard.get("guardId") == TRANSPORT_GUARD, "transport guard id changed")
    require(transport_guard.get("enabledByDefault") is False, "transport guard default changed")
    require(transport_guard.get("transportWritesOnlyWhenM60F16FixtureRecognized") is True,
            "transport scope widened")

    runtime_guard = artifact.get("for392RuntimeGuardPreserved")
    require(isinstance(runtime_guard, dict), "FOR-392 runtime guard proof missing")
    require(runtime_guard.get("guardId") == RUNTIME_GUARD, "FOR-392 runtime guard changed")
    require(runtime_guard.get("runtimeCorrectionApplied") is False, "FOR-392 correction changed")
    require(runtime_guard.get("residualWithGuardEnabled") == 2014, "FOR-392 residual changed")
    require(runtime_guard.get("residualDeltaWithGuardEnabled") == 0, "FOR-392 residual delta changed")

    layout = artifact.get("uniformLayout")
    require(isinstance(layout, dict), "uniform layout missing")
    require(layout.get("uniformSizeBeforeBytes") == 4272, "old uniform size changed")
    require(layout.get("uniformSizeAfterBytes") == 4304, "new uniform size changed")
    slots = layout.get("transportSlots")
    require(isinstance(slots, list) and len(slots) == 2, "transport slots missing")
    require(slots[0].get("offsetBytes") == 4272, "metadata0 offset changed")
    require(slots[1].get("offsetBytes") == 4288, "metadata1 offset changed")

    fields = artifact.get("transportedFields")
    require(isinstance(fields, dict), "transported fields missing")
    for field in ("bandXStart", "bandXEnd", "strokeBandId", "capId", "joinId"):
        require(field in fields.get("fields", []), f"transport field missing: {field}")

    shader = artifact.get("shaderObservability")
    require(isinstance(shader, dict), "shader observability missing")
    require(shader.get("bandLocalXDerivableInShaderWhenTransportEnabled") is True,
            "bandLocalX derivability missing")
    require(shader.get("sourceFacingLocalBandLaneObservableInShader") is True,
            "sourceFacingLocalBandLane observability missing")
    require(shader.get("connectedToFinalColor") is False, "metadata connected to final color")
    require(shader.get("shaderEntryPointsConsumingMetadata") == [], "entry point consumes metadata")

    candidate = artifact.get("candidatePredicate")
    require(isinstance(candidate, dict), "candidate predicate missing")
    require(candidate.get("selectedPixels") == 8, "selected count changed")
    require(candidate.get("improvedPixelsRecovered") == 8, "improved count changed")
    require(candidate.get("regressionsIncluded") == 0, "regression count changed")
    ideal = candidate.get("idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable")
    require(isinstance(ideal, dict), "ideal impact missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "base residual changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "ideal residual changed")

    non_goals = artifact.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def render_report(artifact: dict[str, Any]) -> str:
    layout = artifact["uniformLayout"]
    guard = artifact["explicitDiagnosticTransportGuard"]
    shader = artifact["shaderObservability"]
    candidate = artifact["candidatePredicate"]
    ideal = candidate["idealSimulatedFullSceneImpactIfLaneRuntimeWereAvailable"]
    slots = "\n".join(
        f"- `{slot['name']}` offset `{slot['offsetBytes']}` : {', '.join(slot['fields'])}"
        for slot in layout["transportSlots"]
    )
    fields = "\n".join(f"- `{field}`" for field in artifact["transportedFields"]["fields"])
    conditions = "\n".join(f"- {condition}" for condition in guard["conditions"])
    return f"""# FOR-394 M60 F16 AA stencil-cover band metadata transport

Decision: `{artifact["decision"]}`

Classification: `{artifact["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-394 ajoute un transport diagnostique borne de metadata de bande dans le
chemin `StencilCoverAaPolygonDraw` / `buildStencilCoverAaDrawResources` /
`aa_stencil_cover.wgsl`. Le transport reste eteint par defaut et ne modifie pas
la couleur finale.

## Resultat

Statut JSON: `{artifact["status"]}`.

Le chemin AA stencil-cover solid-color expose maintenant deux slots uniformes
diagnostiques a la fin du layout. Le shader peut deriver `bandLocalX` par
`{shader["bandLocalXFormula"]}` et peut evaluer le helper
`sourceFacingLocalBandLane`, mais `fs_inside` et `fs_outside` ne lisent pas ces
helpers.

## Garde diagnostique

- garde: `{guard["guardId"]}` ;
- active par defaut: `false` ;
- ecrit des metadata quand eteint: `false` ;
- limite au M60 F16 reconnu: `true`.

Conditions de transport :

{conditions}

## Layout transporte

- taille avant: `{layout["uniformSizeBeforeBytes"]}` bytes ;
- taille apres: `{layout["uniformSizeAfterBytes"]}` bytes ;
- compatibilite: {layout["compatibility"]}.

{slots}

Champs transportes :

{fields}

## Observabilite shader

- `bandLocalX` derivable cote shader: `{shader["bandLocalXDerivableInShaderWhenTransportEnabled"]}` ;
- `sourceFacingLocalBandLane` observable cote shader: `{shader["sourceFacingLocalBandLaneObservableInShader"]}` ;
- raccord couleur finale: `{shader["connectedToFinalColor"]}` ;
- raccord couverture: `{shader["connectedToCoverage"]}` ;
- entry points consommant la metadata: `{len(shader["shaderEntryPointsConsumingMetadata"])}`.

## Garde FOR-392 preserve

- garde runtime: `{artifact["for392RuntimeGuardPreserved"]["guardId"]}` ;
- correction runtime appliquee: `false` ;
- probe FOR-380 draw-wide bloque: `true` ;
- residuel avec garde demande: `{artifact["for392RuntimeGuardPreserved"]["residualWithGuardEnabled"]}` ;
- delta: `{artifact["for392RuntimeGuardPreserved"]["residualDeltaWithGuardEnabled"]}`.

## Compteurs conserves

- pixels selectionnes par le predicate ideal: `{candidate["selectedPixels"]}` ;
- ameliores recuperes: `{candidate["improvedPixelsRecovered"]}/8` ;
- regressions incluses: `{candidate["regressionsIncluded"]}/8` ;
- residuel ideal si metadata runtime activee plus tard: `{ideal["baseUncorrectedFullSceneResidual"]} -> {ideal["simulatedAfterResidual"]}`.

## Non-objectifs

Aucune correction M60 F16, activation de predicate runtime, connexion a la
couleur finale, modification de fallback, score, seuil, promotion ou scene non
liee n'est effectuee.

## Risque restant

{artifact["risk"]["remainingRisk"]}

Prochaine extension minimale: {artifact["risk"]["nextMinimalStep"]}.
"""


def write_outputs(artifact: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    REPORT.write_text(render_report(artifact), encoding="utf-8")


def main() -> None:
    artifact = build_artifact()
    validate_artifact(artifact)
    write_outputs(artifact)
    round_trip = load_json(ARTIFACT)
    validate_artifact(round_trip)
    report = read_source(REPORT)
    for needle in (
        DECISION,
        CLASSIFICATION,
        TRANSPORT_GUARD,
        "bandLocalX",
        "sourceFacingLocalBandLane",
        "fs_inside",
        "fs_outside",
        "2014 -> 1949",
    ):
        require(needle in report, f"report missing {needle}")
    print(
        "FOR-394 validation passed: "
        f"{CLASSIFICATION}; bandLocalX shader-derivable diagnostic transport recorded."
    )


if __name__ == "__main__":
    main()
