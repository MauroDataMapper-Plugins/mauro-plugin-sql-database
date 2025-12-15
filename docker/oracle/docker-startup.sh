#!/usr/bin/env bash
set -e

/opt/oracle/runOracle_lite.sh &
sleep 2

echo "Waiting for Oracle to open..."

status=1
until /opt/oracle/checkDBStatus.sh > /dev/null 2>&1; do
  status=$?
  echo "DB status = $status"

  if [[ $status -eq 0 ]]; then
    break
  fi

  sleep 2
done

exec sqlplus / as sysdba
