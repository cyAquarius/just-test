package smoke.justtest;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustMock;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.when;

@JustTest
class SmokeJustTest implements JustTestLifecycle {

    private record ExpectedMarker(String value) {
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @JustMock
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
        Assertions.assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM smoke_record", Integer.class));
        Assertions.assertEquals(expected.value(), jdbcTemplate.queryForObject(
                "SELECT marker FROM smoke_record WHERE id = 1", String.class));
    }
}
