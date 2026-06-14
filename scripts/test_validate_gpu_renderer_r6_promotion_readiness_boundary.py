#!/usr/bin/env python3
import contextlib
import io
import json
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_gpu_renderer_r6_promotion_readiness_boundary as validator
import validate_gpu_renderer_r6_executed_pm_evidence_bundle as executed_validator


DEFAULT_MISSING = [
    "route",
    "resource-decision",
    "submission",
    "readback",
    "pipeline-cache",
]
EXECUTED_ARTIFACT_NAMES = [
    "diagnostic-webgpu-first-route-pm-evidence-01-command.txt",
    "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt",
    "diagnostic-webgpu-first-route-pm-evidence-04-material.txt",
    "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt",
    "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt",
    "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt",
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt",
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt",
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt",
    "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt",
    "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt",
    "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt",
    "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt",
    "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt",
    "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt",
]
ROOT_ARTIFACT_NAMES = [
    "gpu-renderer-first-route-pm-evidence-01-command.txt",
    "gpu-renderer-first-route-pm-evidence-02-analysis.txt",
    "gpu-renderer-first-route-pm-evidence-03-route.txt",
    "gpu-renderer-first-route-pm-evidence-04-material.txt",
    "gpu-renderer-first-route-pm-evidence-05-wgsl.txt",
    "gpu-renderer-first-route-pm-evidence-06-payload.txt",
    "gpu-renderer-first-route-pm-evidence-07-pipeline-key.txt",
    "gpu-renderer-first-route-pm-evidence-08-resource-decision.txt",
    "gpu-renderer-first-route-pm-evidence-09-submission.txt",
    "gpu-renderer-first-route-pm-evidence-10-readback.txt",
    "gpu-renderer-first-route-pm-evidence-11-telemetry.txt",
    "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt",
    "gpu-renderer-first-route-pm-evidence-13-negative-cpu-fallback.txt",
    "gpu-renderer-first-route-pm-evidence-14-unsupported-route-refusals.txt",
]


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def shadow_product_route_activation(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    marker = '"productRouteActivated": false'
    index = text.index(marker)
    line_start = text.rfind("\n", 0, index) + 1
    indent = text[line_start:index]
    path.write_text(
        text[:index] + f'"productRouteActivated": true,\n{indent}' + text[index:],
        encoding="utf-8",
    )


def sha256_text(text: str) -> str:
    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def executed_artifact_text(artifact_name: str) -> str:
    lines = [f"{artifact_name}: fixture evidence"]
    for snippet in executed_validator.REQUIRED_ARTIFACT_SNIPPETS[artifact_name]:
        if snippet == "payloadHash=sha256:":
            lines.append("payloadHash=sha256:" + "0" * 64)
        else:
            lines.append(snippet)
    return "\n".join(lines) + "\n"


def default_entry(**overrides: object) -> dict:
    entry = {
        "key": "gpuRendererR6FirstRoutePmEvidence",
        "claimLevel": "gpu-renderer-r6-pm-evidence-only",
        "status": "Incomplete",
        "promotionGate": "first-route-promotion",
        "promotionGatePassed": False,
        "missingEvidence": DEFAULT_MISSING,
        "artifactDirectory": "release/gpu-renderer-r6-first-route-pm-evidence",
        "manifestArtifact": (
            "release/gpu-renderer-r6-first-route-pm-evidence/"
            "gpu-renderer-first-route-pm-evidence-00-manifest.txt"
        ),
        "manifestEntryJson": "release/gpu-renderer-r6-first-route-pm-evidence/pm-bundle-manifest-entry.json",
        "artifactCount": 15,
        "dumpArtifactCount": 14,
        "generationCommand": "rtk ./gradlew --no-daemon :gpu-renderer:gpuRendererR6FirstRoutePmEvidenceBundle",
        "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
        "productRouteActivated": False,
        "nativeKadreCiRequired": False,
        "webGpuAdapterRequired": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "validationReportName": "gpu-renderer-first-route-pm-evidence",
        "nonClaims": [
            "No product route activation.",
            "No WebGPU adapter, Kadre window, or native demo requirement for this bundle.",
            "No first-route support claim while promotion evidence is missing.",
            "No hidden CPU-rendered texture fallback.",
        ],
        "notice": (
            "The GPU renderer R6 bundle packages validation-owned first-route PM evidence. "
            "The default artifact is refusal-first and incomplete; it is review evidence, "
            "not a product support claim."
        ),
    }
    entry.update(overrides)
    return entry


def executed_summary(**overrides: object) -> dict:
    summary = {
        "key": "gpuRendererR6ExecutedFirstRoutePmEvidence",
        "claimLevel": "gpu-renderer-r6-executed-diagnostic-pm-evidence-only",
        "status": "Passed",
        "validationReportName": "diagnostic-webgpu-first-route-pm-evidence",
        "promotionGate": "first-route-promotion",
        "promotionGatePassed": True,
        "missingEvidence": [],
        "scope": "pm-evidence-only",
        "artifactDirectory": "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence",
        "manifestArtifact": (
            "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence/"
            "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"
        ),
        "artifactCount": 19,
        "dumpArtifactCount": 18,
        "generationCommand": (
            "rtk ./gradlew --no-daemon "
            ":gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle"
        ),
        "validationCommand": (
            "rtk python3 scripts/validate_gpu_renderer_r6_executed_pm_evidence_bundle.py . "
            "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
        ),
        "productRouteActivated": False,
        "rootPipelinePmBundleDependency": False,
        "nativeKadreCiRequired": False,
        "webGpuAdapterRequired": True,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "nonClaims": [
            "No product route activation.",
            "No root pipelinePmBundle dependency on adapter-backed evidence.",
            "No release readiness movement from this diagnostic lane.",
            "No raw readback payloads, backend handles, or WGSL source are exported.",
        ],
        "notice": (
            "The executed GPU renderer R6 bundle is adapter-backed diagnostic PM evidence. "
            "It proves one opt-in WebGPU first-route submit/readback path for review, "
            "but it is not root PM packaging and not product support activation."
        ),
    }
    summary.update(overrides)
    return summary


def write_executed_artifact_fixture(root: Path) -> tuple[Path, str, dict[str, str]]:
    output_dir = root / "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
    output_dir.mkdir(parents=True, exist_ok=True)
    artifact_hashes: dict[str, str] = {}
    manifest_lines = [
        "validation.report.name=diagnostic-webgpu-first-route-pm-evidence",
        "validation.report.status=Passed",
        "validation.gate.name=first-route-promotion",
        "validation.gate.passed=true",
        "validation.gate.missingEvidence=none",
        "validation.bundle.scope=pm-evidence-only",
        f"validation.report.artifacts={len(EXECUTED_ARTIFACT_NAMES)}",
        "validation.report.diagnostics=0",
        "validation.gate.diagnostics=0",
    ]
    for index, artifact_name in enumerate(EXECUTED_ARTIFACT_NAMES, start=1):
        text = executed_artifact_text(artifact_name)
        artifact_hashes[artifact_name] = sha256_text(text)
        (output_dir / artifact_name).write_text(text, encoding="utf-8")
        ordinal = f"{index:02d}"
        manifest_lines.extend(
            [
                f"artifact.{ordinal}.name={artifact_name}",
                f"artifact.{ordinal}.lines={executed_validator.line_count_for_exported_text(text)}",
                f"artifact.{ordinal}.sha256={artifact_hashes[artifact_name]}",
            ]
        )
    manifest_text = "\n".join(manifest_lines) + "\n"
    manifest_path = output_dir / "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"
    manifest_path.write_text(manifest_text, encoding="utf-8")
    return output_dir, sha256_text(manifest_text), artifact_hashes


def write_root_artifact_fixture(root: Path) -> Path:
    output_dir = root / "build/reports/wgsl-pipeline-pm-bundle/release/gpu-renderer-r6-first-route-pm-evidence"
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_lines = [
        "validation.report.name=gpu-renderer-first-route-pm-evidence",
        "validation.report.status=Incomplete",
        "validation.gate.name=first-route-promotion",
        "validation.gate.passed=false",
        "validation.gate.missingEvidence=route,resource-decision,submission,readback,pipeline-cache",
        "validation.bundle.scope=pm-evidence-only",
        f"validation.report.artifacts={len(ROOT_ARTIFACT_NAMES)}",
        "validation.report.diagnostics=6",
        "validation.gate.diagnostics=7",
    ]
    for index, artifact_name in enumerate(ROOT_ARTIFACT_NAMES, start=1):
        text = f"{artifact_name}: root refusal-first fixture evidence\n"
        (output_dir / artifact_name).write_text(text, encoding="utf-8")
        ordinal = f"{index:02d}"
        manifest_lines.extend(
            [
                f"artifact.{ordinal}.name={artifact_name}",
                "artifact.{ordinal}.lines=1".format(ordinal=ordinal),
                f"artifact.{ordinal}.sha256={sha256_text(text)}",
            ]
        )
    manifest_lines.extend(
        [
            "diagnostic.01=first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
            "diagnostic.02=route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
            "diagnostic.03=resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
            "diagnostic.04=submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
            "diagnostic.05=readback requires GPUReadbackResult.Completed",
            "diagnostic.06=pipeline-cache requires GPUCacheTelemetry.pipeline",
            "gateDiagnostic.01=validation report status is Incomplete",
            "gateDiagnostic.02=first-route PM evidence incomplete: route, resource-decision, submission, readback, pipeline-cache",
            "gateDiagnostic.03=route requires GPURouteDecision.Native but found GPURouteDecision.Refused",
            "gateDiagnostic.04=resource-decision requires GPUResourceMaterializationDecision.Materialized but found GPUResourceMaterializationDecision.Refused",
            "gateDiagnostic.05=submission requires GPUCommandSubmission.Submitted but found GPUCommandSubmission.Refused",
            "gateDiagnostic.06=readback requires GPUReadbackResult.Completed",
            "gateDiagnostic.07=pipeline-cache requires GPUCacheTelemetry.pipeline",
        ]
    )
    (output_dir / "gpu-renderer-first-route-pm-evidence-00-manifest.txt").write_text(
        "\n".join(manifest_lines) + "\n",
        encoding="utf-8",
    )
    return output_dir


def update_root_manifest_artifact_digest(pm_bundle_dir: Path, artifact_name: str, text: str) -> None:
    release_dir = pm_bundle_dir / "release/gpu-renderer-r6-first-route-pm-evidence"
    manifest_path = release_dir / "gpu-renderer-first-route-pm-evidence-00-manifest.txt"
    manifest_lines = manifest_path.read_text(encoding="utf-8").splitlines()
    artifact_index = ROOT_ARTIFACT_NAMES.index(artifact_name) + 1
    ordinal = f"{artifact_index:02d}"
    updated_lines = []
    for line in manifest_lines:
        if line.startswith(f"artifact.{ordinal}.lines="):
            updated_lines.append(f"artifact.{ordinal}.lines={executed_validator.line_count_for_exported_text(text)}")
        elif line.startswith(f"artifact.{ordinal}.sha256="):
            updated_lines.append(f"artifact.{ordinal}.sha256={sha256_text(text)}")
        else:
            updated_lines.append(line)
    manifest_path.write_text("\n".join(updated_lines) + "\n", encoding="utf-8")


def write_bundle_fixture(
    root: Path,
    *,
    entry: dict | None = None,
    manifest_overrides: dict | None = None,
    summary: dict | None = None,
) -> tuple[Path, Path]:
    pm_bundle_dir = root / "build/reports/wgsl-pipeline-pm-bundle"
    release_entry = pm_bundle_dir / "release/gpu-renderer-r6-first-route-pm-evidence/pm-bundle-manifest-entry.json"
    entry_payload = entry or default_entry()
    write_root_artifact_fixture(root)
    write_json(release_entry, entry_payload)

    manifest = {
        "generatedBy": "pipelinePmBundle",
        "gpuRendererR6FirstRoutePmEvidence": entry_payload,
    }
    if manifest_overrides:
        manifest.update(manifest_overrides)
    write_json(pm_bundle_dir / "manifest.json", manifest)

    executed_summary_path = (
        root /
        "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence/"
        "diagnostic-webgpu-first-route-pm-evidence-summary.json"
    )
    if summary is not None:
        _, manifest_hash, artifact_hashes = write_executed_artifact_fixture(root)
        summary_payload = dict(summary)
        summary_payload.setdefault("manifestSha256", manifest_hash)
        summary_payload.setdefault("artifactHashes", artifact_hashes)
        write_json(executed_summary_path, summary_payload)

    return pm_bundle_dir, executed_summary_path


class GpuRendererR6PromotionReadinessBoundaryValidatorTest(unittest.TestCase):
    def test_validate_boundary_keeps_executed_evidence_from_activating_product_route(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pm_bundle_dir, summary_path = write_bundle_fixture(
                Path(temp),
                summary=executed_summary(),
            )

            result = validator.validate_boundary(pm_bundle_dir, summary_path)

        self.assertEqual("promotion-boundary-held", result["classification"])
        self.assertEqual("Incomplete", result["rootDefaultBundle"]["status"])
        self.assertFalse(result["rootDefaultBundle"]["promotionGatePassed"])
        self.assertEqual(DEFAULT_MISSING, result["rootDefaultBundle"]["missingEvidence"])
        self.assertEqual("Passed", result["executedDiagnosticEvidence"]["status"])
        self.assertTrue(result["executedDiagnosticEvidence"]["promotionGatePassed"])
        self.assertTrue(result["executedDiagnosticEvidence"]["webGpuAdapterRequired"])
        self.assertFalse(result["productRouteActivated"])
        self.assertFalse(result["releaseBlocking"])
        self.assertEqual(0.0, result["readinessDelta"])
        self.assertTrue(result["promotionDecisionRequired"])

    def test_validate_boundary_rejects_promoted_root_default_bundle(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pm_bundle_dir, summary_path = write_bundle_fixture(
                Path(temp),
                entry=default_entry(status="Passed", promotionGatePassed=True, missingEvidence=[]),
                summary=executed_summary(),
            )

            with self.assertRaisesRegex(validator.ValidationError, "root default PM evidence must remain Incomplete"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_duplicate_product_route_keys_in_json_evidence_inputs(self) -> None:
        input_paths = [
            (
                "root release manifest entry",
                lambda pm_bundle_dir, summary_path: (
                    pm_bundle_dir /
                    "release/gpu-renderer-r6-first-route-pm-evidence/pm-bundle-manifest-entry.json"
                ),
            ),
            (
                "root PM manifest entry",
                lambda pm_bundle_dir, summary_path: pm_bundle_dir / "manifest.json",
            ),
            (
                "executed summary",
                lambda pm_bundle_dir, summary_path: summary_path,
            ),
        ]

        for label, input_path in input_paths:
            with self.subTest(label=label):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        summary=executed_summary(),
                    )
                    shadow_product_route_activation(input_path(pm_bundle_dir, summary_path))

                    with self.assertRaisesRegex(
                        validator.ValidationError,
                        "duplicate JSON key: productRouteActivated",
                    ):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_executed_summary_in_root_pm_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pm_bundle_dir, summary_path = write_bundle_fixture(
                Path(temp),
                manifest_overrides={
                    "gpuRendererR6ExecutedFirstRoutePmEvidence": executed_summary(),
                },
                summary=executed_summary(),
            )

            with self.assertRaisesRegex(validator.ValidationError, "root PM manifest must not contain"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_root_manifest_default_entry_claim_drift(self) -> None:
        unsafe_manifest_entries = [
            default_entry(status="Passed"),
            default_entry(productRouteActivated=True),
            default_entry(releaseBlocking=True),
            default_entry(readinessDelta=1.0),
            default_entry(readinessDelta=False),
            default_entry(webGpuAdapterRequired=True),
            default_entry(nativeKadreCiRequired=True),
            default_entry(supportClaim=True),
            default_entry(activationNote="ready for release"),
        ]

        for manifest_entry in unsafe_manifest_entries:
            with self.subTest(manifest_entry=manifest_entry):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        manifest_overrides={
                            "gpuRendererR6FirstRoutePmEvidence": manifest_entry,
                        },
                        summary=executed_summary(),
                    )

                    with self.assertRaises(validator.ValidationError):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_top_level_root_pm_manifest_claim_fields(self) -> None:
        unsafe_top_level_claims = [
            {"productRouteActivated": True},
            {"releaseBlocking": True},
            {"readinessDelta": 1.0},
            {"promotionGatePassed": True},
            {"supportClaim": True},
            {"nativeKadreCiRequired": True},
            {"webGpuAdapterRequired": True},
        ]

        for manifest_overrides in unsafe_top_level_claims:
            with self.subTest(manifest_overrides=manifest_overrides):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        manifest_overrides=manifest_overrides,
                        summary=executed_summary(),
                    )

                    with self.assertRaisesRegex(validator.ValidationError, "root PM manifest top-level claim"):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_root_default_entry_provenance_drift(self) -> None:
        unsafe_entries = [
            default_entry(validationReportName="diagnostic-webgpu-first-route-pm-evidence"),
            default_entry(artifactDirectory="gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"),
            default_entry(manifestArtifact="release/gpu-renderer-r6-first-route-pm-evidence/other-manifest.txt"),
            default_entry(manifestEntryJson="release/gpu-renderer-r6-first-route-pm-evidence/other-entry.json"),
            default_entry(artifactCount=19),
            default_entry(dumpArtifactCount=18),
            default_entry(generationCommand="rtk ./gradlew --no-daemon :gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle"),
            default_entry(pmPackageCommand="rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary"),
            default_entry(nonClaims=["No product route activation."]),
            default_entry(notice="The bundle is complete and ready."),
        ]

        for unsafe_entry in unsafe_entries:
            with self.subTest(unsafe_entry=unsafe_entry):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        entry=unsafe_entry,
                        summary=executed_summary(),
                    )

                    with self.assertRaises(validator.ValidationError):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_alternate_key_executed_evidence_in_root_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pm_bundle_dir, summary_path = write_bundle_fixture(
                Path(temp),
                manifest_overrides={
                    "renamedGpuRendererExecutedEvidence": executed_summary(
                        key="renamedGpuRendererExecutedEvidence",
                    ),
                },
                summary=executed_summary(),
            )

            with self.assertRaisesRegex(validator.ValidationError, "adapter-backed executed evidence"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_nested_executed_evidence_in_root_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            pm_bundle_dir, summary_path = write_bundle_fixture(
                Path(temp),
                manifest_overrides={
                    "renamedEvidence": {
                        "nested": {
                            "label": "neutral",
                            "detail": "diagnostic-webgpu-first-route-pm-evidence-summary.json",
                        },
                    },
                },
                summary=executed_summary(),
            )

            with self.assertRaisesRegex(validator.ValidationError, "adapter-backed executed evidence"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_hidden_executed_files_in_root_pm_bundle(self) -> None:
        hidden_paths = [
            "release/gpu-renderer-r6-executed-first-route-pm-evidence/diagnostic-webgpu-first-route-pm-evidence-summary.json",
            "release/gpu-renderer-r6-first-route-pm-evidence/diagnostic-webgpu-first-route-pm-evidence-01-command.txt",
            "release/gpu-renderer-r6-first-route-pm-evidence/renamed-command.txt",
        ]

        for hidden_path in hidden_paths:
            with self.subTest(hidden_path=hidden_path):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        summary=executed_summary(),
                    )
                    hidden_file = pm_bundle_dir / hidden_path
                    hidden_file.parent.mkdir(parents=True, exist_ok=True)
                    hidden_file.write_text(
                        "commands:NormalizedDrawCommand.FillRect:recording recording.webgpu-submit commandIds=42\n",
                        encoding="utf-8",
                    )

                    with self.assertRaisesRegex(validator.ValidationError, "root PM bundle must not contain"):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_executed_summary_product_or_release_claims(self) -> None:
        unsafe_summaries = [
            executed_summary(productRouteActivated=True),
            executed_summary(rootPipelinePmBundleDependency=True),
            executed_summary(releaseBlocking=True),
            executed_summary(readinessDelta=1.0),
            executed_summary(readinessDelta=False),
            executed_summary(scope="release-gate"),
        ]

        for unsafe_summary in unsafe_summaries:
            with self.subTest(unsafe_summary=unsafe_summary):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        summary=unsafe_summary,
                    )

                    with self.assertRaises(validator.ValidationError):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_executed_summary_provenance_drift(self) -> None:
        unsafe_summaries = [
            executed_summary(validationReportName="gpu-renderer-first-route-pm-evidence"),
            executed_summary(artifactDirectory="build/reports/wgsl-pipeline-pm-bundle/release/gpu-renderer-r6-first-route-pm-evidence"),
            executed_summary(manifestArtifact="gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence/other-manifest.txt"),
            executed_summary(artifactCount=18),
            executed_summary(dumpArtifactCount=17),
            executed_summary(generationCommand="rtk ./gradlew --no-daemon :gpu-raster:someOtherEvidenceTask"),
            executed_summary(validationCommand="rtk python3 scripts/other_validator.py . gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"),
            executed_summary(nonClaims=["No product route activation."]),
            executed_summary(notice="This evidence activates the product route."),
        ]

        for unsafe_summary in unsafe_summaries:
            with self.subTest(unsafe_summary=unsafe_summary):
                with tempfile.TemporaryDirectory() as temp:
                    pm_bundle_dir, summary_path = write_bundle_fixture(
                        Path(temp),
                        summary=unsafe_summary,
                    )

                    with self.assertRaises(validator.ValidationError):
                        validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_stale_or_mismatched_executed_artifact_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            manifest_path = summary_path.parent / "diagnostic-webgpu-first-route-pm-evidence-00-manifest.txt"
            manifest_path.unlink()

            with self.assertRaisesRegex(validator.ValidationError, "executed manifest"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            artifact_path = summary_path.parent / "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"
            artifact_path.write_text("diagnostic-webgpu-first-route-pm-evidence-10-readback.txt: stale artifact\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "executed artifact hash mismatch"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_unexpected_executed_diagnostic_files_even_when_summary_hashes_match(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            extra_path = summary_path.parent / "unmanifested-diagnostic-note.txt"
            extra_path.write_text("unmanifested executed diagnostic note\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "executed PM bundle contains unexpected files"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_stale_root_release_artifact_hashes(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            artifact_path = (
                pm_bundle_dir /
                "release/gpu-renderer-r6-first-route-pm-evidence/"
                "gpu-renderer-first-route-pm-evidence-10-readback.txt"
            )
            artifact_path.write_text(
                "gpu-renderer-first-route-pm-evidence-10-readback.txt: stale root artifact\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "root PM evidence release copy.*hash mismatch"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_root_native_route_artifact_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            artifact_name = "gpu-renderer-first-route-pm-evidence-03-route.txt"
            artifact_path = pm_bundle_dir / "release/gpu-renderer-r6-first-route-pm-evidence" / artifact_name
            text = (
                "routing:GPURouteDecision.Native:root route claim should not appear in refusal-first PM evidence\n"
            )
            artifact_path.write_text(text, encoding="utf-8")
            update_root_manifest_artifact_digest(pm_bundle_dir, artifact_name, text)

            with self.assertRaisesRegex(validator.ValidationError, "adapter-backed executed files"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_root_pipeline_cache_positive_artifact_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            artifact_name = "gpu-renderer-first-route-pm-evidence-12-pipeline-cache.txt"
            artifact_path = pm_bundle_dir / "release/gpu-renderer-r6-first-route-pm-evidence" / artifact_name
            text = (
                "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:"
                "key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit\n"
            )
            artifact_path.write_text(text, encoding="utf-8")
            update_root_manifest_artifact_digest(pm_bundle_dir, artifact_name, text)

            with self.assertRaisesRegex(validator.ValidationError, "adapter-backed executed files"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_validate_boundary_rejects_unmanifested_root_release_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            extra_path = (
                pm_bundle_dir /
                "release/gpu-renderer-r6-first-route-pm-evidence/"
                "unmanifested-review-note.txt"
            )
            extra_path.write_text("unmanifested safe-looking PM note\n", encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "unexpected root PM evidence (release copy paths|files)"):
                validator.validate_boundary(pm_bundle_dir, summary_path)

    def test_main_rejects_pipeline_pm_bundle_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_bundle_fixture(root, summary=executed_summary())
            (root / "build.gradle.kts").write_text(
                """
tasks.register("pipelinePmBundle") {
    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")
}
""".lstrip(),
                encoding="utf-8",
            )

            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as raised:
                    validator.main([
                        "validate_gpu_renderer_r6_promotion_readiness_boundary.py",
                        str(root),
                        "--require-executed-summary",
                    ])

        self.assertNotEqual(0, raised.exception.code)
        message = stderr.getvalue()
        self.assertIn("pipeline isolation", message)
        self.assertIn("adapter-backed", message)

    def test_validate_boundary_writes_markdown_report_without_support_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            pm_bundle_dir, summary_path = write_bundle_fixture(root, summary=executed_summary())
            report_path = root / "reports/gpu-renderer/r6-promotion-readiness-boundary.md"

            result = validator.validate_boundary(pm_bundle_dir, summary_path)
            validator.write_markdown_report(result, report_path)

            report = report_path.read_text(encoding="utf-8")

        self.assertIn("# GPU Renderer R6 Promotion Readiness Boundary", report)
        self.assertIn("Classification: `promotion-boundary-held`", report)
        self.assertIn("Product route activated: `false`", report)
        self.assertIn("Release blocking: `false`", report)
        self.assertIn("Readiness delta: `0.0`", report)
        self.assertIn("Explicit release/product activation decision", report)

    def test_gradle_wiring_exposes_adapter_backed_boundary_validation_task(self) -> None:
        build_gradle = (PROJECT_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        task_marker = 'tasks.register<Exec>("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary")'
        task_start = build_gradle.index(task_marker)
        task_end = build_gradle.find("\ntasks.", task_start + 1)
        if task_end == -1:
            task_end = len(build_gradle)
        task_body = build_gradle[task_start:task_end]

        self.assertIn('dependsOn("pipelinePmBundle")', task_body)
        self.assertIn('dependsOn("injectGpuRendererR6FirstRoutePmEvidenceIntoPmBundle")', task_body)
        self.assertIn('dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")', task_body)
        self.assertIn("validate_gpu_renderer_r6_promotion_readiness_boundary.py", task_body)
        self.assertIn("--write-report", task_body)
        self.assertIn("--require-executed-summary", task_body)

        self.assertNotIn(
            'dependsOn("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary")',
            build_gradle,
        )
        self.assertNotIn(
            'finalizedBy("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary")',
            build_gradle,
        )


if __name__ == "__main__":
    unittest.main()
