# CharLS RGB near-lossless fixtures

Each `*.ppm.base64` file is the Base64 transport representation of the exact
binary P6 PPM named by its stem.  The `*.jls.base64` files are raw JPEG-LS
codestreams, likewise transported as Base64 for text-safe source control.

Both fixture pairs were generated and decoded with CharLS 3.0.0
(`c0bae6496fa5d787fbb4698debd1e5decb40cf3a`), BSD-3-Clause, using:

```text
charls-cli encode --interleave-mode 1 --near-lossless 1 source.ppm output.jls
charls-cli decode output.jls reconstruction.ppm
```

They are 8-bit RGB, component IDs 1/2/3, unit sampling, `ILV=1`, with no
colour transform or preset coding parameter marker. `line` and `line-run` use
`NEAR=1`; `line-run-near127` covers the 8-bit normative upper bound. `line` exercises
regular coding; `line-run` has constant R/B lines and a varying G line, so it
exercises line-interleaved run and run-interruption state.

| Fixture | Source PPM SHA-256 | JLS SHA-256 | CharLS reconstruction PPM SHA-256 |
|---|---|---|---|
| `line` | `eb933e7350eb37c385edb25d2949012bf77640f4d4f1ec36c748f58b7a0314e4` | `f64fcc2255c1e3ec760b8649a88ecb753142364584cdc83bc31d8853c4ed6f1d` | `dcc562c99e14984918324d670aafd4a7a8fe2abd7db5632941a1d88af58acba0` |
| `line-run` | `1728363fcc8cebc8abf6edbeb3ef734b00cf3f93fab77afda8d1cc4dbdfafb59` | `1a1324c21c96f2d5ca00bbb87756f7166679fc17df6f47cda7fe5b871a6c681c` | `c652010198a2fc09a0a66da980b42bef211d8bc326b8d3e4823a56a7de538d72` |
| `line-run-near127` | `1728363fcc8cebc8abf6edbeb3ef734b00cf3f93fab77afda8d1cc4dbdfafb59` | `50aeb4c4cd46b91a0b80fc7e2751b2f282083dc8e5c6ede75d329196d4b915b0` | `568c3a328bed5fe0be2936c6bc986677e60c30494ab983b201020c87de3c1936` |

CharLS is development evidence only. The Kanvas runtime does not invoke a
native, JNI, AWT, ImageIO, or external JPEG-LS implementation.
