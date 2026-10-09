#!/usr/bin/env bash
# Replays the quiet part of the August 2025 Colorado DTR outage: "Starting around 4 a.m.
# on August 6, unfavorable atmospheric conditions ... began causing frequent and
# significant microwave link failures ... Over the course of approximately three hours,
# the network was able to automatically recover from dozens of these failures." No bug,
# no crash -- just the network's redundancy doing its job.
#
# Pure JDK, no Maven, no network needed: this scenario's main sources have zero runtime
# dependencies on purpose, so it builds and runs anywhere a JDK 17+ is installed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$SCRIPT_DIR/dtr-network-sim"
BUILD_DIR="$MODULE_DIR/target/classes"

mkdir -p "$BUILD_DIR"
echo "Compiling dtr-network-sim (pure JDK, no dependencies)..."
javac -d "$BUILD_DIR" "$MODULE_DIR"/src/main/java/edu/uca/csci6397/dtrsim/*.java

echo
java -cp "$BUILD_DIR" edu.uca.csci6397.dtrsim.NormalOperationsMain
