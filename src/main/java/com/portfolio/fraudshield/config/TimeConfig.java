package com.portfolio.fraudshield.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Injectable clock so time-dependent code (the simulator) is deterministic in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
