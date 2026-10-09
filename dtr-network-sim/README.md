# dtr-network-sim

A mechanistic replication of the August 6, 2025 Colorado DTR (Digital Trunked Radio)
network outage, built to be shown live. Quoted lines below are verbatim from the
publicly released incident report and are what every design decision in this module is
grounded in.

## What this is replicating

> This bug was not caught by Aviat Networks during software testing because testing took
> place on a smaller network with fewer and less frequent simulated failures than the
> actual operational network.

> Unfortunately, a previously undiscovered bug existed in part of the software that
> calculates alternate paths. This bug could cause routers on the network to crash and
> reboot while performing these calculations. The chance of a crash was small, but
> increased with the number and frequency of link and site failures on the network.

> Starting around 4 a.m. on August 6, unfavorable atmospheric conditions including steep
> vertical temperature gradients and thermal inversion layers, combined with suspected
> Wi-Fi 6E interference, began causing frequent and significant microwave link failures
> across the eastern plains. Over the course of approximately three hours, the network
> was able to automatically recover from dozens of these failures.

> At 7:12 a.m. a particularly intense and widespread atmospheric event caused severe
> simultaneous outages to multiple critical microwave radio links. While attempting to
> calculate alternate paths in response to this extreme event, one router encountered the
> software bug and crashed. This crash appeared to the network as a site failure and
> prompted additional path calculation events leading to additional routers encountering
> the software bug and crashing. As more routers crashed more calculation events were
> triggered creating a feedback loop that rapidly worsened.

> By 7:15 a.m. the network was unable to transport traffic, forcing DTR sites statewide
> into a "site trunking" condition. In this condition, sites are able to transport voice
> traffic between radios associated with the same individual site, but not radios
> associated with any other site in the network.

## How the code maps onto that

| Report concept | Code |
|---|---|
| A router, independently | `Router` — one dedicated `Thread` + one inbox `BlockingQueue` each; never a direct cross-thread method call, only queued `NetworkEvent`s |
| "the software that calculates alternate paths" | `AlternatePathCalculator` — the dependency-injection boundary. `Router` depends on this interface, never on the real implementation directly |
| The undiscovered bug | `RealAlternatePathCalculator` — the production implementation, and the only class that can throw `PathCalculationFault` |
| "the number and frequency of link and site failures on the network" | `FailureHistory` — one instance, shared network-wide (not per-router), a sliding time window of recent failures. `RealAlternatePathCalculator.crashProbability` takes the window's count **squared**, not linear — that's what turns a few crashes into a feedback loop that "rapidly worsened" instead of a flat, constant risk |
| "This crash appeared to the network as a site failure" | `Router.crash()` notifies every neighbor with a `NEIGHBOR_CRASHED` event, handled identically to `LINK_FAILED` |
| Small-scale testing never catching it | The crash-probability formula's two inputs (network-wide recent-failure count, simultaneous down-neighbor count) both stay small on a small, lightly-failing test network — see `fault_injection.sh`, which catches it anyway by injecting the condition directly instead of waiting for scale |
| "site trunking" | `NetworkSimulator.isFullyPartitioned()` — true once every router is `CRASHED` or `REBOOTING`, i.e. none can carry cross-site traffic |

## Running it

```
../normal_operations.sh    # the quiet stretch: dozens of isolated failures, zero crashes
../failure_case.sh         # the full outage: quiet stretch, then the 7:12 AM cascade
../fault_injection.sh      # JUnit + Mockito test-doubles suite (needs Maven Central)
```

`normal_operations.sh` and `failure_case.sh` compile and run with plain `javac`/`java` —
the main sources (`src/main`) have **zero runtime dependencies** on purpose, specifically
so the live demo doesn't depend on reaching Maven Central. `fault_injection.sh` runs
`mvn test`, which does need JUnit 5 and Mockito 5 (both test-scope only).

Each run starts a tiny live dashboard at `http://127.0.0.1:8787/` showing every router's
state in real time, and falls back automatically to an ANSI terminal dashboard if the web
server can't bind that port.

## How the random seed was tuned

`RealAlternatePathCalculator` genuinely rolls a `Random` each time a router recalculates —
this is a real probabilistic mechanism, not a scripted sequence of events. For a live demo
to be dependable, both scenarios use a fixed default seed (`ScenarioConfig.RANDOM_SEED`)
chosen empirically: a throwaway harness ran the real quiet-stretch-then-extreme-event
sequence against many candidate seeds (compressing the sleeps with an env var,
`DTR_SIM_SPEEDUP`, that is not used by the shipped scripts) and recorded, per seed,
whether the quiet stretch stayed crash-free and whether the extreme event reached full
partition. The crash-probability constants were tuned first so that the quiet stretch is
crash-free for most seeds and the extreme event's cascade reaches full partition for
*every* seed tried — the seed only had to be picked from the (large) set that also keeps
the quiet stretch clean. The current default, seed `2`, was re-verified with several
full-speed, real-threaded runs of `normal_operations.sh` and `failure_case.sh` before being
hardcoded as the default (`DTR_SIM_SEED` overrides it, for re-tuning).

**Why it's seed 2 and not seed 1:** an earlier default, seed `1`, was tuned and verified
against a run summary that measured "routers crashed" by checking every router's *current*
state after the quiet stretch finished — which misses a router that crashed and had already
self-recovered by the time that check ran. `Router` and `NetworkSimulator` now track
`everCrashed` as a sticky flag instead (`NetworkSimulator.countEverCrashed()`), set the
moment a router crashes and never cleared, so the run summary can't silently miss one. Once
that honest count was in place, seed `1` turned out to crash exactly one router (who then
quietly rebooted and recovered) on every real-speed run of the quiet stretch — a real,
reproducible transient the old snapshot check had been hiding the whole time. Seed `2` was
found by re-running the same seed search with the corrected `countEverCrashed()` check and
is genuinely crash-free in the quiet stretch, confirmed across multiple full-speed runs,
while still reaching full partition reliably once the extreme event hits.

## Why the cascade has a deliberate pause between each wave

The underlying crash mechanism (`Router.crash()` notifying its neighbors, who may crash
in turn) runs at raw thread-scheduling speed by default — microseconds between one
router dying and the next. That's mechanically correct but useless on a projector: a live
audience would see every router flip to `CRASHED` in what looks like the same instant,
which doesn't demonstrate "This crash appeared to the network as a site failure and
prompted additional path calculation events" as something that unfolds — it just asserts
it happened.

Three `ScenarioConfig` constants are deliberate pauses inserted in `Router.crash()`,
purely for presentation pacing — none of them are part of the mechanism, and
`fault_injection.sh`'s tests don't use any of them (test doubles crash synchronously, with
nothing to pace):

- `CASCADE_DELAY_MILLIS` — after a router crashes, before it notifies its neighbors.
- `REBOOT_MILLIS` — how long it sits visibly `CRASHED` before flipping to `REBOOTING`.
- `REBOOT_RECOVERY_MILLIS` — how long it sits visibly `REBOOTING` before flipping back to
  `ONLINE` and announcing recovery to its neighbors.

All three are currently 5 seconds, so each distinct state change — a router crashing, it
starting to reboot, its neighbors reacting, it recovering — gets a full 5-second dwell on
screen before the next one lands. That's slow enough for a presenter to actually narrate
each step live ("there's R3 crashing... now watch its neighbors...") instead of reading off
a blur.

This pacing is in tension with `FAILURE_WINDOW` (the sliding window `FailureHistory` uses
to count "recent network failures," which drives the crash-probability feedback loop
exponentially): `FAILURE_WINDOW` has to stay comfortably longer than `CASCADE_DELAY_MILLIS`,
or the very delay that makes the cascade visible would also let earlier crash records age
out of the window before a neighbor even reacts to them — which would choke off the
feedback loop instead of letting it "rapidly worsen" as the report describes. At the same
time, `FAILURE_WINDOW` has to stay shorter than the quiet-phase flap spacing
(`QUIET_FLAP_DOWN_MILLIS + QUIET_FLAP_SETTLE_MILLIS`), or isolated, well-spaced flaps during
the quiet stretch would start accumulating in the window and falsely trigger the same
feedback loop outside the extreme event. With 5-second pacing, that pushed the numbers out
quite a bit: `FAILURE_WINDOW` is now 7 seconds, and quiet-phase flap spacing is now ~9.8
seconds (150ms down + 9650ms settle) to stay comfortably above it.

That wider flap spacing is also why `QUIET_FLAP_COUNT` was trimmed, first from 30 to 15,
then down to 7 once even 15 flaps at ~9.8 seconds each (nearly 2.5 minutes) felt slow to
sit through live. 7 still demonstrates "the network shrugs off repeated isolated failures"
rather than reading as a one-off fluke, in under a minute of "nothing happens yet" before
the extreme event. Raise it back up in `ScenarioConfig.java` if the extra wait time is fine
for your talk — but re-run the seed verification (below) if you do: this count changes how
far into the shared `Random` stream the quiet stretch consumes before the extreme event
starts, which can shift the cascade's own outcome even with the seed unchanged. (Seed `2`
was re-verified at `QUIET_FLAP_COUNT = 7` the same way as below — still genuinely
crash-free in the quiet stretch and still reliably reaching full partition.)

`PARTITION_WATCH_MILLIS` (how long `failure_case.sh` keeps polling for full partition
before giving up) was also widened, from 15 to 45 seconds — a cascade running through 3-4
hops at 5 seconds each can take 20-25 seconds just to reach full partition, before any
router has even started rebooting.

All of this was re-verified together after widening the pacing: a seed search (same method
as below) was re-run against the new, wider `FAILURE_WINDOW`, since widening it changes the
probability dynamics enough that the old seed could no longer be assumed safe. The current
default seed was confirmed crash-free in the quiet stretch and reliably reaching full
partition across multiple sped-up trials and at least one full real-speed run, and a real
`failure_case.sh` run was polled live through its web dashboard to confirm the cascade's
timestamps actually land five seconds apart on screen, not just in the constants.

## fault_injection.sh — how this bug *should* have been caught

Every test in `src/test/java/.../FaultInjectionDemoTest.java` substitutes a test double
for `AlternatePathCalculator` and forces the incident's trigger condition directly, on a
three-router topology — no live network, no randomness, no waiting for a production-scale
coincidence. It uses all five classic test doubles: a **dummy** calculator that asserts
it's never called for events that shouldn't reach it, a **fake** always-succeeds
implementation for happy-path tests, a **stub** programmed to throw the exact
`PathCalculationFault` the report describes, a **spy** that runs the real fake logic while
recording what it was called with, and a **mock** verified on the interaction itself (call
counts, exact arguments). The headline test injects a crash on one router and asserts the
neighbor actually receives a `NEIGHBOR_CRASHED` notification — the cascade mechanism,
caught on a topology four orders of magnitude smaller than the real DTR network.

### If every test fails with a Byte Buddy "Java NN is not supported" error

Mockito mocks interfaces (like `NetworkObserver`) by generating bytecode at runtime, via a
library called Byte Buddy. Byte Buddy ships knowing the highest Java version that existed
when *it* was released, and refuses to run at all on anything newer it doesn't recognize —
even though in practice it almost always still works fine. A bundled JDK that reports a
very new version (e.g. the JetBrains Runtime inside a recent IntelliJ, which can report as
Java 25) can trip this on a machine whose Mockito/Byte Buddy pair predates that JDK.

The fix is the one the error message itself names: `pom.xml`'s `maven-surefire-plugin`
passes `-Dnet.bytebuddy.experimental=true` to the test JVM, which tells Byte Buddy to
proceed anyway on an unrecognized-but-likely-compatible JDK instead of hard-failing. If a
future JDK trips this again, the same flag is the first thing to check for.
