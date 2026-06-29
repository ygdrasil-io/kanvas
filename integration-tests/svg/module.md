# Integration Tests: SVG

Module containing SVG test files for rendering and performance testing in Kanvas.

## Structure
- `by-render-family/`: SVG files grouped by render family (gradients, transparencies, etc.)
- `performance/`: Large SVG files (>500KB) for performance testing

## Usage
Access SVG files in tests via `ClassLoader.getResource()`:
```kotlin
val svg = this::class.java.getResource("/by-render-family/gradients/gradient-1.svg")?.readText()
```
