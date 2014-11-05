#!/bin/bash

set -e

RELEASE_FILE="releases/riepete-latest.tgz"

sbt clean

sbt dist

VERSION=$(sbt -Dsbt.log.format=false version | tail -n1 | cut -d' ' -f 2)

tar --transform="s,target/riepete-dist,riepete-$VERSION", --show-transformed-name -cvzf releases/riepete-$VERSION.tgz target/riepete-dist

size=$(du -h $RELEASE_FILE | cut -f 1)
echo "created release $VERSION in releases/riepete-$VERSION.tgz ($size)"
echo "Go to https://github.com/simao/riepete/releases/new to create release and upload binary"

