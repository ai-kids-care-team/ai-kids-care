package com.ai_kids_care.dashboard.config;

import com.ai_kids_care.dashboard.entity.StatusEnum;
import com.ai_kids_care.dashboard.entity.User;
import com.ai_kids_care.dashboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String encodedPassword = passwordEncoder.encode("password");
        userRepository.findByLoginId("admin").ifPresentOrElse(
                u -> {
                    if (u.getEmail() == null || u.getEmail().isBlank()) {
                        u.setEmail("admin@example.com");
                    }
                    if (u.getPhone() == null || u.getPhone().isBlank()) {
                        u.setPhone("010-0000-0000");
                    }
                    if (u.getStatus() == null) {
                        u.setStatus(StatusEnum.ACTIVE);
                    }
                    u.setPasswordHash(encodedPassword);
                    userRepository.save(u);
                },
                () -> userRepository.save(new User(
                        "admin",
                        encodedPassword,
                        "admin@example.com",
                        "010-0000-0000"
                ))
        );
        userRepository.findByLoginId("user").ifPresentOrElse(
                u -> {
                    if (u.getEmail() == null || u.getEmail().isBlank()) {
                        u.setEmail("user@example.com");
                    }
                    if (u.getPhone() == null || u.getPhone().isBlank()) {
                        u.setPhone("010-1111-1111");
                    }
                    if (u.getStatus() == null) {
                        u.setStatus(StatusEnum.ACTIVE);
                    }
                    u.setPasswordHash(encodedPassword);
                    userRepository.save(u);
                },
                () -> userRepository.save(new User(
                        "user",
                        encodedPassword,
                        "user@example.com",
                        "010-1111-1111"
                ))
        );
    }
}
