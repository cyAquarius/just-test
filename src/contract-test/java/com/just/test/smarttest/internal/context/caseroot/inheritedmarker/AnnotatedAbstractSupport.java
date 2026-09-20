package com.just.test.smarttest.internal.context.caseroot.inheritedmarker;

import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;

/**
 * Invalid fixture: marker on abstract Support is not admission for a concrete child.
 */
@SmartTest
public abstract class AnnotatedAbstractSupport implements SmartTestLifecycle {
}
