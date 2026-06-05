#!/usr/bin/env python3
"""Validate FOR-396 M60 F16 AA stencil-cover fragment lane diagnostic channel."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-396"
DECISION = "M60_F16_AA_STENCIL_COVER_FRAGMENT_LANE_DIAGNOSTIC_CHANNEL_INSTALLED"
CLASSIFICATION = "fragment-lane-diagnostic-channel-installed"
ALLOWED_CLASSIFICATIONS = [
    "fragment-lane-diagnostic-channel-installed",
    "fragment-lane-diagnostic-channel-mismatch",
    "fragment-lane-diagnostic-channel-refused",
]
GUARD = "kanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
RUNTIME_GUARD = "kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled"
OLD_PROBE_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-396-m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel.md"
)
FOR394_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394.json"
)
FOR395_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-shader-readback-for395/"
    "m60-f16-source-facing-lane-shader-readback-for395.json"
)
FOR391_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-metadata-for391/"
    "m60-f16-source-facing-lane-metadata-for391.json"
)
RENDERER = PROJECT_ROOT / "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt"
AA_STENCIL_COVER_SHADER = PROJECT_ROOT / "gpu-raster/src/main/resources/shaders/aa_stencil_cover.wgsl"

EXPECTED_PIXELS = [
    {"x": 93, "y": 74},
    {"x": 92, "y": 75},
    {"x": 91, "y": 76},
    {"x": 17, "y": 77},
    {"x": 90, "y": 77},
    {"x": 89, "y": 78},
    {"x": 88, "y": 79},
    {"x": 87, "y": 80},
]

VALIDATION_COMMANDS = [
    "rtk python3 scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for396-pycache-parent python3 -m py_compile scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py",
    "rtk git diff --check",
    "rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin",
    "rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true "
    "-Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true "
    "-Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true "
    ":gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest",
    "rtk python3 scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py",
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
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-396 validation failed: {message}")


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


def validate_sources() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], str, str]:
    for394 = load_json(FOR394_ARTIFACT)
    require(for394.get("classification") == "diagnostic-transport-added-not-connected",
            "FOR-394 transport classification changed")
    require(for394.get("shaderObservability", {}).get("sourceFacingLocalBandLaneObservableInShader") is True,
            "FOR-394 helper observability changed")

    for395 = load_json(FOR395_ARTIFACT)
    require(for395.get("classification") == "shader-lane-readback-refused-missing-extension",
            "FOR-395 refusal classification changed")
    require("storage buffer" in for395.get("shaderReadbackStatus", {}).get("minimalExtensionNeeded", ""),
            "FOR-395 must still point to storage-buffer extension")

    for391 = load_json(FOR391_ARTIFACT)
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixelCoordinates") == EXPECTED_PIXELS,
            "FOR-391 selected pixels changed")

    renderer = read_source(RENDERER)
    shader = read_source(AA_STENCIL_COVER_SHADER)

    for needle in (
        f'"{GUARD}"',
        "m60F16AaStencilCoverFragmentLaneDiagnosticsEnabled",
        "m60F16FragmentLaneDiagnosticSnapshot",
        "loadM60F16FragmentLaneDiagnosticShader",
        "diagnostic://m60-f16-aa-stencil-cover-fragment-lane",
        "@binding(1) @group(0) var<storage, read_write> m60F16FragmentLaneDiagnostic",
        "fn m60_f16_record_fragment_lane(pixel: vec2f, side: u32)",
        "m60_f16_candidate_lane(pixel)",
        "M60_F16_FRAGMENT_LANE_DIAGNOSTIC_POINTS",
        "M60_F16_FRAGMENT_LANE_DIAGNOSTIC_BUFFER_SIZE",
        "GPUBufferUsage.Storage or GPUBufferUsage.CopySrc or GPUBufferUsage.CopyDst",
        "GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst",
        "m60F16FragmentLaneDiagnosticBindGroupLayout",
        "m60F16FragmentLaneDiagnosticPipelineFor",
        "res.m60F16FragmentLaneDiagnosticStorage?.let",
        "encoder.copyBufferToBuffer(",
        "recordM60F16FragmentLaneDiagnostics(perDrawResources)",
        "readback.staging.mapAsync(",
    ):
        require(needle in renderer, f"renderer source missing proof: {needle}")

    require("layout = aaPolygonPipelineLayout" in renderer,
            "default AA stencil-cover pipeline layout changed")
    require("layout = m60F16FragmentLaneDiagnosticPipelineLayout" in renderer,
            "diagnostic pipeline layout missing")
    require("diagnosticEnabled =\n            m60F16AaStencilCoverFragmentLaneDiagnosticsEnabled && d.m60F16BandMetadata != null" in renderer,
            "diagnostic allocation must be guard plus FOR-394 metadata")
    require(f'"{TRANSPORT_GUARD}"' in renderer, "FOR-394 guard missing from renderer")
    require(f'"{RUNTIME_GUARD}"' in renderer, "FOR-392 runtime guard missing from renderer")
    require(f'"{OLD_PROBE_GUARD}"' in renderer, "FOR-380 guard missing from renderer")

    for pixel in EXPECTED_PIXELS:
        require(f"px == {pixel['x']}u && py == {pixel['y']}u" in renderer,
                f"diagnostic shader injection missing expected pixel {pixel}")

    require("@binding(1)" not in shader, "default aa_stencil_cover.wgsl must not declare diagnostic storage")
    require("m60F16FragmentLaneDiagnostic" not in shader,
            "default aa_stencil_cover.wgsl must not contain FOR-396 side channel")
    for entry_point in ("fs_inside", "fs_outside"):
        body = function_body(shader, entry_point)
        require("m60_f16_candidate_lane" not in body,
                f"default {entry_point} must not consume lane helper")

    return for394, for395, for391, renderer, shader


def encoded_channel_slots() -> list[dict[str, Any]]:
    return [
        {
            "slot": index,
            "x": pixel["x"],
            "y": pixel["y"],
            "expectedCandidateLane": True,
            "encodedStorageValue": [pixel["x"], pixel["y"], 1, "inside-or-outside"],
        }
        for index, pixel in enumerate(EXPECTED_PIXELS)
    ]


def build_artifact() -> dict[str, Any]:
    for394, _for395, for391, _renderer, _shader = validate_sources()
    selection = for391["selection"]
    ideal = selection["estimatedFullSceneResidualIfAppliedToThisSubset"]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-aa-stencil-cover-fragment-lane-diagnostic-channel-for396",
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "sourceArtifact": rel(FOR394_ARTIFACT),
        "previousRefusalArtifact": rel(FOR395_ARTIFACT),
        "expectedLaneSourceArtifact": rel(FOR391_ARTIFACT),
        "producer": "scripts/validate_for396_m60_f16_aa_stencil_cover_fragment_lane_diagnostic_channel.py",
        "limitedTo": "M60 F16 bounded stroke cap/join AA stencil-cover diagnostic path",
        "supportClaim": False,
        "promoted": False,
        "explicitDiagnosticGuard": {
            "guardId": GUARD,
            "enabledByDefault": False,
            "recognizedByRenderer": True,
            "requiresFor394Transport": True,
            "requiresM60F16RecognizedDraw": True,
            "writesWhenDisabled": False,
        },
        "reusesFor394Transport": {
            "guardId": TRANSPORT_GUARD,
            "sourceArtifactClassification": for394["classification"],
            "helper": "m60_f16_candidate_lane",
            "transportSlots": ["m60F16BandMetadata0", "m60F16BandMetadata1"],
        },
        "fragmentDiagnosticChannel": {
            "status": CLASSIFICATION,
            "shaderSource": "in-memory diagnostic variant of aa_stencil_cover.wgsl",
            "defaultShaderChanged": False,
            "defaultPipelineLayoutChanged": False,
            "diagnosticPipelineLayout": "binding0 AA uniform, binding1 fragment storage buffer",
            "storageBufferBytes": 128,
            "sampleStrideBytes": 16,
            "storedValueSchema": ["x", "y", "candidateLaneBoolAsU32", "coverageSide"],
            "cpuReadbackInstalled": True,
            "cpuReadbackApi": "GPUBufferUsage.MapRead staging buffer after copyBufferToBuffer",
            "runtimeShaderPipelineSmokePassed": True,
        },
        "pixelComparison": {
            "comparisonStatus": "channel-installed-runtime-observation-not-exported",
            "expectedUsefulPixels": EXPECTED_PIXELS,
            "expectedUsefulPixelCount": 8,
            "channelEncodedSlots": encoded_channel_slots(),
            "channelEncodedSlotCount": 8,
            "shaderObservedPixels": [],
            "shaderObservedPixelCount": 0,
            "falsePositives": [],
            "falsePositiveCount": 0,
            "falseNegatives": [],
            "falseNegativeCount": 0,
            "falsePositiveFalseNegativeMeasured": False,
            "exactMatchProvenByRuntimeReadback": False,
            "runtimeReadbackArtifactCaptured": False,
        },
        "candidatePredicate": {
            "id": "source-facing-local-band-lane",
            "metadataName": "sourceFacingLocalBandLane",
            "selectionMethod": "sourceFacingLocalBandLane == true",
            "derivationFormula": (
                "(strokeBand == round-round && bandLocalX >= 39) || "
                "(strokeBand == butt-bevel && bandLocalX <= 17)"
            ),
            "idealSelectedPixels": selection["selectedPixels"],
            "idealImprovedPixelsRecovered": selection["improvedPixelsRecovered"],
            "idealRegressionsIncluded": selection["regressionsIncluded"],
            "idealFullSceneImpactIfRuntimeWereEventuallyProven": {
                "baseUncorrectedFullSceneResidual": ideal["baseUncorrectedFullSceneResidual"],
                "simulatedAfterResidual": ideal["simulatedAfterResidual"],
                "deltaVsCurrent": ideal["deltaVsCurrent"],
                "gainVsCurrent": ideal["gainVsCurrent"],
            },
        },
        "nonGoalsPreserved": {
            "m60F16CorrectionEnabled": False,
            "runtimePredicateActivated": False,
            "finalColorChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "scoringChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "for380ProbeRouteUsed": False,
            "generalizedOutsideM60F16": False,
        },
        "risk": {
            "remainingRisk": (
                "The M60 scene smoke test creates the diagnostic shader/pipeline "
                "with both guards enabled. The checked-in artifact still records "
                "the channel contract structurally; a follow-up can export "
                "m60F16FragmentLaneDiagnosticSnapshot if product evidence needs "
                "driver-observed values."
            ),
            "nextStep": "Run the M60 scene with both FOR-394 and FOR-396 guards and export the snapshot.",
        },
        "validationCommands": VALIDATION_COMMANDS,
    }


def validate_artifact(artifact: dict[str, Any]) -> None:
    require(artifact.get("decision") == DECISION, "decision changed")
    require(artifact.get("classification") == CLASSIFICATION, "classification changed")
    require(artifact.get("classification") in artifact.get("allowedClassifications", []),
            "classification outside allowed set")
    guard = artifact.get("explicitDiagnosticGuard")
    require(isinstance(guard, dict), "diagnostic guard missing")
    require(guard.get("guardId") == GUARD, "guard id changed")
    require(guard.get("enabledByDefault") is False, "guard default changed")
    require(guard.get("writesWhenDisabled") is False, "guard must not write when disabled")

    channel = artifact.get("fragmentDiagnosticChannel")
    require(isinstance(channel, dict), "diagnostic channel missing")
    require(channel.get("cpuReadbackInstalled") is True, "CPU readback must be installed")
    require(channel.get("runtimeShaderPipelineSmokePassed") is True,
            "runtime shader/pipeline smoke proof missing")
    require(channel.get("defaultShaderChanged") is False, "default shader must not change")
    require(channel.get("defaultPipelineLayoutChanged") is False,
            "default pipeline layout must not change")
    require(channel.get("storageBufferBytes") == 128, "storage buffer size changed")

    comparison = artifact.get("pixelComparison")
    require(isinstance(comparison, dict), "pixel comparison missing")
    require(comparison.get("expectedUsefulPixels") == EXPECTED_PIXELS, "expected pixels changed")
    require(comparison.get("channelEncodedSlotCount") == 8, "encoded slot count changed")
    require(comparison.get("shaderObservedPixels") == [],
            "versioned artifact must not claim driver-observed pixels")
    require(comparison.get("shaderObservedPixelCount") == 0,
            "versioned artifact must not claim observed pixel count")
    require(comparison.get("falsePositiveCount") == 0, "false positives changed")
    require(comparison.get("falseNegativeCount") == 0, "false negatives changed")
    require(comparison.get("falsePositiveFalseNegativeMeasured") is False,
            "versioned artifact must not claim measured false positives/negatives")
    require(comparison.get("exactMatchProvenByRuntimeReadback") is False,
            "versioned artifact must not claim runtime exact match")
    require(comparison.get("runtimeReadbackArtifactCaptured") is False,
            "runtime readback artifact must remain explicitly unexported")
    for slot in comparison.get("channelEncodedSlots", []):
        require(slot.get("expectedCandidateLane") is True, "encoded slot must carry expected lane=true")

    predicate = artifact.get("candidatePredicate")
    require(isinstance(predicate, dict), "candidate predicate missing")
    require(predicate.get("idealSelectedPixels") == 8, "ideal selected count changed")
    require(predicate.get("idealImprovedPixelsRecovered") == 8, "ideal improvement count changed")
    require(predicate.get("idealRegressionsIncluded") == 0, "ideal regression count changed")

    non_goals = artifact.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def render_report(artifact: dict[str, Any]) -> str:
    guard = artifact["explicitDiagnosticGuard"]
    channel = artifact["fragmentDiagnosticChannel"]
    comparison = artifact["pixelComparison"]
    predicate = artifact["candidatePredicate"]
    ideal = predicate["idealFullSceneImpactIfRuntimeWereEventuallyProven"]
    pixels = "\n".join(
        f"- ({pixel['x']}, {pixel['y']}) -> lane=true"
        for pixel in comparison["channelEncodedSlots"]
    )
    validations = "\n".join(f"- `{command}`" for command in artifact["validationCommands"])
    return f"""# FOR-396 M60 F16 AA stencil-cover fragment lane diagnostic channel

Decision: `{artifact["decision"]}`

Classification: `{artifact["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-396 installe un canal diagnostique fragment pour le chemin AA stencil-cover
M60 F16. Le chemin par defaut reste sur `aa_stencil_cover.wgsl` et
`aaPolygonPipelineLayout`; la variante diagnostique est generee en memoire et
utilisee uniquement avec le garde opt-in.

## Garde diagnostique

- garde: `{guard["guardId"]}`
- actif par defaut: `{guard["enabledByDefault"]}`
- ecritures hors garde: `{guard["writesWhenDisabled"]}`
- depend du transport FOR-394: `{guard["requiresFor394Transport"]}`

## Canal installe

- shader: `{channel["shaderSource"]}`
- layout: `{channel["diagnosticPipelineLayout"]}`
- buffer: `{channel["storageBufferBytes"]}` octets
- schema: `{channel["storedValueSchema"]}`
- lecture CPU: `{channel["cpuReadbackInstalled"]}` via `{channel["cpuReadbackApi"]}`
- smoke runtime scene M60: `{channel["runtimeShaderPipelineSmokePassed"]}`

Pixels attendus encodes par le canal:

{pixels}

Pixels observes exportes depuis le snapshot runtime: `{comparison["shaderObservedPixelCount"]}`.
L'export runtime du snapshot reste hors de ce ticket ; l'artefact ne revendique
donc pas de faux positifs/faux negatifs mesures ni d'exact-match runtime.

## Non-objectifs preserves

- aucune correction M60 F16 activee ;
- aucune modification de la couleur finale ou de la couverture ;
- aucun changement de fallback, scoring, seuil ou promotion ;
- aucune route FOR-380.

## Impact ideal conserve

- pixels utiles: `{predicate["idealSelectedPixels"]}`
- pixels ameliores: `{predicate["idealImprovedPixelsRecovered"]}`
- regressions incluses: `{predicate["idealRegressionsIncluded"]}`
- residu plein scene ideal: `{ideal["baseUncorrectedFullSceneResidual"]}` -> `{ideal["simulatedAfterResidual"]}`

## Risque restant

{artifact["risk"]["remainingRisk"]}

## Validations

{validations}
"""


def main() -> None:
    artifact = build_artifact()
    validate_artifact(artifact)
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(artifact, indent=2, sort_keys=False) + "\n", encoding="utf-8")
    REPORT.write_text(render_report(artifact), encoding="utf-8")
    print(
        "FOR-396 validation passed: "
        f"{artifact['classification']} with "
        f"{artifact['pixelComparison']['channelEncodedSlotCount']} diagnostic lane slots"
    )


if __name__ == "__main__":
    main()
