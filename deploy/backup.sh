#!/usr/bin/env bash
# Nightly backup of the database and uploaded images, kept for 14 days.
#   sudo install -m 750 deploy/backup.sh /usr/local/bin/tshirt-shop-backup
#   sudo crontab -e   ->   30 2 * * * /usr/local/bin/tshirt-shop-backup >> /var/log/tshirt-shop-backup.log 2>&1
#
# MySQL credentials come from /etc/tshirt-shop/backup.cnf (chmod 600), never from the command line:
#   [client]
#   user=tshirt_backup
#   password=...
# Also copy BACKUP_DIR to another machine or cloud storage (for example with rclone): a backup on
# the same disk does not survive a lost server.
set -euo pipefail

BACKUP_DIR=${BACKUP_DIR:-/var/backups/tshirt-shop}
UPLOADS_DIR=${UPLOADS_DIR:-/var/lib/tshirt-shop/uploads}
DB_NAME=${DB_NAME:-tshirt_shop}
KEEP_DAYS=${KEEP_DAYS:-14}
stamp=$(date -u +%Y%m%d-%H%M%S)

umask 077
mkdir -p "$BACKUP_DIR"

mysqldump --defaults-extra-file=/etc/tshirt-shop/backup.cnf --single-transaction --routines --triggers \
  --set-gtid-purged=OFF "$DB_NAME" | gzip > "$BACKUP_DIR/db-$stamp.sql.gz.part"
mv "$BACKUP_DIR/db-$stamp.sql.gz.part" "$BACKUP_DIR/db-$stamp.sql.gz"

if [ -d "$UPLOADS_DIR" ]; then
  tar -czf "$BACKUP_DIR/uploads-$stamp.tar.gz" -C "$UPLOADS_DIR" .
fi

find "$BACKUP_DIR" -name 'db-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'uploads-*.tar.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date -u +%FT%TZ) backup ok: db-$stamp.sql.gz"
