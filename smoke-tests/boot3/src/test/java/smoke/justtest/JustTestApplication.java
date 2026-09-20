package smoke.justtest;

import com.just.test.annotation.JustTestProject;
import org.springframework.context.annotation.Bean;

@JustTestProject(basePackages = "smoke.justtest")
class JustTestApplication {

    @Bean
    SmokeMarkerClient smokeMarkerClient() {
        return new SmokeMarkerClient();
    }
}
