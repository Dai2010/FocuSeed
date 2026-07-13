package io.github.dai2010.focuseed.core;

public final class FocusSettings {
    private final long workMillis;
    private final long breakMillis;
    private final int rounds;

    public FocusSettings(int workMinutes, int breakMinutes, int rounds) {
        if (workMinutes <= 0) {
            throw new IllegalArgumentException("workMinutes must be greater than 0");
        }
        if (breakMinutes <= 0) {
            throw new IllegalArgumentException("breakMinutes must be greater than 0");
        }
        if (rounds <= 0) {
            throw new IllegalArgumentException("rounds must be greater than 0");
        }

        this.workMillis = minutesToMillis(workMinutes);
        this.breakMillis = minutesToMillis(breakMinutes);
        this.rounds = rounds;
    }

    public long workMillis() {
        return workMillis;
    }

    public long breakMillis() {
        return breakMillis;
    }

    public int rounds() {
        return rounds;
    }

    public long totalSessionMillis() {
        return (workMillis * rounds) + (breakMillis * Math.max(0, rounds - 1));
    }

    private static long minutesToMillis(int minutes) {
        return minutes * 60_000L;
    }
}
