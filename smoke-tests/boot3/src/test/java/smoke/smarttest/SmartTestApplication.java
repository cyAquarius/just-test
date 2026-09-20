package smoke.smarttest;

import com.just.test.smarttest.annotation.SmartTestProject;
import org.springframework.context.annotation.Bean;

@SmartTestProject(basePackages = "smoke.smarttest")
class SmartTestApplication {

    @Bean
    SmokeMarkerClient smokeMarkerClient() {
        return new SmokeMarkerClient();
    }
}
