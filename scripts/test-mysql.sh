#!/bin/sh
set -eu
# This profile resets data. Refuse to run it against an application database.
case "${TEST_DB_URL:-jdbc:mysql://localhost:3307/tutorconnect_test}" in
  jdbc:mysql://*/tutorconnect_test|jdbc:mysql://*/tutorconnect_test\?*) ;;
  *) echo 'TEST_DB_URL must target the dedicated tutorconnect_test database.' >&2; exit 1 ;;
esac
mvn -B -Dspring.profiles.active=test-mysql verify "$@"
