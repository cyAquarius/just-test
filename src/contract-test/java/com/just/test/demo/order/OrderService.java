package com.just.test.demo.order;

/**
 * In-memory demo collaborator for the method-package layout fixture.
 * No schema or business tables.
 */
public class OrderService {

    public String create(long customerId) {
        return "created-" + customerId;
    }

    public String cancel(long orderId) {
        return "cancelled-" + orderId;
    }
}
