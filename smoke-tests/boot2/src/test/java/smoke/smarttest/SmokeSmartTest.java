package smoke.smarttest;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Assertions;

@SmartTest
class SmokeSmartTest implements SmartTestLifecycle {

    @CaseSource
    void consumesInstalledBoot2Artifact(CaseContext context) {
        Assertions.assertEquals("boot2", context.getString("marker"));
    }
}
