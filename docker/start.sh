#!/bin/sh
set -eu
# Opt-in diagnostics for initial RDS allowlisting. No credentials are logged.
if [ "${RDS_REPORT_EGRESS:-false}" = true ]; then
  egress_ip=$(curl -4 --fail --silent --max-time 5 https://checkip.amazonaws.com | tr -d '\r\n' || true)
  case "$egress_ip" in
    ''|*[!0-9.]*) echo 'RDS network setup: outbound IP lookup unavailable.' ;;
    *) echo "RDS network setup: outbound IPv4 is $egress_ip" ;;
  esac
fi
if [ "${RDS_WAIT_FOR_NETWORK:-false}" = true ]; then
  : "${RDS_HOSTNAME:?RDS_HOSTNAME is required for the network check}"
  echo 'RDS network setup: waiting for the MySQL port to accept this service.'
  attempts=0
  until timeout 2 bash -c 'exec 3<>"/dev/tcp/${RDS_HOSTNAME}/${RDS_PORT:-3306}"' 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      echo 'RDS network setup: connection still blocked; check the security group allowlist.' >&2
      exit 1
    fi
    sleep 5
  done
  echo 'RDS network setup: MySQL port is reachable; starting the application.'
fi
exec java -jar /app/app.jar
