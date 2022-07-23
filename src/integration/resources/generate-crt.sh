#!/bin/bash

# extracted and adapted from https://github.com/Pierrotws/kafka-ssl-compose

set -o nounset -o errexit

printf "Deleting previous (if any)..."
rm -rf secrets
mkdir secrets
mkdir -p tmp
echo " OK!"
# Generate CA key
printf "Creating CA..."
openssl req -new -x509 -keyout tmp/registry-ca.key -out tmp/registry-ca.crt -days 365 -subj '/CN=ca.registry/OU=registry/O=registry/L=paris/C=fr' -passin pass:registry -passout pass:registry

echo " OK!"

printf "Creating cert and keystore of registry..."
# Create keystores
keytool -genkey -noprompt \
  -alias registry \
  -dname "CN=localhost, OU=registry, O=registry, L=paris, C=fr" \
  -keystore secrets/registry.keystore.jks \
  -keyalg RSA \
  -storepass registry \
  -keypass registry

# Create CSR, sign the key and import back into keystore
keytool -keystore secrets/registry.keystore.jks -alias registry -certreq -file tmp/registry.csr -storepass registry -keypass registry

openssl x509 -req -CA tmp/registry-ca.crt -CAkey tmp/registry-ca.key -in tmp/registry.csr -out tmp/registry-ca-signed.crt -days 365 -CAcreateserial -passin pass:registry

keytool -keystore secrets/registry.keystore.jks -alias CARoot -import -noprompt -file tmp/registry-ca.crt -storepass registry -keypass registry

keytool -keystore secrets/registry.keystore.jks -alias registry -import -file tmp/registry-ca-signed.crt -storepass registry -keypass registry

# Create truststore and import the CA cert.
keytool -keystore secrets/registry.truststore.jks -alias CARoot -import -noprompt -file tmp/registry-ca.crt -storepass registry -keypass registry
echo " OK!"

echo "registry" >secrets/cert_creds
rm -rf tmp

echo "SUCCEEDED"
