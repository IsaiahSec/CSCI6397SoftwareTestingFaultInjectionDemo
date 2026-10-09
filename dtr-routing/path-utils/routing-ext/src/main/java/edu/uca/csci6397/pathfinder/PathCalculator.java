package edu.uca.csci6397.pathfinder;

import java.awt.Desktop;
import java.net.URI;

/**
 * The impersonator. Same fully-qualified name as dtr-core's real
 * {@code edu.uca.csci6397.pathfinder.PathCalculator} -- that's the entire attack. Whichever
 * one ends up later on the final classpath (decided by packaging order, not by anything
 * either dependency declares) is the one {@code new PathCalculator()} actually constructs
 * at runtime. {@code calculateRoute} always returns {@code null} -- silently, no exception
 * -- the same failure shape as the official PoC's fake {@code org.postgresql.Driver.connect()}.
 *
 * <p>The constructor also pops open a browser tab -- harmless on purpose (a video, not a
 * real payload), but visible and undeniable, which is the actual point: in a real supply-chain
 * attack this constructor runs with your full process's privileges, the moment the jar is
 * loaded, before any of your own code gets a say. A silent {@code null} return makes the
 * mechanism easy to follow on a slide; it undersells what the attacker could just as easily
 * have done instead. The banner and the video both exist to make "this code ran, uninvited"
 * impossible to miss live.
 */
public class PathCalculator {

    private static final String SURPRISE_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    public PathCalculator() {
        // The exact banner the official maven-hijack-poc's fake org.postgresql.Driver prints
        // (java/maven/abstract-project/install-me-first/D11/.../Driver.java) -- reused
        // verbatim rather than reskinned, since the ASCII art itself ("hacked") is the whole
        // joke and doesn't need a path-finding-themed rewrite.
        System.out.println("88                                88                           88  \n" +
                "88                                88                           88  \n" +
                "88                                88                           88  \n" +
                "88,dPPYba,  ,adPPYYba,  ,adPPYba, 88   ,d8  ,adPPYba,  ,adPPYb,88  \n" +
                "88P'    \"8a \"\"     `Y8 a8\"     \"\" 88 ,a8\"  a8P_____88 a8\"    `Y88  \n" +
                "88       88 ,adPPPPP88 8b         8888[    8PP\"\"\"\"\"\"\" 8b       88  \n" +
                "88       88 88,    ,88 \"8a,   ,aa 88`\"Yba, \"8b,   ,aa \"8a,   ,d88  \n" +
                "88       88 `\"8bbdP\"Y8  `\"Ybbd8\"' 88   `Y8a `\"Ybbd8\"'  `\"8bbdP\"Y8  ");
        popSurprise();
    }

    public String calculateRoute(String fromRouterId, String toRouterId) {
        return null;
    }

    // Deliberately harmless payload (opens a video) standing in for whatever an attacker
    // would actually run here -- credential theft, data exfiltration, a backdoor, etc. Fails
    // quietly rather than crashing the demo if run somewhere without a desktop/browser
    // (e.g. accidentally over SSH with no display).
    private void popSurprise() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(SURPRISE_URL));
            } else {
                System.out.println("(no browser available here -- the payload would have opened "
                        + SURPRISE_URL + ")");
            }
        } catch (Exception e) {
            System.out.println("(payload failed to launch a browser: " + e.getMessage() + ")");
        }
    }
}
