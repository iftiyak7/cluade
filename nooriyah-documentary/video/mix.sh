#!/usr/bin/env bash
# Join rendered video segments and mix voiceover + ducked music into the final MP4.
# Usage: mix.sh <out.mp4> <vo.wav> <music.wav> <segment.mp4>...
set -euo pipefail
out=$1 vo=$2 music=$3
shift 3
list=$(mktemp)
for seg in "$@"; do echo "file '$(realpath "$seg")'" >> "$list"; done

ffmpeg -v error -y -f concat -safe 0 -i "$list" -i "$vo" -i "$music" \
  -filter_complex "\
[1:a]aformat=sample_rates=48000:channel_layouts=stereo,asplit=2[vo][key];\
[2:a]volume=0.6[m];\
[m][key]sidechaincompress=threshold=0.025:ratio=5:attack=25:release=450:makeup=1[duck];\
[vo][duck]amix=inputs=2:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[a]" \
  -map 0:v -map "[a]" -c:v libx264 -preset slow -crf 21 -pix_fmt yuv420p -profile:v high -c:a aac -b:a 192k -movflags +faststart -shortest "$out"
rm -f "$list"
