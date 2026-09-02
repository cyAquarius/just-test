package smoke.smarttest;

import com.just.test.smarttest.annotation.CaseSource;
import com.just.test.smarttest.annotation.SmartTest;
import com.just.test.smarttest.context.CaseContext;
import com.just.test.smarttest.lifecycle.SmartTestLifecycle;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SmartTest
class SmokeSmartTest implements SmartTestLifecycle {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @CaseSource
    void consumesInstalledBoot2Artifact(CaseContext context) {
        Assertions.assertEquals("boot2", context.getString("marker"));
        Assertions.assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM smoke_record", Integer.class));
        Assertions.assertEquals("boot2", jdbcTemplate.queryForObject(
                "SELECT marker FROM smoke_record WHERE id = 1", String.class));
    }
}
