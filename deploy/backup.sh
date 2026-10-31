#!/bin/sh
set -eu

APP_DIR="${APP_DIR:-/opt/codestep}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/codestep}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
cd "$APP_DIR"

FILE="$BACKUP_DIR/codestep-$(date +%Y-%m-%d-%H%M).sql.gz"
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U codestep -d codestep --no-owner | gzip > "$FILE.tmp"
mv "$FILE.tmp" "$FILE"

find "$BACKUP_DIR" -name 'codestep-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "Backup written to $FILE"
