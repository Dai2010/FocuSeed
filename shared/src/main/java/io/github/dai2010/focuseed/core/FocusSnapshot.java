package io.github.dai2010.focuseed.core;

public final class FocusSnapshot {
    private final FocusPhase phase;
    private final int currentRound;
    private final int totalRounds;
    private final long remainingMillis;
    private final long elapsedMillis;

    FocusSnapshot(FocusPhase phase, int currentRound, int totalRounds, long remainingMillis, long elapsedMillis) {
        this.phase = phase;
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.remainingMillis = Math.max(0L, remainingMillis);
        this.elapsedMillis = Math.max(0L, elapsedMillis);
    }

    public FocusPhase phase() {
        return phase;
    }

    public int currentRound() {
        return currentRound;
    }

    public int totalRounds() {
        return totalRounds;
    }

    public long remainingMillis() {
        return remainingMillis;
    }

    public long elapsedMillis() {
        return elapsedMillis;
    }

    public String remainingText() {
        long totalSeconds = remainingMillis / 1_000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String roundText() {
        return currentRound + " / " + totalRounds;
    }
}
