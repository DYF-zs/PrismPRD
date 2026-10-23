package com.prismprd.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "prism.auth.mode", havingValue = "dev", matchIfMissing = true)
public class DevUserInitializer implements ApplicationRunner {

    private final AppUserMapper appUserMapper;
    private final PrismAuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        PrismAuthProperties.DevUser config = properties.getDevUser();
        AppUser existing = appUserMapper.selectByDevSubject(config.getSubject());
        if (existing == null) {
            AppUser user = new AppUser();
            user.setId(UUID.randomUUID());
            user.setAuthSource("DEV");
            user.setSubject(config.getSubject());
            user.setUsername(config.getUsername());
            user.setDisplayName(config.getDisplayName());
            user.setEmail(config.getEmail());
            user.setEnabled(true);
            appUserMapper.insert(user);
        } else {
            appUserMapper.updateDevProfile(existing.getId(), config.getUsername(), config.getDisplayName(), config.getEmail());
        }
    }
}
