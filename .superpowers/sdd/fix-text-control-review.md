# Correction P2 tEXt controls

## Scope

Correction du finding P2 de `review-rerun-final.md` concernant le repertoire
de caracteres accepte par `PngEncoder.textChunk`. Les regles du keyword n'ont
pas ete modifiees.

## Root cause

L'encodeur validait le texte comme Latin-1 puis refusait uniquement NUL. Le
parser `PngMetadata.latin1Text` accepte uniquement LF, `0x20..0x7E` et
`0xA1..0xFF`. L'encodeur pouvait donc produire un PNG que le parser Kanvas
classait ensuite `Refused`.

## TDD

RED:

```text
rtk ./gradlew :codec:png:test \
  --tests 'org.graphiks.kanvas.codec.png.PngEncoderTest.tEXt rejects TAB and C1 controls without output' \
  --rerun-tasks --no-daemon
```

Resultat attendu observe: `1 test completed, 1 failed`; l'assertion de refus
echouait a `PngEncoderTest.kt:313` parce que l'encodage de TAB reussissait.

GREEN:

- TAB (`0x09`) et C1 (`0x85`) sont refuses avant toute ecriture.
- LF, `0xA1` et `0xFF` conservent leurs octets Latin-1 exacts.
- Le PNG accepte est rouvert par `PngDocument` et son `tEXt` est `Resolved`, ce
  qui valide le meme repertoire cote encodeur et parser.

## Verification

```text
rtk ./gradlew :codec:png:test \
  --tests 'org.graphiks.kanvas.codec.png.PngEncoderTest' \
  --rerun-tasks --no-daemon
```

Resultat: `BUILD SUCCESSFUL`; 21 tests encodeur, zero echec/error/skip.

```text
rtk ./gradlew :codec:png:test --rerun-tasks --no-daemon
```

Resultat: `BUILD SUCCESSFUL`; 150 tests PNG, zero echec/error/skip
(`PngCodecTest` 63, `PngContainerParserTest` 24, `PngDocumentTest` 42,
`PngEncoderTest` 21).

```text
rtk git diff --check
```

Resultat: code de sortie 0.
