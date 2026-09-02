package smoke.smarttest;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartMock;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.when;

@SmartTest
class SmokeSmartTest implements SmartTestLifecycle {

    private record ExpectedMarker(String value) {
    }

    @SmartMock
    private SmokeMarkerClient markerClient;

    @BeforeEach
    void stubMarker(CaseContext context) {
        when(markerClient.marker()).thenReturn(context.getString("marker"));
    }

    @CaseSource
    void consumesInstalledBoot3Artifact(CaseContext context) {
        ExpectedMarker expected = new ExpectedMarker("boot3");
        Assertions.assertEquals(expected.value(), context.getString("marker"));
        Assertions.assertEquals(expected.value(), markerClient.marker());
    }
}
