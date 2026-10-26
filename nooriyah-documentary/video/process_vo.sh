#!/usr/bin/env bash
# Trim silence, tighten pace and add a broadcast voice chain to every raw TTS line.
# Usage: process_vo.sh <raw_dir> <out_dir>
set -euo pipefail
mkdir -p "$2"
for f in "$1"/*.wav; do
  ffmpeg -v error -y -i "$f" -af "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,\
areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,atempo=1.1,\
highpass=f=70,equalizer=f=180:t=q:w=1:g=2.5,equalizer=f=3500:t=q:w=1.2:g=2,\
acompressor=threshold=-20dB:ratio=3:attack=5:release=80,aresample=48000" -ac 1 "$2/$(basename "$f")"
done
