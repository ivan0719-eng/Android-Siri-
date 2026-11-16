# Bundled wake-word dependencies

Version 0.2 removes Picovoice/Porcupine entirely.

- **sherpa-onnx 2.0.1 Android AAR**: https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar
  - Source: https://github.com/k2-fsa/sherpa-onnx/tree/v2.0.1
  - Apache-2.0; see `sherpa-onnx-LICENSE`.
  - The APK includes only the ARM64 libraries for the target Samsung phone.
- **English keyword model**: `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`
  - https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2
  - Model documentation: https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
  - Bundled encoder, decoder and joiner are the int8 variants, renamed to `encoder.onnx`, `decoder.onnx`, `joiner.onnx`. Tokens are unchanged. The custom keyword file is generated from `JARVIS` with the archive's `bpe.model` and SentencePiece.
- **ONNX Runtime**, bundled by the upstream Android AAR: MIT; see `onnxruntime-LICENSE`.

Runtime and model files are included locally so installation and wake detection require no vendor account, key or runtime download. Android's separately configured command-recognition/TTS providers may use network services.

The files in `checksums.sha256` record the downloaded engine and packaged model assets. Licenses and these notices are also bundled in the APK assets.
