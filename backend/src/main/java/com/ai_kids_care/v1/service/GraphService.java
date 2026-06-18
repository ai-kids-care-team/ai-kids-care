package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.repository.GraphRepository;
import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphRepository graphRepository;

    // 此 Service 尚未接入任何 live Controller。
    // 默认拒绝所有调用，防止将来误用绕过授权。
    // 接入时须先通过 SPEC 定义 AuthorizationAction 并替换此注解。
    @PreAuthorize("denyAll()")
    public ChildGraphVO getChildGraph(Long childId) {
        return graphRepository.findChildGraph(childId);
    }
}