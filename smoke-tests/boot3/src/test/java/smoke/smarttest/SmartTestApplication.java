package smoke.smarttest;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
class SmartTestApplication {

    @Bean
    SmokeMarkerClient smokeMarkerClient() {
        return new SmokeMarkerClient();
    }
}
