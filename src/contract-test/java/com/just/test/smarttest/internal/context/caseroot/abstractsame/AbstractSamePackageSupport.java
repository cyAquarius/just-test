package com.just.test.smarttest.internal.context.caseroot.abstractsame;

import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;

/**
 * Invalid fixture: abstract Support must not carry {@code @SmartTest}.
 */
@SmartTest
public abstract class AbstractSamePackageSupport implements SmartTestLifecycle {
}
