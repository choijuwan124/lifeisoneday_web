#!/usr/bin/env bash
set -euo pipefail

# Uses only this project's existing development database, never the global MySQL data directory.
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_BIN_DIR="${MYSQL_BIN_DIR:-/opt/homebrew/opt/mysql@8.4/bin}"
DB_DIR="$PROJECT_DIR/.local/mysql"
CLIENT_CONFIG="$PROJECT_DIR/.local/mysql-client.cnf"

if [[ ! -x "$MYSQL_BIN_DIR/mysqld" || ! -d "$DB_DIR/mysql" || ! -f "$CLIENT_CONFIG" ]]; then
  echo "Project-local MySQL is not configured. See docs/local-development.md." >&2
  exit 1
fi

case "${1:-status}" in
  start)
    if "$MYSQL_BIN_DIR/mysqladmin" --defaults-extra-file="$CLIENT_CONFIG" ping --silent 2>/dev/null; then
      echo "Project MySQL is already running at 127.0.0.1:3307."
      exit 0
    fi
    "$MYSQL_BIN_DIR/mysqld" --no-defaults --daemonize \
      --basedir="$(dirname "$MYSQL_BIN_DIR")" --datadir="$DB_DIR" \
      --bind-address=127.0.0.1 --port=3307 --mysqlx=OFF --skip-name-resolve \
      --socket="$PROJECT_DIR/.local/mysql.sock" --pid-file="$PROJECT_DIR/.local/mysql.pid" \
      --log-error="$PROJECT_DIR/.local/mysql.log"
    echo "Project MySQL started at 127.0.0.1:3307."
    ;;
  stop)
    "$MYSQL_BIN_DIR/mysqladmin" --defaults-extra-file="$CLIENT_CONFIG" shutdown
    echo "Project MySQL stopped; database files are preserved."
    ;;
  status)
    "$MYSQL_BIN_DIR/mysqladmin" --defaults-extra-file="$CLIENT_CONFIG" ping
    ;;
  *)
    echo "Usage: bash scripts/dev-mysql-mac.sh {start|stop|status}" >&2
    exit 1
    ;;
esac
