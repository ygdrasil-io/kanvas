#!/usr/bin/env python3
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(PROJECT_ROOT / "scripts"))

import validate_gpu_renderer_r6_executed_pm_evidence_bundle as validator


ARTIFACT_LINES = {
    "diagnostic-webgpu-first-route-pm-evidence-01-command.txt": [
        "commands:NormalizedDrawCommand.FillRect:recording recording.webgpu-submit commandIds=42 families=FillRect",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-02-analysis.txt": [
        "analysis:GPUDrawAnalysisRecord:recording recording.webgpu-submit records=analysis.fill_rect.42 decisionHash=analysis-decision:b0b43997",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-03-route.txt": [
        "routing:GPURouteDecision.Native:recording recording.webgpu-submit route:native.fill_rect.solid",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-04-material.txt": [
        "materials:GPUPaintPipelinePlan:recording recording.webgpu-submit materialKeyHashes=pending.material.solid",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt": [
        "wgsl:WGSLReflectionResult:diagnostic-webgpu-first-route-pm-evidence "
        "wgsl.reflection:accepted module=wgsl-module:2a15aeab075d4d88b4a8583cccbae008abb67c71a6f5d232f438a158b4cc249d "
        "parserState=parser-backed tool=wgsl4k source=wgsl4k-parser-ast:generated-solid-rect "
        "bindings=group=0/binding=0/role=uniforms/kind=uniform-buffer "
        "uniforms=layout:Struct_4:73d5713d9aab37d795297c5f12aee77f7d526a7c1166d4d66ebd3493c6e1209c:16B:color "
        "storageCount=0 diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-06-payload.txt": [
        "payloads:GPUPayloadGatherPlan:recording recording.webgpu-submit payload evidence for renderTasks=task.render.42",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-07-pipeline-key.txt": [
        "pipelines:GPUPipelineKeyPreimage.Render:recording recording.webgpu-submit pipelineKeyHashes=pending.pipeline.fill_rect.solid.rgba8unorm.src_over",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt": [
        "resources:GPUResourceMaterializationDecision.Materialized:diagnostic-webgpu-first-route-pm-evidence "
        "resource.materialization:materialized target=surface tasks=task.render.42 "
        "resourcePlans=webgpu.headless-target.surface resourceCount=1 diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt": [
        "execution:GPUCommandSubmission.Submitted:diagnostic-webgpu-first-route-pm-evidence "
        "execution.submission:submitted id=webgpu.first-route.1 deviceGeneration=1 targetGeneration=1 "
        "scopes=root-pass tasks=task.render.42 passes=pass.root.42 resources=surface:copy_src,render_attachment "
        "routes=GPUNative=1 readbacks=readback-1 diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt": [
        "execution:GPUReadbackResult.Completed:diagnostic-webgpu-first-route-pm-evidence "
        "execution.readback:completed request=readback-1 source=first-route-webgpu-submit "
        "bounds=0,0 16x16 format=rgba8unorm sync=after-submit expectedArtifact=first-route-fill.png "
        "failureReason=none bytes=1024 payloadHash=sha256:e1a381e957bd9e32d02e36f267d419f6f362331c75175c923a031ac6ca9fb70a diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-11-telemetry.txt": [
        "telemetry:GPUTelemetryLedger:counter:first_route.command.count:family=Rect:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.route.count:kind=GPUNative:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.wgsl_module_validation.count:outcome=Success:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.resource_materialization.count:outcome=Materialized:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.command_submission.count:outcome=Submitted:1:count",
        "telemetry:GPUTelemetryLedger:counter:first_route.negative_cpu_fallback.refusal.count:policy=forbidden:1:count",
        "telemetry:GPUCacheTelemetry.pipeline:cache:pipeline:hits=0:misses=1:evictions=0:residentBytes=0:pressureBytes=0",
        "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-12-pipeline-cache.txt": [
        "telemetry:GPUCacheTelemetry.pipeline:cache:pipeline:hits=0:misses=1:evictions=0:residentBytes=0:pressureBytes=0",
        "telemetry:GPUCacheTelemetry.pipeline:event:pipeline:Miss:key=render-pipeline:first-fill-rect:subject=webgpu:first-route-submit",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-13-negative-cpu-fallback.txt": [
        "routing:NegativeCPUFallbackRefusal:recording recording.webgpu-submit has no CPU-rendered texture fallback tasks",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt": [
        "routing:UnsupportedRouteFamilyRefusal:diagnostic-webgpu-first-route-pm-evidence "
        "unsupportedFamilies=perspective-transform,singular-transform,rrect-scale-transform,"
        "rrect-affine-transform,unsupported-target-format,unsupported-blend,non-simple-clip,"
        "layer-filter-destination-read,missing-capability,wgsl-validation-or-abi-mismatch diagnostics=none",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-15-recording-analysis.txt": [
        "recording:GPUAnalysisDecisionDump:recording recording.webgpu-submit decision:candidate:analysis.fill_rect.42:native.fill_rect.solid",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-16-recording-task-list.txt": [
        "recording:GPUTaskList:recording recording.webgpu-submit task:render:task.render.42:pass.root.42:analysis.fill_rect.42:pre_materialization",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-17-recording-compatibility.txt": [
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit commandShapeVersion=1",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit dictionaryVersion=material.dictionary.none",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit runtimeRegistrySnapshot=runtime.registry.none",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit capabilityClass=first_slice.fill_rect.native=supported",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit targetFormatClass=rgba8unorm",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit resourceTopologyClass=pre_materialization.no_concrete_resources",
        "recording:GPURecordingCompatibilityKey:recording recording.webgpu-submit replayPolicy=one-shot",
    ],
    "diagnostic-webgpu-first-route-pm-evidence-18-recording-replay.txt": [
        "recording:GPURecordingReplayResult.Refused:recording recording.webgpu-submit replay.one_shot_recording",
    ],
}


def exported_text(lines: list[str]) -> str:
    return "\n".join(lines) + "\n"


def sha256(lines: list[str]) -> str:
    return "sha256:" + hashlib.sha256(exported_text(lines).encode("utf-8")).hexdigest()


def write_fixture_bundle(root: Path, artifact_lines: dict[str, list[str]] | None = None) -> Path:
    output_dir = root / "gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence"
    output_dir.mkdir(parents=True, exist_ok=True)
    artifacts = artifact_lines or ARTIFACT_LINES

    manifest_lines = [
        "validation.report.name=diagnostic-webgpu-first-route-pm-evidence",
        "validation.report.status=Passed",
        "validation.gate.name=first-route-promotion",
        "validation.gate.passed=true",
        "validation.gate.missingEvidence=none",
        "validation.bundle.scope=pm-evidence-only",
        f"validation.report.artifacts={len(artifacts)}",
        "validation.report.diagnostics=0",
        "validation.gate.diagnostics=0",
    ]
    for index, name in enumerate(artifacts, start=1):
        ordinal = f"{index:02d}"
        lines = artifacts[name]
        manifest_lines.extend(
            [
                f"artifact.{ordinal}.name={name}",
                f"artifact.{ordinal}.lines={len(lines)}",
                f"artifact.{ordinal}.sha256={sha256(lines)}",
            ]
        )
        (output_dir / name).write_text(exported_text(lines), encoding="utf-8")

    (output_dir / validator.MANIFEST_ARTIFACT).write_text(exported_text(manifest_lines), encoding="utf-8")
    return output_dir


class GpuRendererR6ExecutedPmEvidenceValidatorTest(unittest.TestCase):
    def test_validate_output_accepts_executed_diagnostic_bundle_without_product_claim(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            _, rows, summary = validator.validate_output(write_fixture_bundle(Path(temp)))

        self.assertEqual(18, len(rows))
        self.assertEqual("gpuRendererR6ExecutedFirstRoutePmEvidence", summary["key"])
        self.assertEqual("Passed", summary["status"])
        self.assertTrue(summary["promotionGatePassed"])
        self.assertTrue(summary["webGpuAdapterRequired"])
        self.assertFalse(summary["productRouteActivated"])
        self.assertFalse(summary["releaseBlocking"])
        self.assertEqual(0.0, summary["readinessDelta"])
        self.assertEqual("pm-evidence-only", summary["scope"])

    def test_validate_summary_payload_rejects_artifact_hash_inventory_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            _, rows, summary = validator.validate_output(write_fixture_bundle(Path(temp)))

        missing_hash = dict(summary)
        missing_hash["artifactHashes"] = dict(summary["artifactHashes"])
        missing_hash["artifactHashes"].pop(validator.EXPECTED_ARTIFACT_NAMES[0])

        extra_hash = dict(summary)
        extra_hash["artifactHashes"] = {
            **summary["artifactHashes"],
            "diagnostic-webgpu-first-route-pm-evidence-99-extra.txt": "sha256:" + ("0" * 64),
        }

        non_string_hash = dict(summary)
        non_string_hash["artifactHashes"] = dict(summary["artifactHashes"])
        non_string_hash["artifactHashes"][validator.EXPECTED_ARTIFACT_NAMES[1]] = 7

        non_string_key = dict(summary)
        non_string_key["artifactHashes"] = dict(summary["artifactHashes"])
        non_string_key["artifactHashes"][7] = "sha256:" + ("0" * 64)

        wrong_hash = dict(summary)
        wrong_hash["artifactHashes"] = dict(summary["artifactHashes"])
        wrong_hash["artifactHashes"][validator.EXPECTED_ARTIFACT_NAMES[2]] = "sha256:" + ("f" * 64)

        cases = [
            ("missing", missing_hash),
            ("extra", extra_hash),
            ("non-string", non_string_hash),
            ("non-string-key", non_string_key),
            ("wrong", wrong_hash),
        ]
        for label, mutated_summary in cases:
            with self.subTest(label=label):
                with self.assertRaisesRegex(validator.ValidationError, "artifactHashes"):
                    validator.validate_summary_payload(mutated_summary, rows)

    def test_validate_output_rejects_raw_readback_or_product_claims(self) -> None:
        mutated = dict(ARTIFACT_LINES)
        mutated["diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"] = [
            ARTIFACT_LINES["diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"][0] + " readbackBytes=0000",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "forbidden raw or product claim"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_extra_native_route_positive_evidence_line(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-03-route.txt"
        mutated = dict(ARTIFACT_LINES)
        mutated[artifact_name] = [
            *ARTIFACT_LINES[artifact_name],
            "routing:GPURouteDecision.Native:recording recording.webgpu-submit route:native.fill_rect.solid.extra",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "exactly one"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_extra_materialized_resource_positive_evidence_line(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-08-resource-decision.txt"
        mutated = dict(ARTIFACT_LINES)
        mutated[artifact_name] = [
            *ARTIFACT_LINES[artifact_name],
            "resources:GPUResourceMaterializationDecision.Materialized:diagnostic-webgpu-first-route-pm-evidence duplicate",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "exactly one"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_extra_submitted_command_positive_evidence_line(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-09-submission.txt"
        mutated = dict(ARTIFACT_LINES)
        mutated[artifact_name] = [
            *ARTIFACT_LINES[artifact_name],
            "execution:GPUCommandSubmission.Submitted:diagnostic-webgpu-first-route-pm-evidence duplicate",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "exactly one"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_extra_completed_readback_positive_evidence_line(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"
        mutated = dict(ARTIFACT_LINES)
        mutated[artifact_name] = [
            *ARTIFACT_LINES[artifact_name],
            "execution:GPUReadbackResult.Completed:diagnostic-webgpu-first-route-pm-evidence duplicate",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "exactly one"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_support_claim_product_contamination(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-01-command.txt"
        cases = ["artifact", "manifest"]

        for case in cases:
            with self.subTest(case=case):
                with tempfile.TemporaryDirectory() as temp:
                    output_dir = write_fixture_bundle(Path(temp))
                    if case == "artifact":
                        mutated = dict(ARTIFACT_LINES)
                        mutated[artifact_name] = [
                            *ARTIFACT_LINES[artifact_name],
                            "supportClaim=true",
                        ]
                        output_dir = write_fixture_bundle(Path(temp), mutated)
                    else:
                        manifest_path = output_dir / validator.MANIFEST_ARTIFACT
                        manifest_text = manifest_path.read_text(encoding="utf-8")
                        manifest_path.write_text(manifest_text + "supportClaim=true\n", encoding="utf-8")

                    with self.assertRaisesRegex(validator.ValidationError, "forbidden raw or product claim"):
                        validator.validate_output(output_dir)

    def test_validate_output_rejects_spaced_json_or_adapter_requirement_claims(self) -> None:
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-01-command.txt"
        cases = [
            ("productRouteActivated = true", "forbidden raw or product claim"),
            ('"releaseBlocking": true', "forbidden raw or product claim"),
            ("nativeKadreCiRequired=true", "forbidden raw or product claim"),
            ("readinessDelta: 1", "forbidden raw or product claim"),
            ("support_claim=true", "forbidden raw or product claim"),
            ("supported: true", "forbidden raw or product claim"),
            ("webGpuAdapterRequired=false", "adapter requirement claim"),
        ]

        for claim, expected_message in cases:
            with self.subTest(claim=claim):
                mutated = dict(ARTIFACT_LINES)
                mutated[artifact_name] = [
                    *ARTIFACT_LINES[artifact_name],
                    claim,
                ]

                with tempfile.TemporaryDirectory() as temp:
                    with self.assertRaisesRegex(validator.ValidationError, expected_message):
                        validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_duplicate_executed_manifest_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            manifest_path = output_dir / validator.MANIFEST_ARTIFACT
            manifest_lines = manifest_path.read_text(encoding="utf-8").splitlines()
            updated_lines = []
            for line in manifest_lines:
                if line == "validation.gate.passed=true":
                    updated_lines.append("validation.gate.passed=false")
                updated_lines.append(line)
            manifest_path.write_text(exported_text(updated_lines), encoding="utf-8")

            with self.assertRaisesRegex(validator.ValidationError, "duplicate manifest key: validation.gate.passed"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_unexpected_executed_manifest_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            manifest_path = output_dir / validator.MANIFEST_ARTIFACT
            manifest_text = manifest_path.read_text(encoding="utf-8")
            manifest_path.write_text(
                manifest_text
                + "\n".join(
                    [
                        "releaseStatus=ready-for-product-route",
                        "artifact.19.name=extra-positive-claim.txt",
                        "gateDiagnostic.01=unexpected gate diagnostic",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "unexpected executed manifest fields"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_shadow_result_dump_contamination(self) -> None:
        mutated = dict(ARTIFACT_LINES)
        artifact_name = "diagnostic-webgpu-first-route-pm-evidence-01-command.txt"
        mutated[artifact_name] = [
            *ARTIFACT_LINES[artifact_name],
            (
                "gpuRendererShadow v=1 status=native route=native.fill_rect.solid diagnostic=none "
                "cpuFallback=false command=source=GpuRendererShadowAdapter:legacy.fillRect.shadow"
            ),
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "shadow"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_appended_skip_or_no_adapter_markers(self) -> None:
        skip_markers = [
            "skipped=true",
            "skipReason=no-webgpu-adapter",
            "No WebGPU adapter",
            "No webgpu adapter",
            "adapter-skipped",
            "no-webgpu-adapter",
            "webgpu-adapter-unavailable",
        ]

        for marker in skip_markers:
            with self.subTest(marker=marker):
                mutated = dict(ARTIFACT_LINES)
                mutated["diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"] = [
                    ARTIFACT_LINES["diagnostic-webgpu-first-route-pm-evidence-10-readback.txt"][0],
                    f"note={marker}",
                ]

                with tempfile.TemporaryDirectory() as temp:
                    with self.assertRaisesRegex(validator.ValidationError, "skip marker"):
                        validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_stale_existing_summary_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            _, _, summary = validator.validate_output(output_dir)
            summary["readinessDelta"] = False
            summary["supportClaim"] = True
            (output_dir / "diagnostic-webgpu-first-route-pm-evidence-summary.json").write_text(
                json.dumps(summary, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "summary sidecar"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_duplicate_summary_sidecar_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = write_fixture_bundle(Path(temp))
            _, _, summary = validator.validate_output(output_dir)
            sidecar_text = json.dumps(summary, indent=2, sort_keys=True)
            self.assertIn('  "productRouteActivated": false,', sidecar_text)
            sidecar_text = sidecar_text.replace(
                '  "productRouteActivated": false,',
                '  "productRouteActivated": true,\n  "productRouteActivated": false,',
                1,
            )
            (output_dir / "diagnostic-webgpu-first-route-pm-evidence-summary.json").write_text(
                sidecar_text + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "duplicate JSON key: productRouteActivated"):
                validator.validate_output(output_dir)

    def test_validate_output_rejects_non_parser_backed_wgsl_reflection(self) -> None:
        mutated = dict(ARTIFACT_LINES)
        mutated["diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt"] = [
            ARTIFACT_LINES["diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt"][0].replace(
                "parserState=parser-backed",
                "parserState=fixture-declared",
            ),
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "parser-backed"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_raw_wgsl_with_unexpected_entry_point_name(self) -> None:
        mutated = dict(ARTIFACT_LINES)
        mutated["diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt"] = [
            ARTIFACT_LINES["diagnostic-webgpu-first-route-pm-evidence-05-wgsl.txt"][0],
            "@fragment fn vertex_main() -> @location(0) vec4<f32> { return vec4<f32>(); }",
        ]

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "raw WGSL source"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_validate_output_rejects_missing_unsupported_route_family_refusals(self) -> None:
        mutated = {
            name: lines
            for name, lines in ARTIFACT_LINES.items()
            if name != "diagnostic-webgpu-first-route-pm-evidence-14-unsupported-route-refusals.txt"
        }

        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaisesRegex(validator.ValidationError, "unsupported-route-refusals"):
                validator.validate_output(write_fixture_bundle(Path(temp), mutated))

    def test_gradle_wiring_keeps_executed_bundle_out_of_root_pipeline_pm_bundle(self) -> None:
        build_gradle = (PROJECT_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        pipeline_task_start = build_gradle.index('tasks.register("pipelinePmBundle")')
        pipeline_task_end = build_gradle.index("\ntasks.", pipeline_task_start + 1)
        pipeline_task = build_gradle[pipeline_task_start:pipeline_task_end]

        self.assertNotIn("gpuRendererR6ExecutedFirstRoutePmEvidenceBundle", pipeline_task)
        self.assertNotIn("validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle", pipeline_task)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_get_by_name_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.getByName("pipelinePmBundle").dependsOn(',
                        '    ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle",',
                        ")",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_indirect_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val adapterBackedR6Evidence = ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(adapterBackedR6Evidence)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_typed_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val adapterBackedR6Evidence: String = ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        'val rootPmBundle: TaskProvider<Task> = tasks.named<Task>("pipelinePmBundle")',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "rootPmBundle.configure {",
                        "    dependsOn(adapterBackedR6Evidence)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_multiline_value_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        "val adapterBackedR6Evidence =",
                        '    ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(adapterBackedR6Evidence)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_line_comment_value_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        "val adapterBackedR6Evidence = // adapter-backed lane",
                        '    ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(adapterBackedR6Evidence)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_block_comment_value_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        "val adapterBackedR6Evidence = /* adapter-backed lane */",
                        '    ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(adapterBackedR6Evidence)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_transitive_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val executedEvidence = ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        "val pmDependencyAlias = executedEvidence",
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(pmDependencyAlias)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_line_comment_task_alias_adapter_backed_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        "val rootPmBundle = // task alias comment",
                        '    tasks.named<Task>("pipelinePmBundle")',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "rootPmBundle.configure {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_comment_separated_task_alias_configure(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val rootPmBundle = tasks.named<Task>("pipelinePmBundle")',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "rootPmBundle",
                        "    // comment before chained configure",
                        "    .configure {",
                        '        dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "    }",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_configure_call_task_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val rootPmBundle = tasks.named<Task>("pipelinePmBundle")',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "configure(rootPmBundle) {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_project_tasks_named_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'project.tasks.named("pipelinePmBundle") {',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_this_tasks_named_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'this.tasks.named("pipelinePmBundle") {',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_project_configure_task_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val rootPmBundle = tasks.named<Task>("pipelinePmBundle")',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "project.configure(rootPmBundle) {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_multiline_named_pipeline_task_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "tasks.named<Task>(",
                        '    "pipelinePmBundle"',
                        ") {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_multiline_named_pipeline_task_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        "val rootPmBundle = tasks.named<Task>(",
                        '    "pipelinePmBundle"',
                        ")",
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "rootPmBundle.configure {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_transitive_task_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val rootPmBundle = tasks.named<Task>("pipelinePmBundle")',
                        "val rootPmAlias = rootPmBundle",
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "rootPmAlias.configure {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_task_name_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val pipelineTaskName = "pipelinePmBundle"',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "tasks.named<Task>(pipelineTaskName) {",
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_transitive_task_name_alias_wiring(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val pipelineTaskName = "pipelinePmBundle"',
                        "val pmTaskName = pipelineTaskName",
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "tasks.getByName(pmTaskName).finalizedBy(",
                        '    ":gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        ")",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_ignores_value_alias_comment_only_executed_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'val harmlessDependency = "validateMepRcRuntime" // :gpu-renderer:gpuRendererR6FirstRoutePmEvidenceBundle',
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    dependsOn(harmlessDependency)",
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_ignores_comment_and_raw_string_braces(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.named("pipelinePmBundle") {',
                        "    // } comment brace must not close this task block",
                        '    val diagnostic = """ } raw-string brace must not close this task block """',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_ignores_commented_pipeline_wiring_example(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        "/*",
                        'tasks.named("pipelinePmBundle") {',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        "}",
                        "*/",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_adapter_backed_finalizer(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.getByName("pipelinePmBundle").finalizedBy(',
                        '    ":gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle",',
                        ")",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_comment_separated_chained_adapter_backed_finalizer(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.getByName("pipelinePmBundle")',
                        "    // comment before the chained call",
                        '    .finalizedBy(":gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_rejects_multiline_chained_adapter_backed_finalizer(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.getByName("pipelinePmBundle")',
                        '    .finalizedBy(":gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(validator.ValidationError, "pipelinePmBundle"):
                validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_allows_separate_adapter_backed_boundary_task(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.register<Exec>("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary") {',
                        '    dependsOn("pipelinePmBundle")',
                        '    dependsOn(":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        '    commandLine("python3", "scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_allows_opt_in_task_combined_depends_on(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.register<Exec>("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary") {',
                        '    dependsOn(tasks.getByName("pipelinePmBundle"), ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")',
                        '    commandLine("python3", "scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            validator.validate_root_pipeline_isolation(root)

    def test_root_pipeline_isolation_allows_opt_in_task_multiline_combined_depends_on(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "build.gradle.kts").write_text(
                "\n".join(
                    [
                        'tasks.register("pipelinePmBundle") {',
                        "}",
                        'tasks.register<Exec>("validateGpuRendererR6AdapterBackedPromotionReadinessBoundary") {',
                        "    dependsOn(",
                        '        tasks.getByName("pipelinePmBundle"), ":gpu-raster:validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle"',
                        "    )",
                        '    commandLine("python3", "scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py")',
                        "}",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )

            validator.validate_root_pipeline_isolation(root)

    def test_gpu_raster_wiring_exposes_only_opt_in_executed_validation_task(self) -> None:
        build_gradle = (PROJECT_ROOT / "gpu-raster/build.gradle.kts").read_text(encoding="utf-8")
        task_start = build_gradle.index(
            'tasks.register<Exec>("validateGpuRendererR6ExecutedFirstRoutePmEvidenceBundle")'
        )
        task_end = build_gradle.index("\n// MIGRATION_PLAN_GPU_WEBGPU", task_start + 1)
        task_body = build_gradle[task_start:task_end]

        self.assertIn('dependsOn("gpuRendererR6ExecutedFirstRoutePmEvidenceBundle")', task_body)
        self.assertIn("validate_gpu_renderer_r6_executed_pm_evidence_bundle.py", task_body)
        self.assertIn("--write-summary", task_body)
        self.assertNotIn("pipelinePmBundle", task_body)


if __name__ == "__main__":
    unittest.main()
