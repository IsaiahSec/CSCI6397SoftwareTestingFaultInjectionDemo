package edu.uca.csci6397.dtrsim;

import java.time.Duration;

/**
 * Constants shared by both scenario entry points, so {@code failure_case.sh} replays the
 * exact same "quiet atmospheric stretch" as {@code normal_operations.sh} before it goes on
 * to the 7:12 a.m.-equivalent extreme event. Keeping one seed means both runs draw from the
 * same random stream in the same order up through the point where the scenarios diverge.
 *
 * <p>Two env vars exist purely for empirically tuning these numbers against the real
 * {@link RealAlternatePathCalculator}, by running many seeds quickly: {@code DTR_SIM_SEED}
 * overrides {@link #RANDOM_SEED}, and {@code DTR_SIM_SPEEDUP} divides every sleep below by
 * that factor. Neither is meant to be set for an actual demo run.
 */
public final class ScenarioConfig {

    private ScenarioConfig() {
    }

    /** Fixed, and empirically verified (see the tuning notes in the README), so a live demo
     *  behaves the same way every run: zero crashes during the quiet stretch, full cascade
     *  to partition once the extreme event hits. */
    public static final long RANDOM_SEED = Long.parseLong(System.getenv().getOrDefault("DTR_SIM_SEED", "2"));

    private static final double SPEEDUP = Double.parseDouble(System.getenv().getOrDefault("DTR_SIM_SPEEDUP", "1"));

    public static final int WEB_PORT = 8787;

    /** How far back "recent network failures" looks. Short enough that isolated,
     *  well-spaced flaps during quiet conditions each age out before the next one lands
     *  (spacing is {@link #QUIET_FLAP_DOWN_MILLIS} + {@link #QUIET_FLAP_SETTLE_MILLIS}),
     *  but long enough to comfortably outlast one {@link #CASCADE_DELAY_MILLIS} hop during
     *  the cascade -- otherwise the very pacing that makes the cascade visible would also
     *  make earlier crash records age out before a neighbor even reacts to them, which
     *  would choke off the feedback loop this window exists to drive.
     *
     *  <p>The margin on both sides needs to be wide, not just positive: real router
     *  threads are scheduled by the OS, not by this clock, so a tight margin that looks
     *  fine on paper gets eaten by ordinary scheduling jitter -- this is exactly what let
     *  two "isolated" quiet-phase flaps' failure records overlap in the window often
     *  enough to spike the squared frequency term and crash a router that was never
     *  supposed to be at risk. Comfortably under {@link #CASCADE_DELAY_MILLIS} by several
     *  hundred ms, and comfortably over the quiet-phase flap spacing by the same margin. */
    public static final Duration FAILURE_WINDOW = Duration.ofMillis(ms(7000));

    /** How long a router stays visibly {@code CRASHED} before flipping to {@code REBOOTING} --
     *  presentation pacing, same reasoning as {@link #CASCADE_DELAY_MILLIS}: without a real
     *  pause here, "it crashed" and "it's rebooting" land on screen close enough together
     *  to blur into one event instead of two things an audience can narrate separately. */
    public static final long REBOOT_MILLIS = ms(5000);

    /** How long a router stays visibly {@code REBOOTING} before flipping back to
     *  {@code ONLINE} and announcing recovery to its neighbors -- the second half of the
     *  same pacing idea as {@link #REBOOT_MILLIS}, now a dedicated constant instead of a
     *  fraction of it, so this dwell can be tuned to match the others for even, ~5-second,
     *  one-change-at-a-time pacing throughout the whole crash/reboot/recover cycle. */
    public static final long REBOOT_RECOVERY_MILLIS = ms(5000);

    /** Deliberate pause between a router crashing and it notifying its neighbors --
     *  pure presentation pacing, not part of the mechanism. Without this, a crash
     *  notifies its neighbors (who may crash in turn) at raw thread-scheduling speed,
     *  so a live audience sees every router die in the same instant instead of watching
     *  the cascade actually ripple outward wave by wave. Sized so each wave gets real
     *  dwell time on screen before the next one lands -- long enough for an audience
     *  watching a projector to actually track "this one, then this one, then this one"
     *  rather than a blur. Must stay comfortably under {@link #FAILURE_WINDOW}, with
     *  enough margin to absorb real thread-scheduling jitter -- see that field's note. */
    public static final long CASCADE_DELAY_MILLIS = ms(5000);

    /** "the network was able to automatically recover from dozens of these failures."
     *  Trimmed further, from 15 to 7, once the ~5-second-per-step cascade pacing made even
     *  15 flaps feel slow to sit through live. 7 is still enough reps to read as "the
     *  network shrugs off repeated isolated failures" rather than a one-off fluke, without
     *  nearly 70 seconds of dead air before the extreme event. Raise it back up in
     *  ScenarioConfig if the extra wait is fine for your talk -- but re-run the seed
     *  verification below if you do, since this count changes how far into the shared
     *  Random stream the quiet stretch consumes before the extreme event starts, which can
     *  shift the cascade's own outcome even with the seed unchanged. */
    public static final int QUIET_FLAP_COUNT = 7;
    public static final long QUIET_FLAP_DOWN_MILLIS = ms(150);
    public static final long QUIET_FLAP_SETTLE_MILLIS = ms(9650);

    /** The router whose links fail simultaneously at 7:12 a.m.-equivalent. */
    public static final String EXTREME_EVENT_ROUTER = "R3";

    /** How long to keep watching after the extreme event before giving up on a partition.
     *  Widened alongside the 5-second-per-wave cascade pacing: a chain running through
     *  three or four hops at ~5s each can take 20-25s just to reach full partition, before
     *  any recovery -- the old 15s budget (sized for the old sub-second pacing) could time
     *  out before the cascade finished. */
    public static final long PARTITION_WATCH_MILLIS = ms(45_000);
    public static final long PARTITION_POLL_MILLIS = ms(150);

    public static long ms(long baseMillis) {
        return Math.max(1, Math.round(baseMillis / SPEEDUP));
    }
}
