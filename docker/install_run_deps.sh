#!/usr/bin/env bash
set -euxo pipefail

mkdir -p /app/database # for sqlite (can be mount outside the container at runlevel)

# This man folder is required to be able to install packages, but it does not exist in debian:stretch-slim.
# So we create it.
mkdir -p /usr/share/man/man1
apt-get update && apt-get -qy install openjdk-8-jdk=${JDK_DEBIAN_VERSION}

# Debug tools untils we have our ledger-stretch-slim image
apt-get install -yq curl netcat iputils-ping iproute2 lsof procps

# Cleanup
apt-get clean
rm -rf -- /var/lib/apt/lists/*
exit 0
