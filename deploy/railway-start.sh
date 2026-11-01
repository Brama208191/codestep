#!/bin/sh
set -e

# Runs as root under tini, only to start each part as its own user:
#   judge - runs submitted code (judged.sh), with an empty environment
#   app   - the API, the only process that holds the database password
# Different users mean a submission can't read the API's environment or memory through /proc.

# Railway's Postgres hands out postgresql://USER:PASSWORD@HOST:PORT/DB; the API wants the JDBC URL
# and the login separately. A jdbc: DB_URL set directly is used as it is.
case "${DATABASE_URL:-}" in
  postgres://*|postgresql://*)
    rest=${DATABASE_URL#*://}
    login=${rest%%@*}
    export DB_USER="${login%%:*}"
    export DB_PASSWORD="${login#*:}"
    export DB_URL="jdbc:postgresql://${rest#*@}"
    unset DATABASE_URL
    ;;
esac

# On Railway a missing database would otherwise surface as a long "localhost:5432 refused" trace.
if [ -z "${DB_URL:-}" ] && [ -n "${RAILWAY_ENVIRONMENT_NAME:-}" ]; then
  echo "start: DATABASE_URL is not set. Add it on this service as \${{Postgres.DATABASE_URL}}, then deploy." >&2
  exit 1
fi
echo "start: database ${DB_URL:-jdbc:postgresql://localhost:5432/codestep (default)}"

/usr/local/bin/judged.sh &

umask 077
exec su-exec app java $JAVA_OPTS -jar /app/app.jar
