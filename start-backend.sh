#!/usr/bin/env bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/services/backend"
mvn spring-boot:run
