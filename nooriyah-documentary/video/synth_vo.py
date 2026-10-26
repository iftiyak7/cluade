"""Generate one WAV per script line with the Coqui Bangla male VITS model.

Usage: python3 synth_vo.py <model_dir> <out_dir>
<model_dir> is the unzipped tts_models--bn--custom--vits_male release
(github.com/coqui-ai/TTS/releases/download/v0.13.3_models/tts_models--bn--custom--vits_male.zip).
"""
import functools
import json
import sys
from pathlib import Path

import torch

# The checkpoint holds only tensors; force the safe loader so no pickled code can run.
_orig_load = torch.load
torch.load = functools.partial(lambda *a, **k: _orig_load(*a, **{**k, "weights_only": True}))
from TTS.utils.synthesizer import Synthesizer  # noqa: E402

model, out = Path(sys.argv[1]), Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
synth = Synthesizer(tts_checkpoint=str(model / "model_file.pth"), tts_config_path=str(model / "config.json"))
for scene in json.loads((Path(__file__).parent / "script.json").read_text()):
    for i, line in enumerate(scene["lines"]):
        synth.save_wav(synth.tts(line.replace(" / ", " ")), str(out / f"{scene['id']}_{i}.wav"))
        print(scene["id"], i, flush=True)
