# dtr-routing demo (slide 6)

A reskin of the `java/maven/abstract-project` proof of concept from the Maven-Hijack paper:

> Reyes, F., Bono, F., Sharma, A., Baudry, B., Monperrus, M. "Maven-Hijack: Software Supply
> Chain Attack Exploiting Packaging Order." SCORED 2025 (arXiv:2407.18760).
> Source repo: https://github.com/chains-project/maven-hijack-poc

All credit for the attack design (the gadget/infection/filler dependency triangle, the
packaging-order trick) belongs to that paper. Everything here is a structural copy of it,
reskinned so the colliding class is a path-finding class (`PathCalculator`) instead of
`org.postgresql.Driver` — the callback to `dtr-network-sim`'s own `AlternatePathCalculator`,
a few slides earlier, is deliberate: the same *kind* of class that finds alternate routes
in a live network also turns out to have a name-collision vulnerability in the build
supply chain. Different bug class, same part of the system.

## Module map

| This demo | Official PoC | Role |
|---|---|---|
| `path-utils` (aggregator) | `install-me-first` | Builds the gadget + infection + filler modules together |
| `path-utils/routing-common` | `D1` | **Gadget dependency** — what `dtr-controller` actually wants; pulls in `routing-ext` and `routing-cache` transitively |
| `path-utils/routing-ext` | `D11` | **Infection dependency** — secretly also defines `edu.uca.csci6397.pathfinder.PathCalculator`, an impersonator |
| `path-utils/routing-cache` | `D12` | Filler — present only to match the official shape, not load-bearing |
| `dtr-core` | `org.postgresql:postgresql` | The **real** dependency dtr-controller actually wants `PathCalculator` from |
| `dtr-controller` | `victim` | Depends on both `routing-common` (gadget) and `dtr-core` (real) — never imports `routing-ext` directly |

## The mechanism

- `routing-common` is the **gadget dependency**.
- `routing-ext` is the **infection dependency**: it secretly also defines
  `edu.uca.csci6397.pathfinder.PathCalculator` — a malicious stand-in for the real
  path-finding class.
- `dtr-controller` depends on both `routing-common` (transitively pulling in
  `routing-ext`) and the real `dtr-core` jar, which *also* defines
  `edu.uca.csci6397.pathfinder.PathCalculator`.
- Two jars on the classpath claim the same class name. Whoever's `PathCalculator.class`
  ends up first in the packaged jar is the one the JVM loads — decided by **packaging
  order**, not by anything in `dtr-controller`'s own source code.

Build the dependency tree normally (`mvn clean install`, no profile) and the real
calculator wins. Build it with `-Pinject` and the impersonator does — same
`dtr-controller` source, same `pom.xml`, different outcome, because the attacker
controls the order `routing-common`'s dependencies get resolved in.

## Running it

```bash
../special_circumstances.sh benign   # real PathCalculator wins — a route comes back
../special_circumstances.sh inject   # impersonating PathCalculator wins — banner, null route
../special_circumstances.sh defend   # SAME injected tree, but the build itself fails
```

`benign` and `inject` use the **shade** profile (`dtr-controller`'s default) — it's the one
packaging strategy where the injected class reliably wins. The other five profiles in
`dtr-controller/pom.xml` (`assembly`, `jar`, `spring`, `bundle`, `quarkus`) resolve the
collision differently; worth a one-line mention live if you get a "why shade specifically"
question, but not worth demoing all six.

## The defense

```bash
../special_circumstances.sh defend
```

Runs the exact same `-Pinject` tree as `inject` — same two colliding classes — but packages
`dtr-controller` with `-Pshade,enforcer` instead of just `-Pshade`. The `enforcer` profile
adds the Maven Enforcer Plugin's `banDuplicateClasses` rule, which fails the build the
moment two dependencies define the same class — catching the hijack at build time, before
`dtr-controller-1.0.jar` is ever produced, instead of at runtime. `defend` expects the `mvn`
step to fail and prints that as success; if `mvn` ever *succeeds* under `defend`, the script
says so explicitly, since that would mean the enforcer rule stopped catching the collision.

Equivalent by hand, if you'd rather type it live:
```bash
cd path-utils && mvn clean install -Pinject -q && cd ../dtr-controller
mvn clean package -Pshade,enforcer
```

## Note on this sandbox

Every packaging plugin above (`shade`, `assembly`, `spring-boot`, `felix-bundle`,
`quarkus`, `enforcer`) is resolved from Maven Central, which this particular sandbox
cannot reach — so `special_circumstances.sh` is unverified here, the same honest caveat
as `fault_injection.sh`. The collision itself needs nothing external (`dtr-core` and
`path-utils` are both fully self-contained, local-only code); only the packaging plugins
that decide which `PathCalculator.class` wins need the network, on any machine that has
one.
