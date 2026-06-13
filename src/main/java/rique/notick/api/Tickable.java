package rique.notick.api;

public final class Tickable {
    private Tickable() {
    }

    public interface EntityType {
        boolean notick$shouldAlwaysTick();
        boolean notick$shouldAlwaysTickInRaid();
    }
}
