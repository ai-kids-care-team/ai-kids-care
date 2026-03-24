package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.KindergartenCreateDTO;
import com.ai_kids_care.v1.dto.KindergartenUpdateDTO;
import com.ai_kids_care.v1.entity.Kindergarten;
import com.ai_kids_care.v1.mapper.KindergartenMapper;
import com.ai_kids_care.v1.repository.KindergartenRepository;
import com.ai_kids_care.v1.vo.KindergartenVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KindergartenService {

    private final KindergartenRepository repository;
    private final KindergartenMapper mapper;

    public Page<KindergartenVO> listKindergartens(String keyword, Pageable pageable) {
        return repository.findByNameContains(keyword, pageable).map(mapper::toVO);
    }

    public KindergartenVO getKindergarten(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("Kindergarten not found"));
    }

    public KindergartenVO createKindergarten(KindergartenCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public KindergartenVO updateKindergarten(Long id, KindergartenUpdateDTO updateDTO) {
        Kindergarten entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Kindergarten not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteKindergarten(Long id) {
        Kindergarten entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("Kindergarten not found"));
        repository.delete(entity);
    }

    /**
     * 회원가입용: 사업자등록번호(숫자 10자리)로 유치원 조회.
     *
     * @param raw 하이픈 포함 입력 가능
     */
    public List<KindergartenVO> searchForSignupByBusinessRegistrationNo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 10) {
            return List.of();
        }
        return repository.findByBusinessRegistrationDigits(digits).stream()
                .map(mapper::toVO)
                .toList();
    }
}