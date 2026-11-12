#!/usr/bin/env bash
# Install FFmpeg and the DejaVu font used by render_video.py on an Ubuntu Actions runner.
# When the first Ubuntu mirror of a hosted runner stops answering, apt waits out its timeout on
# every single index and package before it falls back, which turns a one-minute installation
# into more than 30 minutes without any error (actions/runner-images#14594). Every apt call is
# therefore time-limited, a failed attempt continues with the next mirror of the runner's list,
# and the packages are downloaded before dpkg runs so that a retry never interrupts an
# installation.
set -uo pipefail
packages=(ffmpeg fonts-dejavu-core)
mirrors=/etc/apt/apt-mirrors.txt  # hosted runners: one "<url> priority:<n>" line per mirror
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

skip_first_mirror() {  # the next attempt starts with the following mirror, if the runner lists one
  [[ -f "$mirrors" ]] && (( $(grep -c . "$mirrors") > 1 )) || return 0
  echo "::warning::Skipping the apt mirror $(awk 'NR == 1 { print $1 }' "$mirrors")"
  sudo sed -i 1d "$mirrors"
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
  run_apt 60 update
  status=$?
  # A timeout means that the mirror stalls. Any other update error may come from an unrelated
  # source, so the download is still tried.
  if (( status != 124 && status != 137 )) && run_apt 150 install -y --download-only "${packages[@]}"; then
    break
  fi
  (( attempt < 3 )) || fail "Could not download ${packages[*]} in 3 attempts"
  skip_first_mirror
done
run_apt 360 install -y --no-download "${packages[@]}" || fail "Could not install ${packages[*]}"
test -f /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf || fail 'DejaVuSans.ttf is missing after the installation'
ffmpeg -hide_banner -version | sed -n 1p
