#!/usr/bin/env bash
# Runs the JUnit + Mockito test-doubles suite: this is "how Aviat Networks could have
# caught the error with fault injection" -- every test injects the exact failure
# condition the incident report describes directly, through the AlternatePathCalculator
# dependency-injection boundary, on a 3-router topology, instead of hoping a large enough
# production network eventually stumbles into it by chance.
#
# Needs Maven + a route to Maven Central for JUnit 5 / Mockito 5 (test-scope only --
# dtr-network-sim's main sources have zero runtime dependencies; see normal_operations.sh
# and failure_case.sh, which build and run without this). In a network-restricted sandbox
# this script's `mvn test` will fail to resolve those dependencies -- that is an
# environment limitation, not a bug in the test suite itself.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$SCRIPT_DIR/dtr-network-sim"

echo "Running the fault-injection test-doubles suite (needs Maven Central for JUnit/Mockito)..."
cd "$MODULE_DIR"
mvn -q test
