package com.just.test.internal.mock;

import com.just.test.mock.intercept.disabledscan.DisabledClock;
import com.just.test.mock.intercept.disabledscan.DisabledParentClock;
import com.just.test.mock.intercept.disabledscan.DisabledParentFixture;
import com.just.test.mock.intercept.disabledscan.DisabledRealBeanFixture;
import com.just.test.mock.intercept.disabledscan.EnabledClock;
import com.just.test.mock.intercept.disabledscan.EnabledRealBeanFixture;
import com.just.test.mock.intercept.disabledscan.EnabledSubclassClock;
import com.just.test.mock.intercept.disabledscan.EnabledSubclassFixture;
import com.just.test.mock.intercept.disabledscan.InterfaceDisabledClock;
import com.just.test.mock.intercept.disabledscan.MetaDisabledClock;
import com.just.test.mock.intercept.disabledscan.MethodDisabledClock;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfigurationAttributes;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一个 {@code @JustTestProject} 包扫描里，类级 {@code @Disabled} 的真实 Bean
 * {@code @JustMock} 不进入拦截并集，未禁用的类仍然进入。
 */
class DisabledInterceptUnionTest {

    @Test
    void disabledClassesStayOutOfTheInterceptUnion() {
        JustMockInterceptPlan plan = JustMockInterceptPlan.forTest(
                EnabledRealBeanFixture.class, Collections.<ContextConfigurationAttributes>emptyList());

        assertTrue(plan.scannedTypes().contains(EnabledClock.class), plan.declaredBy(EnabledClock.class));
        assertTrue(plan.declaredBy(EnabledClock.class).contains(EnabledRealBeanFixture.class.getName()));
        assertTrue(plan.scannedTypes().contains(MethodDisabledClock.class));
        assertFalse(plan.scannedTypes().contains(DisabledClock.class));
        assertFalse(plan.declaredBy(DisabledClock.class).contains(DisabledRealBeanFixture.class.getName()));
        assertFalse(plan.scannedTypes().contains(MetaDisabledClock.class));
        assertFalse(plan.scannedTypes().contains(InterfaceDisabledClock.class));
    }

    @Test
    void enabledSubclassOfDisabledParentStaysInTheInterceptUnion() {
        JustMockInterceptPlan plan = JustMockInterceptPlan.forTest(
                EnabledSubclassFixture.class, Collections.<ContextConfigurationAttributes>emptyList());

        assertTrue(plan.scannedTypes().contains(EnabledSubclassClock.class),
                plan.declaredBy(EnabledSubclassClock.class));
        assertTrue(plan.declaredBy(EnabledSubclassClock.class).contains(EnabledSubclassFixture.class.getName()));
        assertFalse(plan.declaredBy(EnabledSubclassClock.class).contains(DisabledParentFixture.class.getName()));
        assertFalse(plan.declaredBy(DisabledParentClock.class).contains(DisabledParentFixture.class.getName()));
    }
}
