# Skia Pack Context

This document provides context about the Skia Pack project by combining information from the root-level markdown files.

## Project Overview

The Skia Pack project is dedicated to building Skia binaries for use in [Skiko](https://github.com/JetBrains/skiko). It provides automated builds and prebuilt binaries of the Skia graphics library.

## Key Files

### README.md

The main README file contains essential information about:

- **Prebuilt binaries**: Available in [releases](https://github.com/JetBrains/skia-pack/releases)
- **Building process**: Instructions for building new versions of Skia
- **Local building**: Commands for building Skia locally, including debug builds
- **Windows-specific requirements**: Clang-cl installation for Windows builds

### SKIA_DEVICES.md

This document provides a comprehensive overview of Skia's device architecture:

- **Device hierarchy**: The inheritance structure of Skia devices
- **Core device types**: 
  - `SkBitmapDevice` for CPU/raster rendering
  - GPU devices (Graphite and Ganesha)
  - Vector devices (SVG, PDF, XPS)
- **Device vs Surface**: Key differences and relationships
- **Rendering pipeline**: From high-level API to actual pixels
- **Performance considerations**: For different device types

### SKIA_PATH_DRAWING.md

This document explains Skia's path drawing architecture:

- **Call stack**: From public API to pixel rendering
- **Key components**: 
  - `SkBasicEdgeBuilder` for edge conversion
  - Edge walking algorithms
  - Blitting for pixel rendering
- **Anti-aliasing implementation**: Coverage calculation and sub-pixel precision
- **Performance considerations**: Tiling, clipping, and path simplification

## Building Process

The typical workflow for building Skia involves:

1. Finding the release commit in the Skia repository
2. Rebasing the `skiko` branch in the Skia fork
3. Updating the version in the build workflow
4. Running the build scripts:
   - `script/checkout.py` - Checkout the specific Skia version
   - `script/build.py` - Build Skia
   - `script/archive.py` - Archive the built binaries

## Technical Architecture

Skia's architecture is complex and sophisticated:

- **Device-based rendering**: Different devices handle different output types
- **Path rendering pipeline**: Multi-stage process from path to pixels
- **GPU acceleration**: Both Graphite (modern) and Ganesha (legacy) backends
- **Vector output**: Support for SVG, PDF, and XPS generation

## Usage

This project is primarily used by the Skiko team to provide prebuilt Skia binaries for their multiplatform graphics library. The binaries are consumed by JetBrains products that use Skiko for graphics rendering.

## Additional Resources

- [Skia repository](https://github.com/google/skia)
- [JetBrains Skia fork](https://github.com/JetBrains/skia)
- [Skiko repository](https://github.com/JetBrains/skiko)
