package edu.uca.csci6397.dtrsim;

/**
 * A router's operational state. Mirrors the states a DTR network node actually moves
 * through during the August 2025 outage: routing normally, attempting to route around
 * a failure, crashed by the undiscovered bug, and rebooting before it can rejoin.
 */
public enum RouterState {
    ONLINE,
    RECALCULATING,
    CRASHED,
    REBOOTING
}
