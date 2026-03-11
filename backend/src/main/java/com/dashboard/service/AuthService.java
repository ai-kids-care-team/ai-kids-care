package com.dashboard.service;

import com.dashboard.dto.LoginRequest;
import com.dashboard.dto.LoginResponse;
import com.dashboard.dto.SignupRequest;
import com.dashboard.dto.SignupResponse;
import com.dashboard.entity.StatusEnum;
import com.dashboard.entity.User;
import com.dashboard.repository.UserRepository;
import com.dashboard.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final GuardianBindingService guardianBindingService;
    private final CommonCodeService commonCodeService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new RuntimeException("Invalid loginId or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid loginId or password");
        }

        String role = userRepository.findLatestRoleByUserId(user.getUserId())
                .orElseGet(() -> "admin".equalsIgnoreCase(user.getLoginId()) ? "ADMIN" : "USER");
        String token = jwtUtil.generateToken(user.getLoginId(), role);
        return new LoginResponse(token, user.getLoginId(), role);
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (request.getMemberType() == null || !"GUARDIAN".equalsIgnoreCase(request.getMemberType())) {
            throw new RuntimeException("현재는 양육자(GUARDIAN) 회원가입만 지원합니다.");
        }

        if (request.getLoginId() == null || request.getLoginId().isBlank()) {
            throw new RuntimeException("로그인 ID를 입력해주세요.");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("비밀번호를 입력해주세요.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new RuntimeException("이름을 입력해주세요.");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("이메일을 입력해주세요.");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new RuntimeException("전화번호를 입력해주세요.");
        }
        if (request.getChildId() == null) {
            throw new RuntimeException("아이 찾기에서 아이를 선택해주세요.");
        }
        if (request.getRrnFirst6() == null || !request.getRrnFirst6().matches("\\d{6}")) {
            throw new RuntimeException("주민등록번호 앞자리는 숫자 6자리여야 합니다.");
        }
        if (request.getRrnBack7() == null || !request.getRrnBack7().matches("\\d{7}")) {
            throw new RuntimeException("주민등록번호 뒷자리는 숫자 7자리여야 합니다.");
        }
        if (request.getRelationship() == null || request.getRelationship().isBlank()) {
            throw new RuntimeException("관계를 선택해주세요.");
        }
        if ("OTHER".equalsIgnoreCase(request.getRelationship())
                && (request.getCustomRelationship() == null || request.getCustomRelationship().isBlank())) {
            throw new RuntimeException("기타 관계를 입력해주세요.");
        }

        String relationshipCode = request.getRelationship().trim().toUpperCase();
        boolean isValidRelationshipCode = commonCodeService.existsActiveCode("GUARDIAN_RELATIONSHIP", relationshipCode);
        if (!isValidRelationshipCode) {
            throw new RuntimeException("유효하지 않은 관계 코드입니다.");
        }

        String loginId = request.getLoginId().trim();
        if (userRepository.existsByLoginId(loginId)) {
            throw new RuntimeException("이미 사용 중인 로그인 ID입니다.");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        String userEmail = request.getEmail().trim();
        String userTel = request.getPhone().trim();
        User user = new User(loginId, passwordHash, userEmail, userTel);

        User saved = userRepository.saveAndFlush(user);
        guardianBindingService.bindGuardianToChild(saved, request);
        return new SignupResponse(saved.getUserId(), saved.getLoginId(), saved.getStatus());
    }
}
