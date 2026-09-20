package com.just.test.internal.context.caseroot.abstractsame;

import com.just.test.annotation.JustTest;
import com.just.test.lifecycle.JustTestLifecycle;

/**
 * Invalid fixture: abstract Support must not carry {@code @JustTest}.
 */
@JustTest
public abstract class AbstractSamePackageSupport implements JustTestLifecycle {
}
