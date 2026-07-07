package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.mapper.UserMapper;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.vo.UserVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;

    @PreAuthorize("denyAll()")
    public Page<UserVO> listUsers(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toVO);
    }

    @PreAuthorize("denyAll()")
    public UserVO getUser(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
