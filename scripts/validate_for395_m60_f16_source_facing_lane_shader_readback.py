#!/usr/bin/env python3
"""Validate FOR-395 M60 F16 source-facing lane shader readback evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-395"
DECISION = "M60_F16_SOURCE_FACING_LANE_SHADER_READBACK_REFUSED"
CLASSIFICATION = "shader-lane-readback-refused-missing-extension"
ALLOWED_CLASSIFICATIONS = [
    "shader-lane-readback-confirmed",
    "shader-lane-readback-mismatch",
    "shader-lane-readback-refused-missing-extension",
]
READBACK_GUARD = "kanvas.webgpu.m60F16SourceFacingLaneShaderReadback.enabled"
TRANSPORT_GUARD = "kanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled"
RUNTIME_GUARD = "kanvas.webgpu.m60F16SourceFacingLaneRuntimeCandidate.enabled"
OLD_PROBE_GUARD = "kanvas.webgpu.m60F16SourceColorCorrectionProbe.enabled"
SOURCE_MEMORY = (
    "global/kanvas/ticket-drafts/"
    "draft-prochain-ticket-m60-f16-prouver-readback-shader-source-facing-local-band-lane-apres-for-394"
)
SOURCE_FINDING = (
    "global/kanvas/findings/"
    "for-394-ajoute-un-transport-diagnostique-aa-stencil-cover-de-metadata-de-bande-m60-f16-sans-activation-runtime"
)

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-source-facing-lane-shader-readback-for395"
)
ARTIFACT = ARTIFACT_DIR / "m60-f16-source-facing-lane-shader-readback-for395.json"
REPORT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/"
    "2026-06-05-for-395-m60-f16-source-facing-lane-shader-readback.md"
)
FOR394_ARTIFACT = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394/"
    "m60-f16-aa-stencil-cover-band-metadata-transport-for394.json"
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
    "rtk python3 scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py",
    "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for395-pycache-parent python3 -m py_compile scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py",
    "rtk git diff --check",
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
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-395 validation failed: {message}")


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


def validate_sources() -> tuple[dict[str, Any], dict[str, Any], str, str]:
    for394 = load_json(FOR394_ARTIFACT)
    require(for394.get("decision") == "M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_RECORDED",
            "FOR-394 decision changed")
    require(for394.get("classification") == "diagnostic-transport-added-not-connected",
            "FOR-394 classification changed")
    guard = for394.get("explicitDiagnosticTransportGuard")
    require(isinstance(guard, dict), "FOR-394 transport guard missing")
    require(guard.get("guardId") == TRANSPORT_GUARD, "FOR-394 transport guard changed")
    require(guard.get("enabledByDefault") is False, "FOR-394 transport guard default changed")
    shader_observability = for394.get("shaderObservability")
    require(isinstance(shader_observability, dict), "FOR-394 shader observability missing")
    require(shader_observability.get("sourceFacingLocalBandLaneObservableInShader") is True,
            "FOR-394 helper observability changed")
    require(shader_observability.get("connectedToFinalColor") is False,
            "FOR-394 helper must remain disconnected from color")

    for391 = load_json(FOR391_ARTIFACT)
    require(for391.get("decision") == "M60_F16_SOURCE_FACING_LOCAL_BAND_LANE_METADATA_RECORDED",
            "FOR-391 decision changed")
    require(for391.get("classification") == "source-facing-local-band-lane-stable-diagnostic-only",
            "FOR-391 classification changed")
    selection = for391.get("selection")
    require(isinstance(selection, dict), "FOR-391 selection missing")
    require(selection.get("selectedPixels") == 8, "FOR-391 selected count changed")
    require(selection.get("improvedPixelsRecovered") == 8, "FOR-391 improvement count changed")
    require(selection.get("regressionsIncluded") == 0, "FOR-391 regression count changed")
    require(len(selection.get("selectedPixelCoordinates", [])) == 8, "FOR-391 selected coordinates changed")

    renderer = read_source(RENDERER_SOURCE)
    shader = read_source(AA_STENCIL_COVER_SHADER)

    for needle in (
        'WEBGPU_M60_F16_SOURCE_FACING_LANE_SHADER_READBACK_FLAG: String =',
        f'"{READBACK_GUARD}"',
        'm60F16SourceFacingLaneShaderReadbackDiagnosticsEnabled',
        'System.getProperty(WEBGPU_M60_F16_SOURCE_FACING_LANE_SHADER_READBACK_FLAG, "false").toBoolean()',
        'WEBGPU_M60_F16_AA_STENCIL_COVER_BAND_METADATA_TRANSPORT_FLAG: String =',
        f'"{TRANSPORT_GUARD}"',
        f'"{RUNTIME_GUARD}"',
        f'"{OLD_PROBE_GUARD}"',
        'FOR258_SHADER_SIDE_PROBE_PROPERTY: String = "kanvas.webgpu.for258.shaderSideProbe"',
        'for258ShaderSideProbePipelineLazy',
        'GPUBufferUsage.Storage or GPUBufferUsage.CopySrc or GPUBufferUsage.CopyDst',
    ):
        require(needle in renderer, f"renderer source missing proof: {needle}")

    for needle in (
        'm60F16BandMetadata0: vec4f',
        'm60F16BandMetadata1: vec4f',
        'fn m60_f16_band_metadata_enabled() -> bool',
        'fn m60_f16_local_x(pixel: vec2f) -> f32',
        'fn m60_f16_candidate_lane(pixel: vec2f) -> bool',
        'band_id == 1u && local_x <= left_max',
        'band_id == 2u && local_x >= right_min',
    ):
        require(needle in shader, f"shader source missing proof: {needle}")

    for entry_point in ("fs_inside", "fs_outside"):
        body = function_body(shader, entry_point)
        require("m60_f16_candidate_lane" not in body, f"{entry_point} must not call the FOR-394 helper")
        require("m60F16BandMetadata" not in body, f"{entry_point} must not read metadata directly")

    forbidden_shader_side_outputs = (
        "var<storage, read_write> m60",
        "m60F16SourceFacingLaneShaderReadback",
        "@location(1)",
        "readback",
    )
    for forbidden in forbidden_shader_side_outputs:
        require(forbidden not in shader, f"shader unexpectedly added a FOR-395 side output: {forbidden}")

    return for394, for391, renderer, shader


def build_artifact() -> dict[str, Any]:
    for394, for391, _renderer, _shader = validate_sources()
    selection = for391["selection"]
    ideal = selection["estimatedFullSceneResidualIfAppliedToThisSubset"]
    expected_pixels = selection["selectedPixelCoordinates"]
    return {
        "schemaVersion": 1,
        "linear": LINEAR_ID,
        "sceneId": "m60-f16-source-facing-lane-shader-readback-for395",
        "decision": DECISION,
        "classification": CLASSIFICATION,
        "allowedClassifications": ALLOWED_CLASSIFICATIONS,
        "sourceMemory": SOURCE_MEMORY,
        "sourceFinding": SOURCE_FINDING,
        "sourceArtifact": rel(FOR394_ARTIFACT),
        "expectedLaneSourceArtifact": rel(FOR391_ARTIFACT),
        "producer": "scripts/validate_for395_m60_f16_source_facing_lane_shader_readback.py",
        "limitedTo": "M60 F16 bounded stroke cap/join AA stencil-cover diagnostic path",
        "supportClaim": False,
        "promoted": False,
        "explicitDiagnosticReadbackGuard": {
            "guardId": READBACK_GUARD,
            "enabledByDefault": False,
            "recognizedByRenderer": True,
            "readbackPipelineInstalled": False,
            "readbackWritesWhenDisabled": False,
        },
        "reusesFor394Transport": {
            "guardId": TRANSPORT_GUARD,
            "enabledByDefault": False,
            "sourceArtifactClassification": for394["classification"],
            "helper": "m60_f16_candidate_lane",
            "transportSlots": ["m60F16BandMetadata0", "m60F16BandMetadata1"],
        },
        "shaderReadbackStatus": {
            "status": CLASSIFICATION,
            "directReadbackAvailable": False,
            "nonColorChannelAvailable": False,
            "reason": (
                "The existing FOR-258 diagnostic compute probe can sample a texture after normal rendering, "
                "but the AA stencil-cover path has no side channel that records the boolean value returned by "
                "m60_f16_candidate_lane during fs_inside/fs_outside execution."
            ),
            "minimalExtensionNeeded": (
                "Add an opt-in diagnostic storage buffer or separate diagnostic render target bound to the "
                "AA stencil-cover fragment pass, recording pixel coordinate plus m60_f16_candidate_lane "
                "without feeding final color, coverage, fallback, scoring, thresholds, promotion, or FOR-380."
            ),
        },
        "pixelComparison": {
            "comparisonStatus": "not-executed-missing-extension",
            "expectedUsefulPixels": expected_pixels,
            "expectedUsefulPixelCount": 8,
            "shaderObservedPixels": [],
            "shaderObservedPixelCount": 0,
            "falsePositives": [],
            "falsePositiveCount": 0,
            "falseNegatives": [],
            "falseNegativeCount": 0,
            "unobservedExpectedPixels": expected_pixels,
            "exactMatchProven": False,
            "zeroFalsePositiveFalseNegativeProven": False,
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
            "m60_f16_candidate_laneConnectedToFsInside": False,
            "m60_f16_candidate_laneConnectedToFsOutside": False,
            "finalColorChanged": False,
            "coverageChanged": False,
            "fallbackChanged": False,
            "scoringChanged": False,
            "thresholdChanged": False,
            "promotionChanged": False,
            "for380ProbeRouteUsed": False,
            "generalizedOutsideM60F16": False,
        },
        "validationCommands": VALIDATION_COMMANDS,
    }


def validate_artifact(artifact: dict[str, Any]) -> None:
    require(artifact.get("decision") == DECISION, "decision changed")
    require(artifact.get("classification") == CLASSIFICATION, "classification changed")
    require(artifact.get("classification") in artifact.get("allowedClassifications", []),
            "classification is not in the allowed FOR-395 set")
    guard = artifact.get("explicitDiagnosticReadbackGuard")
    require(isinstance(guard, dict), "readback guard missing")
    require(guard.get("guardId") == READBACK_GUARD, "readback guard id changed")
    require(guard.get("enabledByDefault") is False, "readback guard default changed")
    require(guard.get("recognizedByRenderer") is True, "readback guard must be renderer-recognized")
    require(guard.get("readbackPipelineInstalled") is False, "refusal must not install readback pipeline")

    transport = artifact.get("reusesFor394Transport")
    require(isinstance(transport, dict), "FOR-394 transport proof missing")
    require(transport.get("guardId") == TRANSPORT_GUARD, "FOR-394 transport guard changed")
    require(transport.get("helper") == "m60_f16_candidate_lane", "shader helper changed")

    status = artifact.get("shaderReadbackStatus")
    require(isinstance(status, dict), "shader readback status missing")
    require(status.get("status") == CLASSIFICATION, "shader readback status changed")
    require(status.get("directReadbackAvailable") is False, "direct readback availability changed")
    require(status.get("nonColorChannelAvailable") is False, "non-color channel availability changed")
    require("diagnostic storage buffer" in status.get("minimalExtensionNeeded", ""),
            "minimal extension must name the storage-buffer route")

    comparison = artifact.get("pixelComparison")
    require(isinstance(comparison, dict), "pixel comparison missing")
    require(comparison.get("comparisonStatus") == "not-executed-missing-extension",
            "comparison status changed")
    require(comparison.get("expectedUsefulPixelCount") == 8, "expected pixel count changed")
    require(len(comparison.get("expectedUsefulPixels", [])) == 8, "expected pixels missing")
    require(comparison.get("shaderObservedPixels") == [], "refusal must not claim observed pixels")
    require(comparison.get("falsePositives") == [], "refusal must not claim measured false positives")
    require(comparison.get("falseNegatives") == [], "refusal must not claim measured false negatives")
    require(comparison.get("unobservedExpectedPixels") == comparison.get("expectedUsefulPixels"),
            "all expected pixels must remain unobserved in refusal mode")
    require(comparison.get("exactMatchProven") is False, "refusal must not prove exact match")
    require(comparison.get("zeroFalsePositiveFalseNegativeProven") is False,
            "refusal must not prove zero false positives/negatives")

    predicate = artifact.get("candidatePredicate")
    require(isinstance(predicate, dict), "candidate predicate missing")
    require(predicate.get("idealSelectedPixels") == 8, "ideal selected count changed")
    require(predicate.get("idealImprovedPixelsRecovered") == 8, "ideal improvement count changed")
    require(predicate.get("idealRegressionsIncluded") == 0, "ideal regression count changed")
    ideal = predicate.get("idealFullSceneImpactIfRuntimeWereEventuallyProven")
    require(isinstance(ideal, dict), "ideal impact missing")
    require(ideal.get("baseUncorrectedFullSceneResidual") == 2014, "base residual changed")
    require(ideal.get("simulatedAfterResidual") == 1949, "ideal residual changed")

    non_goals = artifact.get("nonGoalsPreserved")
    require(isinstance(non_goals, dict), "non-goals missing")
    for key, value in non_goals.items():
        require(value is False, f"non-goal not preserved: {key}")


def render_report(artifact: dict[str, Any]) -> str:
    comparison = artifact["pixelComparison"]
    status = artifact["shaderReadbackStatus"]
    guard = artifact["explicitDiagnosticReadbackGuard"]
    predicate = artifact["candidatePredicate"]
    ideal = predicate["idealFullSceneImpactIfRuntimeWereEventuallyProven"]
    pixels = "\n".join(
        f"- ({pixel['x']}, {pixel['y']})"
        for pixel in comparison["expectedUsefulPixels"]
    )
    validations = "\n".join(f"- `{command}`" for command in artifact["validationCommands"])
    return f"""# FOR-395 M60 F16 source-facing lane shader readback

Decision: `{artifact["decision"]}`

Classification: `{artifact["classification"]}`

Artifact: `{rel(ARTIFACT)}`

FOR-395 refuse l'activation d'une preuve shader par readback pour
`m60_f16_candidate_lane`, parce que le chemin `aa_stencil_cover.wgsl` ne dispose
pas encore d'un canal non-couleur capable d'enregistrer la valeur booleenne du
helper pendant `fs_inside` / `fs_outside`.

## Garde diagnostique

- garde: `{guard["guardId"]}`
- active par defaut: `{guard["enabledByDefault"]}`
- reconnue par le renderer: `{guard["recognizedByRenderer"]}`
- pipeline de readback installe: `{guard["readbackPipelineInstalled"]}`

## Resultat

Le transport FOR-394 est conserve et reutilise comme source unique :
`m60F16BandMetadata0`, `m60F16BandMetadata1` et le helper
`m60_f16_candidate_lane` restent disponibles dans le shader. Le helper n'est pas
raccorde a la couleur finale, a la couverture, aux fallbacks, au scoring, aux
seuils, a la promotion, ni a la route FOR-380.

Statut de comparaison: `{comparison["comparisonStatus"]}`

Pixels utiles attendus depuis FOR-391 :

{pixels}

Pixels observes cote shader: `{comparison["shaderObservedPixelCount"]}`.
Aucun faux positif ou faux negatif mesure n'est revendique, car la comparaison
n'a pas ete executee. Les 8 pixels restent dans `unobservedExpectedPixels`.

## Blocage concret

{status["reason"]}

Extension minimale :

{status["minimalExtensionNeeded"]}

## Impact ideal conserve

- pixels utiles: `{predicate["idealSelectedPixels"]}`
- pixels ameliores: `{predicate["idealImprovedPixelsRecovered"]}`
- regressions incluses: `{predicate["idealRegressionsIncluded"]}`
- residu plein scene ideal: `{ideal["baseUncorrectedFullSceneResidual"]}` -> `{ideal["simulatedAfterResidual"]}`

## Non-objectifs preserves

- aucune correction M60 F16 activee ;
- aucun raccordement a `fs_inside` / `fs_outside` ;
- aucune modification de couleur finale, couverture, fallback, scoring, seuil ou promotion ;
- aucune utilisation de l'ancien probe draw-wide FOR-380.

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
        "FOR-395 validation passed: "
        f"{CLASSIFICATION}; expected pixels={artifact['pixelComparison']['expectedUsefulPixelCount']}; "
        f"observed pixels={artifact['pixelComparison']['shaderObservedPixelCount']}"
    )


if __name__ == "__main__":
    main()
