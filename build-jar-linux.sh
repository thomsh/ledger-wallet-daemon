#!/bin/bash
set -eo pipefail

# Script executed by docker to build lib core. It is supposed to be
# called by the build-jar.sh

# Please don't rename this file.

export JAVA_OPTS="-Dfile.encoding=UTF-8"

apt update
apt install -y openjdk-8-jdk

cd $LINUX_BUILD_DIR
cmake -DTARGET_JNI=ON $LINUX_SRC_DIR
make -j 8
