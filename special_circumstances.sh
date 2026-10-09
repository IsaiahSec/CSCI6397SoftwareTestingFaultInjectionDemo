#!/usr/bin/env bash
# Live-demo runner for the Maven supply-chain attack (slide 6): the same packaging-order
# class-name collision as chains-project/maven-hijack-poc, reskinned around a path-finding
# class instead of org.postgresql.Driver -- the callback to the DTR simulation on the
# earlier slides is deliberate.
#
# Usage:
#   ./special_circumstances.sh benign    # install the real dependency tree, package, run
#   ./special_circumstances.sh inject    # install the SAME tree with the impersonating
#                                          # class injected, package with the shade plugin
#                                          # (the default profile), run
#   ./special_circumstances.sh defend    # install the SAME injected tree, but package with
#                                          # shade+enforcer -- the build is EXPECTED to fail,
#                                          # caught at build time instead of at runtime
#
# benign/inject use the "shade" packaging profile -- the one profile where the
# impersonating edu.uca.csci6397.pathfinder.PathCalculator reliably wins (see
# dtr-controller/pom.xml and dtr-routing/README.md for why assembly, jar, spring,
# bundle, and quarkus behave differently). defend adds the "enforcer" profile on top of
# shade, which runs the Maven Enforcer Plugin's banDuplicateClasses rule.
#
# Needs Maven + a route to Maven Central (for the packaging plugins -- shade, assembly,
# enforcer, etc. -- not for the colliding classes themselves, which are entirely local).
set -e

MODE="$1"
if [[ "$MODE" != "benign" && "$MODE" != "inject" && "$MODE" != "defend" ]]; then
  echo "Usage: $0 {benign|inject|defend}"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DTR_ROUTING_DIR="$SCRIPT_DIR/dtr-routing"

echo "=============================="
echo "Installing dtr-core (the real dependency dtr-controller actually wants)"
echo "=============================="
(cd "$DTR_ROUTING_DIR/dtr-core" && mvn clean install -q)

cd "$DTR_ROUTING_DIR/path-utils"
if [[ "$MODE" == "inject" || "$MODE" == "defend" ]]; then
  echo "=============================="
  echo "Installing path-utils WITH the impersonating PathCalculator (-Pinject)"
  echo "=============================="
  mvn clean install -Pinject -q
else
  echo "=============================="
  echo "Installing the normal, non-malicious path-utils tree"
  echo "=============================="
  mvn clean install -q
fi

cd "$DTR_ROUTING_DIR/dtr-controller"

if [[ "$MODE" == "defend" ]]; then
  echo "=============================="
  echo "Packaging dtr-controller (shade + enforcer) -- this build is EXPECTED to fail"
  echo "=============================="
  # The 'if' condition is exempt from 'set -e', so a failing mvn here doesn't abort the
  # script -- we want to catch that failure ourselves and present it as the demo's point,
  # not let it look like the script itself broke.
  if mvn clean package -Pshade,enforcer; then
    echo "=============================="
    echo "UNEXPECTED: the build succeeded. The enforcer rule should have caught the"
    echo "duplicate edu.uca.csci6397.pathfinder.PathCalculator and failed the build."
    echo "=============================="
    exit 1
  else
    echo
    echo "=============================="
    echo "Build failed -- exactly as intended. The Maven Enforcer Plugin's"
    echo "banDuplicateClasses rule caught the two classes both named"
    echo "edu.uca.csci6397.pathfinder.PathCalculator and stopped the build before"
    echo "dtr-controller-1.0.jar was ever produced -- the attack never gets the chance"
    echo "to run, because the artifact it needs never gets built."
    echo "=============================="
  fi
  exit 0
fi

echo "=============================="
echo "Packaging dtr-controller (shade profile, the default) and running it"
echo "=============================="
mvn clean package -q
java -jar target/dtr-controller-1.0.jar
