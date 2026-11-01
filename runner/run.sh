#!/bin/sh

# One judging job. Stdin: the tar's size in bytes on one line, then the tar. Stdout: the protocol
# parsed by SandboxOutput. The size prefix means the end of the job is known without the sender
# half-closing the stream, which not every proxy passes through. In single-container mode
# judged.sh runs this once per connection, so every job gets its own private directory.

TIME_LIMIT="${TIME_LIMIT:-2}"
MAIN_CLASS="${MAIN_CLASS:-Main}"
COMPILE_LIMIT=20
MAX_ARCHIVE_BYTES=4194304

umask 077
# A job killed from outside can't run its trap; sweep anything that old.
find /tmp -maxdepth 1 -name 'job.*' -mmin +10 -exec rm -rf {} + 2> /dev/null
JOB=$(mktemp -d /tmp/job.XXXXXXXX) || { echo "SANDBOX_ERROR"; exit 0; }
trap 'rm -rf "$JOB"' EXIT
WORK="$JOB/work"
CLASSES="$JOB/classes"
mkdir -p "$WORK" "$CLASSES"

read -r LENGTH
case "$LENGTH" in
  ''|*[!0-9]*) echo "SANDBOX_ERROR"; exit 0 ;;
esac
if [ "$LENGTH" -gt "$MAX_ARCHIVE_BYTES" ]; then
  echo "SANDBOX_ERROR"
  exit 0
fi

if ! head -c "$LENGTH" | tar -x -C "$WORK" 2> "$JOB/tar.txt"; then
  echo "SANDBOX_ERROR"
  exit 0
fi

[ -f "$WORK/main" ] && MAIN_CLASS=$(cat "$WORK/main")
[ -f "$WORK/limit" ] && TIME_LIMIT=$(cat "$WORK/limit")
case "$MAIN_CLASS" in
  ''|*[!A-Za-z0-9_\$]*) echo "SANDBOX_ERROR"; exit 0 ;;
esac
case "$TIME_LIMIT" in
  ''|*[!0-9]*) echo "SANDBOX_ERROR"; exit 0 ;;
esac

# -proc:none: no annotation processors, so nothing of the submission runs at compile time.
if ! timeout -s KILL "$COMPILE_LIMIT" javac -J-Xmx192m -J-XX:-UsePerfData -J-XX:TieredStopAtLevel=1 -J-Xshare:auto \
    -proc:none -encoding UTF-8 -d "$CLASSES" "$WORK"/src/*.java 2> "$JOB/compile.txt"; then
  echo "COMPILE_ERROR"
  head -c 8192 "$JOB/compile.txt" | base64 | tr -d '\n'
  echo
  exit 0
fi

echo "COMPILED"

set -- "$WORK"/tests/*.in
COUNT=$#
# runner.policy grants the submission's classes nothing: no files, network, processes or native code.
timeout -s KILL $((COUNT * TIME_LIMIT + 5)) \
  java -Xmx160m -Xss16m -XX:MaxDirectMemorySize=16m -XX:+UseSerialGC -XX:-UsePerfData -XX:TieredStopAtLevel=1 \
    -Xshare:auto -Djava.security.manager=allow -Djava.security.policy=/opt/runner/runner.policy \
    -cp /opt/runner TestRunner "$MAIN_CLASS" "$TIME_LIMIT" "$WORK/tests" "$CLASSES" 2> /dev/null
