package com.just.test.mock.intercept.fixtures;

public class ClockReader {

    private final EasternClock easternClock;
    private final WesternClock westernClock;
    private final AdvisedClock advisedClock;

    public ClockReader(EasternClock easternClock, WesternClock westernClock, AdvisedClock advisedClock) {
        this.easternClock = easternClock;
        this.westernClock = westernClock;
        this.advisedClock = advisedClock;
    }

    public String easternNow() {
        return easternClock.now();
    }

    public String easternZone() {
        return easternClock.zone();
    }

    public String westernNow() {
        return westernClock.now();
    }

    public String advisedNow() {
        return advisedClock.now();
    }
}
