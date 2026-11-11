#!/bin/zsh
# fetch.sh < "id url" lines : saves out/<id>.mp3 (skips ones already there)
cd "$(dirname $0)"; mkdir -p out
while read -r id url; do
  [ -z "$id" ] && continue
  [ -f out/$id.mp3 ] && continue
  curl -sfL "$url" -o out/$id.mp3 && echo "$id ok" || echo "$id FAILED"
done
