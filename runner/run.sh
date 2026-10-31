#!/bin/sh

TIME_LIMIT="${TIME_LIMIT:-2}"
MAIN_CLASS="${MAIN_CLASS:-Main}"
WORK=/tmp/work
CLASSES=/tmp/classes
mkdir -p "$WORK" "$CLASSES"

if ! tar -x -C "$WORK" 2> /tmp/tar.txt; then
  echo "SANDBOX_ERROR"
  exit 0
fi

if ! javac -J-XX:-UsePerfData -J-XX:TieredStopAtLevel=1 -J-Xshare:auto -encoding UTF-8 -d "$CLASSES" "$WORK"/src/*.java 2> /tmp/compile.txt; then
  echo "COMPILE_ERROR"
  head -c 8192 /tmp/compile.txt | base64 | tr -d '\n'
  echo
  exit 0
fi

echo "COMPILED"

set -- "$WORK"/tests/*.in
COUNT=$#
timeout -s KILL $((COUNT * TIME_LIMIT + 5)) \
  java -Xmx160m -Xss16m -XX:+UseSerialGC -XX:-UsePerfData -XX:TieredStopAtLevel=1 -Xshare:auto \
    -Djava.security.manager=allow \
    -cp /opt/runner TestRunner "$MAIN_CLASS" "$TIME_LIMIT" "$WORK/tests" "$CLASSES" 2> /dev/null
