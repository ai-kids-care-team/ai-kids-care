package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.dto.UserCreateDTO;
import com.ai_kids_care.v1.dto.UserUpdateDTO;
import com.ai_kids_care.v1.entity.User;
import com.ai_kids_care.v1.mapper.UserMapper;
import com.ai_kids_care.v1.repository.UserRepository;
import com.ai_kids_care.v1.vo.UserVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;

    public Page<UserVO> listUsers(String keyword, Pageable pageable) {
        // TODO: filter User by keyword
        return repository.findAll(pageable).map(mapper::toVO);
    }

    public UserVO getUser(Long id) {
        return repository.findById(id).map(mapper::toVO)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    public UserVO createUser(UserCreateDTO createDTO) {
        return mapper.toVO(repository.save(mapper.toEntity(createDTO)));
    }

    public UserVO updateUser(Long id, UserUpdateDTO updateDTO) {
        User entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("User not found"));
        mapper.updateEntity(updateDTO, entity);
        return mapper.toVO(repository.save(entity));
    }

    public void deleteUser(Long id) {
        User entity = repository.findById(id).
                orElseThrow(() -> new EntityNotFoundException("User not found"));
        repository.delete(entity);
    }
}