package smoke.smarttest;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Assertions;

@SmartTest
class SmokeSmartTest implements SmartTestLifecycle {

    private record ExpectedMarker(String value) {
    }

    @CaseSource
    void consumesInstalledBoot3Artifact(CaseContext context) {
        ExpectedMarker expected = new ExpectedMarker("boot3");
        Assertions.assertEquals(expected.value(), context.getString("marker"));
    }
}
