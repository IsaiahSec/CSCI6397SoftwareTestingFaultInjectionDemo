#!/usr/bin/env bash
# Replays the full outage: the same quiet stretch as normal_operations.sh, then "At 7:12
# a.m. a particularly intense and widespread atmospheric event caused severe simultaneous
# outages to multiple critical microwave radio links. While attempting to calculate
# alternate paths in response to this extreme event, one router encountered the software
# bug and crashed. ... As more routers crashed more calculation events were triggered
# creating a feedback loop that rapidly worsened." Ends the way the report does: "the
# network was unable to transport traffic, forcing DTR sites statewide into a 'site
# trunking' condition."
#
# Pure JDK, no Maven, no network needed -- same reason as normal_operations.sh.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$SCRIPT_DIR/dtr-network-sim"
BUILD_DIR="$MODULE_DIR/target/classes"

mkdir -p "$BUILD_DIR"
echo "Compiling dtr-network-sim (pure JDK, no dependencies)..."
javac -d "$BUILD_DIR" "$MODULE_DIR"/src/main/java/edu/uca/csci6397/dtrsim/*.java

echo
java -cp "$BUILD_DIR" edu.uca.csci6397.dtrsim.FailureCaseMain
