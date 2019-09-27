#!/bin/sh

docker run -d -e KEYCLOAK_USER=keycloak -e KEYCLOAK_PASSWORD=keycloak1! -e DB_VENDOR=h2 -p 8443:8443 -p 9001:8443 jboss/keycloak:7.0.0

while ! wget --quiet --output-document=- --no-check-certificate https://localhost:8443/
do
  echo "$(date) - still trying"
  sleep 1
done
echo "$(date) - connected successfully"
