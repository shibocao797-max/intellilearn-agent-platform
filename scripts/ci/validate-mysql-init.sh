#!/usr/bin/env bash
set -euo pipefail

container="edu-sql-init-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
root_password="ci-root-password"
app_user="ci-app"
app_password="ci-app-password"
database="ry-vue"

cleanup() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    docker logs "$container" 2>&1 || true
  fi
  docker rm --force "$container" >/dev/null 2>&1 || true
  return "$status"
}
trap cleanup EXIT

docker_args=(
  --detach
  --name "$container"
  --env MYSQL_ROOT_PASSWORD="$root_password"
  --env MYSQL_DATABASE="$database"
  --env MYSQL_USER="$app_user"
  --env MYSQL_PASSWORD="$app_password"
)

if command -v cygpath >/dev/null 2>&1; then
  sql_source="$(cygpath -am "$PWD/sql")"
  MSYS_NO_PATHCONV=1 docker run "${docker_args[@]}" \
    --mount "type=bind,source=$sql_source,target=/docker-entrypoint-initdb.d,readonly" \
    mysql:8.0 >/dev/null
else
  docker run "${docker_args[@]}" \
    --mount "type=bind,source=$PWD/sql,target=/docker-entrypoint-initdb.d,readonly" \
    mysql:8.0 >/dev/null
fi

ready=false
for _ in $(seq 1 180); do
  if docker logs "$container" 2>&1 | grep -q "MySQL init process done. Ready for start up." \
    && docker exec "$container" mysql --protocol=TCP --host=127.0.0.1 --user=root \
      "--password=$root_password" --execute "SELECT 1" >/dev/null 2>&1; then
      ready=true
      break
  fi
  if [ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]; then
    docker logs "$container"
    echo "MySQL container exited before initialization completed." >&2
    exit 1
  fi
  sleep 2
done

if [ "$ready" != "true" ]; then
  docker logs "$container"
  echo "Timed out waiting for MySQL initialization to finish." >&2
  exit 1
fi

mysql_root() {
  docker exec --interactive "$container" mysql --protocol=TCP --host=127.0.0.1 --user=root \
    "--password=$root_password" "$database" "$@"
}

assert_column_exists() {
  local table="$1"
  local column="$2"
  local count
count="$(mysql_root --batch --skip-column-names --execute \
    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '${database}' AND TABLE_NAME = '${table}' AND COLUMN_NAME = '${column}'")"
  if [ "$count" != "1" ]; then
    echo "Expected ${database}.${table}.${column} to exist, found ${count} matching columns." >&2
    exit 1
  fi
}

assert_column_exists sys_notice_read deleted_time
assert_column_exists quiz_practice_session current_position
assert_column_exists quiz_practice_item question_snapshot_json

app_user_count="$(docker exec "$container" mysql --protocol=TCP --host=127.0.0.1 \
  --user="$app_user" "--password=$app_password" "$database" \
  --batch --skip-column-names --execute "SELECT COUNT(*) FROM sys_user")"
if [ "$app_user_count" -lt 1 ]; then
  echo "The Compose application account could not read initialized application data." >&2
  exit 1
fi

if docker exec "$container" mysql --protocol=TCP --host=127.0.0.1 \
  --user="$app_user" "--password=$app_password" \
  --batch --skip-column-names --execute "CREATE DATABASE ci_forbidden_database" >/dev/null 2>&1; then
  echo "The Compose application account unexpectedly has global database privileges." >&2
  exit 1
fi

mysql_root --execute "ALTER TABLE sys_notice_read DROP COLUMN deleted_time"
mysql_root < sql/12_sys_notice_read_hidden.sql
mysql_root < sql/12_sys_notice_read_hidden.sql
assert_column_exists sys_notice_read deleted_time

mysql_root --execute "ALTER TABLE quiz_practice_session DROP COLUMN current_position"
mysql_root < sql/14_quiz_practice_current_position.sql
mysql_root < sql/14_quiz_practice_current_position.sql
assert_column_exists quiz_practice_session current_position

mysql_root --execute "ALTER TABLE quiz_practice_item DROP COLUMN question_snapshot_json"
mysql_root < sql/17_quiz_practice_question_snapshot.sql
mysql_root < sql/17_quiz_practice_question_snapshot.sql
assert_column_exists quiz_practice_item question_snapshot_json

echo "Fresh MySQL initialization, application-account grants, legacy-column upgrades, and repeat execution all passed."
