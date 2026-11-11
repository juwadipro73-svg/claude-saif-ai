#!/usr/bin/env bash
# Install FFmpeg and the DejaVu font used by render_video.py on an Ubuntu Actions runner.
# apt on hosted runners can stall for tens of minutes on a degraded Ubuntu mirror node without
# ever failing (actions/runner-images#14594). Every apt call is therefore time-limited, the
# network phases are retried on a fresh connection, and packages are downloaded before dpkg
# runs so that a retry never interrupts an installation.
set -uo pipefail
packages=(ffmpeg fonts-dejavu-core)
out="$(mktemp)"
trap 'rm -f "$out"' EXIT

run_apt() {  # run_apt <seconds> <apt-get arguments...>
  local limit="$1" started=$SECONDS status=0
  shift
  # timeout runs under sudo: an unprivileged timeout cannot signal root's apt-get.
  sudo timeout --kill-after=15 "$limit" apt-get "$@" < /dev/null > "$out" 2>&1 || status=$?
  echo "::group::apt-get $* (exit $status, $((SECONDS - started))s)"
  cat "$out"
  echo '::endgroup::'
  if (( status == 124 || status == 137 )); then
    echo "::warning::apt-get $1 gave no result within ${limit}s"
  elif (( status != 0 )); then
    echo "::warning::apt-get $1 failed with exit $status"
  fi
  return "$status"
}

fail() {  # fail <message>: one error annotation that ends with the last apt output
  local last
  last="$(tail -n 20 "$out")"
  last="${last//'%'/%25}"
  last="${last//$'\r'/}"
  last="${last//$'\n'/%0A}"
  echo "::error::$1%0A${last:0:3000}"
  exit 1
}

for attempt in 1 2 3; do
  # A failed update is not fatal by itself: the lists of an unrelated source may be out of date.
  run_apt 60 update
  run_apt 150 install -y --download-only "${packages[@]}" && break
  (( attempt < 3 )) || fail "Could not download ${packages[*]} in 3 attempts"
  sleep 5
done
run_apt 360 install -y --no-download "${packages[@]}" || fail "Could not install ${packages[*]}"
test -f /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf || fail 'DejaVuSans.ttf is missing after the installation'
ffmpeg -hide_banner -version | sed -n 1p
