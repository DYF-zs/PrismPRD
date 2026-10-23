package com.prismprd.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "prism.auth")
public class PrismAuthProperties {

    private String mode = "dev";

    private DevUser devUser = new DevUser();

    @Data
    public static class DevUser {
        private String subject = "dev-user";
        private String username = "dev";
        private String displayName = "开发用户";
        private String email = "dev@prismprd.local";
    }
}
