package com.ai_kids_care.v1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai_kids_care.v1.dto.ChildCreateDTO;
import com.ai_kids_care.v1.dto.ChildUpdateDTO;
import com.ai_kids_care.v1.entity.Child;
import com.ai_kids_care.v1.mapper.ChildMapper;
import com.ai_kids_care.v1.repository.ChildRepository;
import com.ai_kids_care.v1.security.JwtUtil;
import com.ai_kids_care.v1.vo.ChildVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChildrenService {

    private final ChildRepository repository;
//    private final ChildRepository childRepository;
    private final ChildMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final Pattern RRN_KEYWORD_PATTERN = Pattern.compile("^(\\d{6})-(\\d{7})$");
    private static final Pattern RRN_COMPACT_PATTERN = Pattern.compile("^(\\d{6})(\\d{7})$");
    private static final ObjectMapper JSON = new ObjectMapper();

    public Page<ChildVO> listChildren(String keyword, Integer page, Integer size, String sort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 20 : size;
        String safeSort = (sort == null || sort.isBlank()) ? "createdAt" : sort;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, safeSort));

        if (keyword == null || keyword.isBlank()) {
            return repository.findAll(pageable).map(mapper::toVO);
        }

        String trimmed = keyword.trim();
        String[] rrnParts = parseRrnSearchKeyword(trimmed);
        if (rrnParts != null) {
            return listChildrenByRrnParts(rrnParts[0], rrnParts[1], pageable);
        }

        return repository.findByNameContains(trimmed, pageable).map(mapper::toVO);
    }

    private static String[] parseRrnSearchKeyword(String trimmed) {
        Matcher hyphen = RRN_KEYWORD_PATTERN.matcher(trimmed);
        if (hyphen.matches()) {
            return new String[]{hyphen.group(1), hyphen.group(2)};
        }
        String digitsOnly = trimmed.replaceAll("\\D", "");
        Matcher compact = RRN_COMPACT_PATTERN.matcher(digitsOnly);
        if (compact.matches()) {
            return new String[]{compact.group(1), compact.group(2)};
        }
        return null;
    }

    public Page<ChildVO> listChildrenByRrnParts(String rrnFirst6, String rrnBack7, Pageable pageable) {
        List<Child> candidates = repository.findByRrnFirst6(rrnFirst6);
        List<ChildVO> matched = candidates.stream()
                .filter(child -> isRrnBack7Matched(child.getRrnEncrypted(), rrnBack7))
                .map(mapper::toVO)
                .toList();
        return new PageImpl<>(matched, pageable, matched.size());
    }

    /** 회원가입(보호자) 등에서 아이 주민 앞6·뒤7로 단일 원아 조회 (JWT/BCrypt 저장 모두 지원). */
    public Optional<Child> findChildByRrnParts(String rrnFirst6, String rrnBack7) {
        if (rrnFirst6 == null || rrnFirst6.isBlank() || rrnBack7 == null || rrnBack7.isBlank()) {
            return Optional.empty();
        }
        String f6 = rrnFirst6.trim();
        String b7 = rrnBack7.trim();
        return repository.findByRrnFirst6(f6).stream()
                .filter(child -> isRrnBack7Matched(child.getRrnEncrypted(), b7))
                .findFirst();
    }

    private boolean isRrnBack7Matched(String rrnEncrypted, String rrnBack7) {
        if (rrnEncrypted == null || rrnEncrypted.isBlank()) {
            return false;
        }

        // Legacy bcrypt data
        if (looksLikeBcryptHash(rrnEncrypted)) {
            try {
                if (passwordEncoder.matches(rrnBack7, rrnEncrypted)) {
                    return true;
                }
            } catch (Exception ignored) {
                // continue with JWT matching
            }
        }

        // JWT: 서명 검증이 성공하면 subject 비교
        try {
            String subject = jwtUtil.extractIdentifier(rrnEncrypted);
            if (rrnBack7.equals(subject)) {
                return true;
            }
        } catch (Exception ignored) {
            // 시드 JWT와 현재 서버 secret이 다를 수 있어 fallback 수행
        }

        String subUnverified = jwtPayloadSubWithoutVerify(rrnEncrypted);
        return rrnBack7.equals(subUnverified);
    }

    private static boolean looksLikeBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private static String jwtPayloadSubWithoutVerify(String compactToken) {
        if (compactToken == null || compactToken.isBlank()) {
            return null;
        }
        String[] segments = compactToken.split("\\.");
        if (segments.length != 3) {
            return null;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(padBase64Url(segments[1]));
            JsonNode root = JSON.readTree(payload);
            JsonNode sub = root.get("sub");
            return sub != null && sub.isTextual() ? sub.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String padBase64Url(String segment) {
        int missing = (4 - segment.length() % 4) % 4;
        return segment + "=".repeat(missing);
    }

    public ChildVO getChildren(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Children not found"));
    }

    public ChildVO createChildren(ChildCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public ChildVO updateChildren(Long id, ChildUpdateDTO updateDTO) {
        Child entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Children not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteChildren(Long id) {
        Child entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Children not found"));
        repository.delete(entity);
    }
}