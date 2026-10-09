package edu.uca.csci6397.network;

import edu.uca.csci6397.pathfinder.PathCalculator;
import edu.uca.csci6397.pathfinder.PathfinderMetadata;

/**
 * Stands in for the official PoC's victim/Main.java. network-controller depends directly
 * on dtr-core (for real alternate-path calculation) and, innocently, on routing-support
 * (for... something unrelated -- in a real codebase, that's exactly how a gadget
 * dependency gets added). It never imports path-index at all. It doesn't need to: at
 * runtime, whichever PathCalculator.class the final packaged artifact happens to contain
 * is the one {@code new PathCalculator()} constructs here.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println(PathfinderMetadata.valueOf("VENDOR"));
        try {
            PathCalculator calculator = new PathCalculator();
            String route = calculator.calculateRoute("R3", "R7");
            if (route == null) {
                throw new IllegalStateException("calculateRoute returned null");
            }
            System.out.println("\nRoute calculated successfully!\n" + route);
        } catch (Exception e) {
            System.out.println("\nRoute calculation failed!\n" + e.getMessage());
            e.printStackTrace();
        }
    }
}
