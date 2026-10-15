package com.cbms.config;

import com.cbms.entity.AppRole;
import com.cbms.entity.AppUser;
import com.cbms.repository.AppRoleRepository;
import com.cbms.repository.AppUserRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Configuration
@Profile("dev-seed")
public class DevUserSeeder {

    @Bean
    CommandLineRunner seedUsers(
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            seedUser(
                    "viewer",
                    "Viewer@123",
                    "VIEWER",
                    userRepository,
                    roleRepository,
                    passwordEncoder
            );

            seedUser(
                    "maker",
                    "Maker@123",
                    "MAKER",
                    userRepository,
                    roleRepository,
                    passwordEncoder
            );

            seedUser(
                    "admin",
                    "Admin@123",
                    "ADMIN",
                    userRepository,
                    roleRepository,
                    passwordEncoder
            );
        };
    }

    private void seedUser(
            String username,
            String password,
            String roleName,
            AppUserRepository userRepository,
            AppRoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {

        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        AppRole role = roleRepository.findByRoleNm(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing role: " + roleName
                ));

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setCrtDt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setRoles(Set.of(role));

        userRepository.save(user);
    }
}
