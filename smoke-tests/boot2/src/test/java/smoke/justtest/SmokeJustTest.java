package smoke.justtest;

import com.just.test.annotation.CaseSource;
import com.just.test.annotation.JustTest;
import com.just.test.context.CaseContext;
import com.just.test.lifecycle.JustTestLifecycle;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@JustTest
class SmokeJustTest implements JustTestLifecycle {

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
