#!/bin/sh

# The judge service for single-container mode. Started as root, it drops to the unprivileged
# `judge` user with an empty environment (no database URL, no platform secrets) and serves one
# run.sh per TCP connection, which carries the same tar-in, protocol-out stream as Docker mode.
# The API connects from another user, so a submission can't read the API's memory or environment.

PORT="${JUDGE_PORT:-7070}"
BIND="${JUDGE_BIND:-127.0.0.1}"
JOBS="${JUDGE_MAX_JOBS:-2}"

exec su-exec judge env -i PATH=/opt/java/openjdk/bin:/usr/bin:/bin HOME=/tmp TIME_LIMIT=2 sh -c '
  # Caps for everything the judge user runs: threads and processes, file size, CPU seconds.
  ulimit -u 256
  ulimit -f 16384
  ulimit -t 120
  while true; do
    socat -t 120 TCP-LISTEN:'"$PORT"',bind='"$BIND"',fork,reuseaddr,max-children='"$JOBS"' EXEC:/usr/local/bin/run.sh
    sleep 1
  done
'
