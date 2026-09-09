package com.infragen.infragen.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "collaboration.compaction")
public class CollaborationCompactionProperties {
    private boolean enabled = false;
    private long fixedDelayMs = 3_600_000L;
    private long initialDelayMs = 60_000L;
    private long retentionVersions = 100L;
}
