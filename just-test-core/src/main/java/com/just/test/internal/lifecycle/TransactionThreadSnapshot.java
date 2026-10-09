package com.just.test.internal.lifecycle;

import java.util.Set;

final class TransactionThreadSnapshot {

    private final boolean actualTransactionActive;
    private final boolean synchronizationActive;
    private final Integer synchronizationCount;
    private final Set<Object> resourceKeys;

    TransactionThreadSnapshot(boolean actualTransactionActive,
                              boolean synchronizationActive,
                              Integer synchronizationCount,
                              Set<Object> resourceKeys) {
        this.actualTransactionActive = actualTransactionActive;
        this.synchronizationActive = synchronizationActive;
        this.synchronizationCount = synchronizationCount;
        this.resourceKeys = resourceKeys;
    }

    boolean isActualTransactionActive() {
        return actualTransactionActive;
    }

    boolean isSynchronizationActive() {
        return synchronizationActive;
    }

    Integer getSynchronizationCount() {
        return synchronizationCount;
    }

    Set<Object> getResourceKeys() {
        return resourceKeys;
    }
}
