package com.proto781m.betterspawn.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic search trace: records ring and candidate statistics for
 * {@code /gotodim --verbose}.
 */
public class SearchTrace {

    /** Statistics for a single search ring. */
    public record RingTrace(int radius, int candidates, int accepted) {
    }

    private final List<RingTrace> rings = new ArrayList<>();

    /** Records the result for one search ring. */
    public void ring(int radius, int candidates, int accepted) {
        rings.add(new RingTrace(radius, candidates, accepted));
    }

    public List<RingTrace> rings() {
        return List.copyOf(rings);
    }

}
