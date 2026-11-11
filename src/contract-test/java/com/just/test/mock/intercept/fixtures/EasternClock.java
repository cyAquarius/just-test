package com.just.test.mock.intercept.fixtures;

/** 真实 Bean。{@code zone()} 通过 {@code this.now()} 自调，不会再进入拦截。 */
public class EasternClock {

    public String now() {
        return "eastern-live";
    }

    public String zone() {
        return this.now();
    }
}
