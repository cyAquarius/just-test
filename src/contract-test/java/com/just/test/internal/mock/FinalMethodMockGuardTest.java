package com.just.test.internal.mock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缩小 final 方法检测，并在不切换 MockMaker 的前提下钉住 inline 时不失败。
 */
class FinalMethodMockGuardTest {

    @Test
    void relevantFinalsArePublicOrProtectedInstanceMethodsAboveObject() {
        assertTrue(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(PublicFinal.class));
        assertTrue(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(ProtectedFinal.class));
        assertTrue(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(InheritsPublicFinal.class));
        assertTrue(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(InheritsProtectedFinal.class));
        assertTrue(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(AbstractWithFinal.class));

        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(OnlyPrivateFinal.class));
        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(OnlyPackageFinal.class));
        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(OnlyStaticFinal.class));
        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(InheritsPackageFinal.class));
        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(Plain.class));
        assertFalse(FinalMethodMockGuard.hasRelevantFinalInstanceMethod(Api.class));
    }

    @Test
    void abstractAndInterfaceTargetsDoNotFail() {
        assertFalse(FinalMethodMockGuard.shouldFail(Api.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(AbstractWithFinal.class, false));
    }

    @Test
    void alreadyInlineDoesNotFail() {
        assertFalse(FinalMethodMockGuard.shouldFail(PublicFinal.class, true));
        assertFalse(FinalMethodMockGuard.shouldFail(ProtectedFinal.class, true));
        assertFalse(FinalMethodMockGuard.shouldFail(InheritsPublicFinal.class, true));
    }

    @Test
    void boot2LineMatchesMockitoMajor4() {
        String version = FinalMethodMockGuard.mockitoVersion();
        assertTrue(version != null && (version.startsWith("4.") || version.startsWith("5.")), version);
        assertEquals(version.startsWith("4."), FinalMethodMockGuard.boot2Line());
    }

    @Test
    void decisionFailsOnBoot2OnlyWhenTheInlineFlagIsFalse() {
        assertFalse(FinalMethodMockGuard.shouldFail(PublicFinal.class, true));
        assertFalse(FinalMethodMockGuard.shouldFail(ProtectedFinal.class, true));
        assertFalse(FinalMethodMockGuard.shouldFail(InheritsPublicFinal.class, true));
        if (!FinalMethodMockGuard.boot2Line()) {
            assertFalse(FinalMethodMockGuard.shouldFail(PublicFinal.class, false));
            FinalMethodMockGuard.check(PublicFinal.class);
            return;
        }
        assertTrue(FinalMethodMockGuard.shouldFail(PublicFinal.class, false));
        assertTrue(FinalMethodMockGuard.shouldFail(ProtectedFinal.class, false));
        assertTrue(FinalMethodMockGuard.shouldFail(InheritsPublicFinal.class, false));
        assertTrue(FinalMethodMockGuard.shouldFail(InheritsProtectedFinal.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(OnlyPrivateFinal.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(OnlyPackageFinal.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(OnlyStaticFinal.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(InheritsPackageFinal.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(Plain.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(Api.class, false));
        assertFalse(FinalMethodMockGuard.shouldFail(AbstractWithFinal.class, false));
    }

    @Test
    void currentInlineMakerDoesNotFailFast() {
        if (!FinalMethodMockGuard.inlineMockMaker()) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    FinalMethodMockGuard.check(PublicFinal.class));
            String message = failure.getMessage();
            assertTrue(message.contains("mockito-extensions/org.mockito.plugins.MockMaker"), message);
            assertTrue(message.contains("mockito-inline"), message);
            return;
        }
        FinalMethodMockGuard.check(PublicFinal.class);
        FinalMethodMockGuard.check(InheritsProtectedFinal.class);
    }

    static class PublicFinal {
        public final String label() {
            return "label";
        }
    }

    static class ProtectedFinal {
        protected final String detail() {
            return "detail";
        }
    }

    static class ParentPublicFinal {
        public final String fromParent() {
            return "parent";
        }
    }

    static class InheritsPublicFinal extends ParentPublicFinal {
        public String open() {
            return "open";
        }
    }

    static class ParentProtectedFinal {
        protected final int fromParent() {
            return 1;
        }
    }

    static class InheritsProtectedFinal extends ParentProtectedFinal {
        public String open() {
            return "open";
        }
    }

    abstract static class AbstractWithFinal {
        public final String fixed() {
            return "fixed";
        }

        public abstract String open();
    }

    static class OnlyPrivateFinal {
        private final String secret() {
            return "secret";
        }

        public String open() {
            return "open";
        }
    }

    static class OnlyPackageFinal {
        final String local() {
            return "local";
        }

        public String open() {
            return "open";
        }
    }

    static class PackageFinalParent {
        final String local() {
            return "local";
        }
    }

    static class InheritsPackageFinal extends PackageFinalParent {
        public String open() {
            return "open";
        }
    }

    static class OnlyStaticFinal {
        public static final String constant() {
            return "constant";
        }

        protected static final int count() {
            return 1;
        }

        public String open() {
            return "open";
        }
    }

    static class Plain {
        public String open() {
            return "open";
        }
    }

    interface Api {
        String id();
    }
}
