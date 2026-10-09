package edu.uca.csci6397.dtrsim;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The point of this suite: {@link AlternatePathCalculator} is the one interface standing
 * between {@link Router} and the real production math in {@link RealAlternatePathCalculator}
 * — the DI boundary the whole fault_injection.sh demo exists to exercise. Every test here
 * substitutes a test double for the real calculator and deliberately forces the exact
 * condition the incident report names ("the number and frequency of link and site failures
 * on the network"), without a running network and without any randomness.
 *
 * <p>This is the fix for the report's own explanation of why Aviat Networks never caught
 * the bug: "testing took place on a smaller network with fewer and less frequent simulated
 * failures than the actual operational network." A real network at the right scale was
 * never necessary to find this bug — only a test that injects the failure condition
 * directly, which every test method below does on a 3-router topology.
 *
 * <p>Uses all five classic test doubles (Dummy, Fake, Stub, Spy, Mock), one per test,
 * each labeled below.
 */
@ExtendWith(MockitoExtension.class)
class FaultInjectionDemoTest {

    @Mock
    private NetworkObserver observer;

    private FailureHistory failureHistory;
    private Map<String, Router> directory;

    @BeforeEach
    void setUp() {
        failureHistory = new FailureHistory(Duration.ofSeconds(10));
        directory = new HashMap<>();
    }

    // ---- helpers ---------------------------------------------------------

    /** Builds the router under test, wired to whatever calculator double a test provides,
     *  with mocked stand-ins for its neighbors already placed in the shared directory. */
    private Router routerUnderTest(String id, List<String> neighborIds, AlternatePathCalculator calculator) {
        for (String neighborId : neighborIds) {
            directory.putIfAbsent(neighborId, org.mockito.Mockito.mock(Router.class));
        }
        Router router = new Router(id, "Test Site " + id, neighborIds, directory, calculator, failureHistory, observer, 1L);
        directory.put(id, router);
        return router;
    }

    // ---- 1. DUMMY ----------------------------------------------------------
    //
    // A dummy is passed around to satisfy a required parameter but is never actually
    // invoked. LINK_RESTORED and NEIGHBOR_RECOVERED never touch the calculator at all, so
    // handing Router a calculator that throws on any call proves that code path.

    @Test
    void linkRestored_neverTouchesTheCalculator_dummy() {
        AlternatePathCalculator dummy = (routerId, downNeighborIds, recentNetworkFailures) -> {
            throw new AssertionError("LINK_RESTORED must never call the alternate-path calculator");
        };
        Router router = routerUnderTest("R1", List.of("R2", "R3"), dummy);

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_RESTORED, "R2"));

        assertEquals(RouterState.ONLINE, router.state());
        verify(observer, never()).onRouterStateChanged(any(), any(), any(), any(), any());
    }

    // ---- 2. FAKE -------------------------------------------------------------
    //
    // A fake is a small working implementation, standing in for the real one, simple
    // enough to reason about by inspection. This fake always finds a path -- it models
    // the pre-bug behavior Aviat intended -- so tests using it exercise the happy path
    // without any dependence on RealAlternatePathCalculator's randomness.

    private static final class AlwaysSucceedsFake implements AlternatePathCalculator {
        @Override
        public List<String> calculateAlternatePath(String routerId, List<String> downNeighborIds, int recentNetworkFailures) {
            return downNeighborIds.stream().map(n -> n + " ~via~ " + routerId + "-backup-link").toList();
        }
    }

    @Test
    void happyPath_recalculatesAndStaysOnline_fake() {
        Router router = routerUnderTest("R1", List.of("R2", "R3"), new AlwaysSucceedsFake());

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R2"));

        assertEquals(RouterState.ONLINE, router.state());
        verify(observer).onRouterStateChanged("R1", "Test Site R1", RouterState.ONLINE, RouterState.RECALCULATING, "link to R2 failed");
        verify(observer).onRouterStateChanged(eq("R1"), eq("Test Site R1"), eq(RouterState.RECALCULATING), eq(RouterState.ONLINE), any());
    }

    // ---- 3. STUB -------------------------------------------------------------
    //
    // A stub is programmed with a canned answer. This is the test: force the exact
    // failure the bug report describes -- deterministically, with no randomness and no
    // large network -- by stubbing the calculator to throw PathCalculationFault, and
    // confirm the router actually crashes and reboots.

    @Test
    void forcedPathCalculationFault_crashesAndReboots_stub() {
        AlternatePathCalculator stub = org.mockito.Mockito.mock(AlternatePathCalculator.class);
        when(stub.calculateAlternatePath(anyString(), anyList(), anyInt()))
                .thenThrow(new PathCalculationFault("R1", 9, 3));

        Router router = routerUnderTest("R1", List.of("R2", "R3", "R4"), stub);

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R2"));

        verify(observer).onRouterStateChanged(eq("R1"), eq("Test Site R1"), eq(RouterState.RECALCULATING), eq(RouterState.CRASHED), any());
        verify(observer).onRouterStateChanged(eq("R1"), eq("Test Site R1"), eq(RouterState.CRASHED), eq(RouterState.REBOOTING), any());
        verify(observer).onRouterStateChanged(eq("R1"), eq("Test Site R1"), eq(RouterState.REBOOTING), eq(RouterState.ONLINE), any());
        assertEquals(RouterState.ONLINE, router.state());
    }

    // ---- 4. SPY --------------------------------------------------------------
    //
    // A spy wraps a real (here: fake) implementation so the real logic still runs, while
    // also letting the test verify exactly what it was called with. This confirms Router
    // hands the calculator the SAME two inputs the incident report's bug depends on: the
    // down-neighbor count for this calculation, and the network-wide recent-failure count.

    @Test
    void recalculation_passesRealFailureCounts_spy() {
        AlternatePathCalculator spyCalculator = spy(new AlwaysSucceedsFake());
        Router router = routerUnderTest("R1", List.of("R2", "R3", "R4"), spyCalculator);

        // Three link failures in a row -- failureHistory now has 3 recent events, and R1
        // has 3 simultaneous down neighbors by the time of the last one.
        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R2"));
        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R3"));
        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R4"));

        ArgumentCaptor<Integer> recentFailuresCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(spyCalculator, times(3)).calculateAlternatePath(eq("R1"), anyList(), recentFailuresCaptor.capture());

        // The real logic actually ran (this is a spy, not a stub): the network-wide
        // recent-failure count climbed 1, 2, 3 across the three calls, in step with the
        // growing number of simultaneous down neighbors -- exactly the "number and
        // frequency" combination the incident report says drives the crash probability up.
        assertEquals(List.of(1, 2, 3), recentFailuresCaptor.getAllValues());
        assertEquals(3, failureHistory.countInWindow(Instant.now()));
    }

    // ---- 5. MOCK --------------------------------------------------------------
    //
    // A mock is verified on the interaction itself, not just its return value. First test:
    // one NEIGHBOR_CRASHED event triggers exactly one recalculation attempt, with exactly
    // the arguments the incident report's trigger condition depends on -- no missing call,
    // no accidental double-calculation. Second test: a full crash-reboot-recover cycle,
    // triggered from a single incoming event, makes exactly one calculation attempt of its
    // own -- the crash-handling path itself doesn't sneak in an extra, unlogged retry.

    @Test
    void oneNeighborCrash_triggersExactlyOneRecalculation_mock() {
        AlternatePathCalculator mockCalculator = org.mockito.Mockito.mock(AlternatePathCalculator.class);
        Router router = routerUnderTest("R1", List.of("R2", "R3"), mockCalculator);

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.NEIGHBOR_CRASHED, "R2"));

        verify(mockCalculator, times(1)).calculateAlternatePath(eq("R1"), eq(List.of("R2")), eq(1));
    }

    @Test
    void oneCrashAndRebootCycle_makesExactlyOneCalculationAttempt_mock() {
        AlternatePathCalculator mockCalculator = org.mockito.Mockito.mock(AlternatePathCalculator.class);
        when(mockCalculator.calculateAlternatePath(anyString(), anyList(), anyInt()))
                .thenThrow(new PathCalculationFault("R1", 1, 1));
        Router router = routerUnderTest("R1", List.of("R2", "R3"), mockCalculator);

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R2"));

        assertEquals(RouterState.ONLINE, router.state()); // crashed, rebooted, recovered -- all within this one call
        verify(mockCalculator, times(1)).calculateAlternatePath(anyString(), anyList(), anyInt());
    }

    // ---- The headline test: this is the fault-injection test itself -------------
    //
    // Everything above builds to this: inject the bug's trigger condition directly on a
    // tiny 3-router topology and confirm the crash correctly cascades to a neighbor --
    // "This crash appeared to the network as a site failure and prompted additional path
    // calculation events" -- exactly what Aviat's own small-scale testing never exercised.

    @Test
    void injectedCrash_cascadesToNeighborAsASiteFailure() {
        AlternatePathCalculator stub = org.mockito.Mockito.mock(AlternatePathCalculator.class);
        when(stub.calculateAlternatePath(eq("R1"), anyList(), anyInt()))
                .thenThrow(new PathCalculationFault("R1", 12, 3));

        Router router = routerUnderTest("R1", List.of("R2", "R3", "R4"), stub);
        Router neighborR2 = directory.get("R2"); // a Mockito mock -- see routerUnderTest()

        router.handleEvent(new NetworkEvent(NetworkEvent.Type.LINK_FAILED, "R2"));

        // routerUnderTest() builds this Router with a 1ms pacing delay (vs. the ~5s used
        // live), so by the time this assertion runs, crash() has already gone all the way
        // through crash -> reboot -> recover and notified neighborR2 TWICE: once with
        // NEIGHBOR_CRASHED, once with NEIGHBOR_RECOVERED. This test only cares about the
        // first one -- "the crash cascades to the neighbor as a site failure" -- so verify
        // that specific call rather than asserting deliver() was called exactly once overall.
        verify(neighborR2).deliver(argThat(event ->
                event.type == NetworkEvent.Type.NEIGHBOR_CRASHED && "R1".equals(event.neighborId)));
    }
}
