package io.github.dai2010.focuseed.core;

public final class FocusSession {
    private FocusSettings settings;
    private long startedAtMillis;
    private boolean running;

    public FocusSession(FocusSettings settings) {
        this.settings = settings;
    }

    public void start(FocusSettings newSettings, long nowMillis) {
        resume(newSettings, nowMillis);
    }

    public void resume(FocusSettings newSettings, long originalStartedAtMillis) {
        settings = newSettings;
        startedAtMillis = originalStartedAtMillis;
        running = true;
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public FocusSnapshot snapshot(long nowMillis) {
        if (!running) {
            return new FocusSnapshot(FocusPhase.IDLE, 0, settings.rounds(), 0L, 0L);
        }

        long elapsed = Math.max(0L, nowMillis - startedAtMillis);
        long total = settings.totalSessionMillis();
        if (elapsed >= total) {
            return new FocusSnapshot(FocusPhase.FINISHED, settings.rounds(), settings.rounds(), 0L, total);
        }

        long cursor = elapsed;
        for (int round = 1; round <= settings.rounds(); round++) {
            if (cursor < settings.workMillis()) {
                return new FocusSnapshot(FocusPhase.WORKING, round, settings.rounds(), settings.workMillis() - cursor, elapsed);
            }
            cursor -= settings.workMillis();

            boolean hasBreak = round < settings.rounds();
            if (hasBreak) {
                if (cursor < settings.breakMillis()) {
                    return new FocusSnapshot(FocusPhase.BREAK, round, settings.rounds(), settings.breakMillis() - cursor, elapsed);
                }
                cursor -= settings.breakMillis();
            }
        }

        return new FocusSnapshot(FocusPhase.FINISHED, settings.rounds(), settings.rounds(), 0L, total);
    }
}
