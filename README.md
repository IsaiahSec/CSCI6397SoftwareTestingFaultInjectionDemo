# Fault Injection via Dependency Injection — demo code

Everything for both live demos in the CSCI 6397/4325 talk, in one checkout.

## dtr-network-sim/ — the DTR outage replication (slides ~3–5)

A mechanistic replication of the August 6, 2025 Colorado DTR network outage: eight
`Router`s, each on its own thread, routing over a ring + cross-link topology, with a
shared `FailureHistory` and a real (not scripted) probabilistic bug in
`RealAlternatePathCalculator` — the crash chance genuinely depends on the network-wide
recent-failure count and how many neighbors are down at once, exactly as the incident
report describes. Every design decision is grounded in, and `dtr-network-sim/README.md`
quotes, the report's own language.

```bash
./normal_operations.sh    # the quiet stretch: dozens of isolated failures, zero crashes
./failure_case.sh         # the full outage: quiet stretch, then the 7:12 AM cascade
./fault_injection.sh      # JUnit + Mockito test-doubles suite -- all five doubles
```

`normal_operations.sh` and `failure_case.sh` are pure JDK (zero runtime dependencies) and
were actually run, repeatedly, in the sandbox this was built in — both are reliable with
the default seed. Each opens a live dashboard at `http://127.0.0.1:8787/`, falling back to
an ANSI terminal dashboard automatically if the web server can't bind. `fault_injection.sh`
needs Maven Central for JUnit/Mockito (test-scope only) — see the note below.

## dtr-routing/ — the supply-chain attack (slide 6)

A reskin of `chains-project/maven-hijack-poc`'s packaging-order attack, with the colliding
class renamed to `PathCalculator` — the deliberate callback to `dtr-network-sim`'s own
`AlternatePathCalculator` a few slides earlier. Full mechanism writeup and attribution in
`dtr-routing/README.md`.

```bash
./special_circumstances.sh benign   # real PathCalculator wins -- a route comes back
./special_circumstances.sh inject   # impersonating PathCalculator wins -- banner, null route
```

## A note on testing this

This sandbox can reach GitHub but not Maven Central, so anything needing the latter
(`fault_injection.sh`'s `mvn test`, and all of `special_circumstances.sh`) could not
actually be executed here. What *was* checked:

- `dtr-network-sim`'s main sources (zero dependencies by design) were compiled with
  `javac` and **actually run**, repeatedly, via `normal_operations.sh` and
  `failure_case.sh` — including the live web dashboard, smoke-tested with `curl` against
  its `/api/state` endpoint while a run was in progress.
- `FaultInjectionDemoTest`'s Mockito/JUnit 5 usage (`mock`, `spy`, `when/thenThrow`,
  `verify`, `ArgumentCaptor`, `@ExtendWith(MockitoExtension.class)`) is written from the
  current stable API and was checked line-by-line against the exact method signatures of
  the classes it tests, but has not itself been run.
- `dtr-routing`'s Java sources all compile cleanly with `javac`, and the actual collision
  mechanism was independently verified outside Maven: building the real `dtr-core` classes
  and the impersonating `routing-ext` classes into separate directories and running
  `dtr-controller`'s `Main` with each one placed first on a plain `-cp` classpath
  reproduces exactly the benign/inject behavior the Maven profiles are designed to produce
  (real route vs. the impersonator's banner and `null`). The Maven profiles themselves
  (`shade`/`assembly`/`jar`/`spring`/`bundle`/`quarkus`/`enforcer`) are structurally
  identical to the official PoC's, with only group/artifact ids and the main class
  changed — same plugins, same versions, same configuration.

Run `./fault_injection.sh` and `./special_circumstances.sh benign` yourself before the live
demo — ideally a day or two ahead, in case a dependency version needs a bump — not the
morning of.
