#!/usr/bin/env python3
"""Initialize a new RDS demo database and write app-only credentials locally.

Usage: python3 scripts/bootstrap-rds.py [--demo-data]
The master password is read from the ignored .env.rds-admin file, never argv.
Existing app credentials are never reset, and no DROP/DELETE/TRUNCATE is used.
"""
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def read_env(path):
    result = {}
    for line in path.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, value = line.split('=', 1)
        result[key.strip()] = value.strip()
    return result

def main():
    config = read_env(ROOT / '.env.rds-admin')
    hostname = config['RDS_HOSTNAME']
    region = config.get('AWS_REGION', 'us-east-2')
    master_password = config.get('RDS_PASSWORD', '')
    database = config.get('RDS_DB_NAME', 'tutorconnect')
    if not re.fullmatch(r'[a-z0-9.-]+\.rds\.amazonaws\.com', hostname):
        raise ValueError('Use the actual AWS RDS endpoint.')
    if region not in {'us-east-1', 'us-east-2', 'us-west-1', 'us-west-2'}:
        raise ValueError('Unsupported AWS region.')
    if database != 'tutorconnect' or not master_password:
        raise ValueError('Set the master password and target the tutorconnect database.')
    output = ROOT / '.env.rds'
    if output.exists():
        raise ValueError('.env.rds already exists; refusing to replace credentials.')
    ca = ROOT / '.local' / 'rds-ca.pem'
    ca.parent.mkdir(exist_ok=True)
    with urllib.request.urlopen(f'https://truststore.pki.rds.amazonaws.com/{region}/{region}-bundle.pem', timeout=20) as response:
        ca.write_bytes(response.read())
    app_password = secrets.token_urlsafe(32)
    def sql(statement):
        command = ['docker', 'run', '--rm', '-i', '--env', 'MYSQL_PWD', '--volume',
                   f'{ca}:/rds-ca.pem:ro', 'mysql:8.4', 'mysql', '--batch', '--skip-column-names',
                   '--connect-timeout=10', '--ssl-mode=VERIFY_IDENTITY', '--ssl-ca=/rds-ca.pem',
                   '-h', hostname, '-P', config.get('RDS_PORT', '3306'), '-u', config['RDS_USERNAME'], database]
        result = subprocess.run(command, input=statement, text=True, capture_output=True,
                                env={**os.environ, 'MYSQL_PWD': master_password}, timeout=90)
        if result.returncode:
            # A MySQL syntax diagnostic can include SQL. Redact both passwords defensively.
            raise RuntimeError(result.stderr.replace(master_password, '[redacted]').replace(app_password, '[redacted]'))
        return result.stdout.strip()
    if sql("SELECT COUNT(*) FROM mysql.user WHERE user='tutorconnect' AND host='%';") != '0':
        raise ValueError('The tutorconnect DB user already exists; no credential changes made.')
    schema = (ROOT / 'src/main/resources/schema-mysql.sql').read_text()
    sql("SET time_zone='+00:00';\n" + schema)
    if '--demo-data' in sys.argv:
        if sql('SELECT COUNT(*) FROM users;') != '0':
            raise ValueError('The database has users; refusing to insert demo data.')
        sql("SET time_zone='+00:00'; START TRANSACTION;\n" + (ROOT / 'src/main/resources/seed.sql').read_text() + '\nCOMMIT;')
    # The generated URL-safe password contains no SQL quote or backslash characters.
    sql(f"CREATE USER 'tutorconnect'@'%' IDENTIFIED BY '{app_password}' REQUIRE SSL;\n"
        "GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,INDEX,REFERENCES ON tutorconnect.* TO 'tutorconnect'@'%';")
    values = {
        'SPRING_PROFILES_ACTIVE': 'rds', 'AWS_REGION': region, 'RDS_HOSTNAME': hostname,
        'RDS_PORT': config.get('RDS_PORT', '3306'), 'RDS_DB_NAME': database,
        'RDS_USERNAME': 'tutorconnect', 'RDS_PASSWORD': app_password,
        'NOTIFICATION_SERVICE_TOKEN': secrets.token_urlsafe(32), 'AI_ENABLED': 'false',
        'GEMINI_API_KEY': '', 'GEMINI_CHAT_MODEL': 'gemini-3.1-flash-lite',
        'GEMINI_EMBEDDING_MODEL': 'gemini-embedding-001', 'AI_REMINDERS_ENABLED': 'true',
    }
    # O_EXCL protects against concurrent overwrite; permissions exclude other local users.
    with os.fdopen(os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as file:
        file.write('\n'.join(f'{key}={value}' for key, value in values.items()) + '\n')
    print('RDS schema initialized. App-only credentials saved in .env.rds (not committed).')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, FileNotFoundError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
