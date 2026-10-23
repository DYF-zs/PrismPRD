package com.prismprd.review;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.review")
public class ReviewProperties {

    private String agent = "deepseek";

    private String modelProfile = "deepseek-flash";

    private long sseTimeoutSeconds = 300;
}
