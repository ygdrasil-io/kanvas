You are implementing Picture serialization and GPU expansion in Kanvas.

Working directory: /Users/chaos/.local/share/opencode/worktree/b0ac68aba2977c8e330962597a21babf616d6567/cosmic-engine

### CONTEXT

Picture.kt has stubs for toByteArray() and fromByteArray(). These need real implementation. Also, the GPU mapper needs DrawPicture expansion (handled by Prompt 1, but verify it works).

Read: kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt

### TASKS

**1. toByteArray() implementation**

Serialize the Picture to a compact binary format:
- Magic bytes: `[0x4B, 0x50, 0x49, 0x43]` ("KPIC")
- Version: Int (1)
- cullRect: 4× Float (left, top, right, bottom)
- opCount: Int
- For each DisplayOp: type discriminator (Byte) + op-specific fields

Use kotlinx.serialization or manual binary encoding. For Images embedded in DrawImage/DrawImageNine/DrawImageLattice, encode the image pixels as PNG bytes using the existing `toPng()` infrastructure.

Type discriminators:
```
0 = DrawRect, 1 = DrawRRect, 2 = DrawDRRect, 3 = DrawPath, 4 = DrawPoint,
5 = DrawPoints, 6 = DrawImage, 7 = DrawImageNine, 8 = DrawImageLattice,
9 = DrawText, 10 = DrawPicture (recursive), 11 = DrawVertices, 12 = DrawAtlas,
13 = DrawColor, 14 = Clear, 15 = SetTransform, 16 = SetClip,
17 = BeginLayer, 18 = EndLayer, 19 = Annotation
```

**2. fromByteArray() implementation**

Reverse of toByteArray. Validate magic bytes, read version, reconstruct cullRect, read ops one by one. Return null if data is invalid.

**3. Tests**

In `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`, add:

```kotlin
@Test fun `serialize and deserialize roundtrip`() {
    val recorder = PictureRecorder()
    val canvas = recorder.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
    canvas.drawRect(Rect.fromLTRB(10f, 10f, 50f, 50f), Paint.fill(Color.RED))
    canvas.drawCircle(30f, 30f, 15f, Paint.fill(Color.BLUE))
    val original = recorder.finishRecordingAsPicture()

    val bytes = original.toByteArray()
    assertTrue(bytes.isNotEmpty())

    val restored = Picture.fromByteArray(bytes)
    assertNotNull(restored)
    assertEquals(original.cullRect, restored!!.cullRect)
    assertEquals(original.approximateOpCount(), restored.approximateOpCount())
}

@Test fun `fromByteArray returns null for invalid data`() {
    assertNull(Picture.fromByteArray(byteArrayOf(0, 1, 2, 3)))
}

@Test fun `fromByteArray returns null for empty data`() {
    assertNull(Picture.fromByteArray(ByteArray(0)))
}
```

### VERIFICATION

```bash
./gradlew :kanvas:test --tests "org.graphiks.kanvas.picture.PictureTest" 2>&1 | tail -15
```

Commit: `git commit -m "feat(kanvas): Picture binary serialization (toByteArray/fromByteArray)"`
