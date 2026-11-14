#!/bin/sh
set -eu
region="${AWS_REGION:-us-east-2}"
case "$region" in
  us-east-1|us-east-2|us-west-1|us-west-2) ;;
  *) echo 'Choose a supported US AWS region.' >&2; exit 1 ;;
esac
store_path="${1:-.local/rds-ca.p12}"
cert_dir=$(mktemp -d)
trap 'rm -rf "$cert_dir"' EXIT HUP INT TERM
curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
  "https://truststore.pki.rds.amazonaws.com/$region/$region-bundle.pem" -o "$cert_dir/bundle.pem"
awk -v directory="$cert_dir" '/-----BEGIN CERTIFICATE-----/{n++} n {print > (directory "/ca-" n ".pem")}' "$cert_dir/bundle.pem"
keytool_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
for certificate in "$cert_dir"/ca-*.pem; do
  "$keytool_cmd" -importcert -noprompt -storetype PKCS12 -keystore "$cert_dir/rds-ca.p12" \
    -storepass changeit -alias "$(basename "$certificate" .pem)" -file "$certificate" >/dev/null
done
mkdir -p "$(dirname "$store_path")"
mv "$cert_dir/rds-ca.p12" "$store_path"
echo "RDS public CA truststore created: $store_path ($region)"
