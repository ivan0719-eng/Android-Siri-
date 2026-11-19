"""Native model smoke test; run with sherpa-onnx==2.0.1 and numpy installed.

Usage: python scripts/verify_wake_model.py positive-jarvis.wav negative-speech.wav
Audio must be mono 16-bit PCM at 16 kHz. This validates model inference, not
Android microphone behavior or real-world false-positive rates.
"""
import sys
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx

assets = Path(__file__).resolve().parents[1] / "app/src/main/assets/wake"
kws = sherpa_onnx.KeywordSpotter(
    tokens=str(assets / "tokens.txt"),
    encoder=str(assets / "encoder.onnx"),
    decoder=str(assets / "decoder.onnx"),
    joiner=str(assets / "joiner.onnx"),
    keywords_file=str(assets / "keywords.txt"),
    num_threads=1,
    keywords_score=1.5,
    keywords_threshold=float(sys.argv[3]) if len(sys.argv) > 3 else 0.18,
    num_trailing_blanks=int(sys.argv[4]) if len(sys.argv) > 4 else 1,
)


def detect(samples):
    stream = kws.create_stream()
    found = []
    samples = np.concatenate([samples, np.zeros(16000, dtype=np.float32)])
    for offset in range(0, len(samples), 1600):
        stream.accept_waveform(16000, samples[offset:offset + 1600])
        while kws.is_ready(stream):
            kws.decode_stream(stream)
            result = kws.get_result(stream)
            if result:
                found.append(result)
                kws.reset_stream(stream)
    return found


def read_audio(path):
    with wave.open(path) as audio:
        assert (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) == (1, 2, 16000)
        return np.frombuffer(audio.readframes(audio.getnframes()), dtype=np.int16).astype(np.float32) / 32768


assert not detect(np.zeros(48000, dtype=np.float32)), "Silence triggered wake detection"
assert "JARVIS" in detect(read_audio(sys.argv[1])), "Positive Jarvis sample was not detected"
assert not detect(read_audio(sys.argv[2])), "Unrelated speech triggered wake detection"
print("PASS: model loads; Jarvis detected; silence and unrelated speech rejected.")
