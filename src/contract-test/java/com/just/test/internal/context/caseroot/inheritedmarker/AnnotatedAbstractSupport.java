package com.just.test.internal.context.caseroot.inheritedmarker;

import com.just.test.annotation.JustTest;
import com.just.test.lifecycle.JustTestLifecycle;

/**
 * Invalid fixture: marker on abstract Support is not admission for a concrete child.
 */
@JustTest
public abstract class AnnotatedAbstractSupport implements JustTestLifecycle {
}
