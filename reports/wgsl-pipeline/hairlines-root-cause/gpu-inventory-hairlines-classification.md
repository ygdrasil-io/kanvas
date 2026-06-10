# KAN-035 GPU Inventory Hairlines Classification

Source command:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest
```

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 1 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 0 |
| `adapter-skip` | 0 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

| Test | Category | Reason |
|---|---|---|
| `org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest#HairlinesGM matches reference on raster and GPU backends()` | `expected-unsupported-diagnostic` | `coverage.stroke-cap-join-visual-parity-below-threshold` |

Catalog entry: `coverage.stroke-cap-join-visual-parity-below-threshold` -> `KAN-035/KAN-036 (stroke cap/join parity evidence before support promotion)`.

Policy: Expected unsupported while stroke cap/join evidence remains below the 99.95 support threshold.
