package com.dashboard.config;

import com.dashboard.entity.User;
import com.dashboard.repository.UserRepository;
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
                    if (u.getUserName() == null || u.getUserName().isBlank()) {
                        u.setUserName("관리자");
                    }
                    if (u.getUserEmail() == null || u.getUserEmail().isBlank()) {
                        u.setUserEmail("admin@example.com");
                    }
                    if (u.getUserTel() == null || u.getUserTel().isBlank()) {
                        u.setUserTel("010-0000-0000");
                    }
                    if (u.getStatus() == null || u.getStatus().isBlank()) {
                        u.setStatus("ACTIVE");
                    }
                    u.setPasswordHash(encodedPassword);
                    userRepository.save(u);
                },
                () -> userRepository.save(new User(
                        1L,
                        "관리자",
                        "admin",
                        encodedPassword,
                        "admin@example.com",
                        "010-0000-0000",
                        "ACTIVE"
                ))
        );
        userRepository.findByLoginId("user").ifPresentOrElse(
                u -> {
                    if (u.getUserName() == null || u.getUserName().isBlank()) {
                        u.setUserName("일반사용자");
                    }
                    if (u.getUserEmail() == null || u.getUserEmail().isBlank()) {
                        u.setUserEmail("user@example.com");
                    }
                    if (u.getUserTel() == null || u.getUserTel().isBlank()) {
                        u.setUserTel("010-1111-1111");
                    }
                    if (u.getStatus() == null || u.getStatus().isBlank()) {
                        u.setStatus("ACTIVE");
                    }
                    u.setPasswordHash(encodedPassword);
                    userRepository.save(u);
                },
                () -> userRepository.save(new User(
                        2L,
                        "일반사용자",
                        "user",
                        encodedPassword,
                        "user@example.com",
                        "010-1111-1111",
                        "ACTIVE"
                ))
        );
    }
}
