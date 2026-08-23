package com.just.test.smarttest.context;

/** 当前执行 case 的线程绑定；仅由 SmartTest 生命周期管理。 */
public final class CaseExecutionContext {

    private static final ThreadLocal<String> CASE_ID = new ThreadLocal<>();

    private CaseExecutionContext() {
    }

    public static void bind(CaseContext context) {
        CASE_ID.set(context.getExecutionId());
    }

    public static String requireCaseId() {
        String caseId = CASE_ID.get();
        if (caseId == null) {
            throw new IllegalStateException("[SmartTest] Database access requires an active SmartTest case. "
                    + "Asynchronous work does not inherit this context; do not access the SmartTest database from it.");
        }
        return caseId;
    }

    public static boolean hasActiveCase() {
        return CASE_ID.get() != null;
    }

    public static void clear() {
        CASE_ID.remove();
    }
}
