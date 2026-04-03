package com.ai_kids_care.v1.service;

import com.ai_kids_care.v1.repository.GraphRepository;
import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final GraphRepository graphRepository;

    public ChildGraphVO getChildGraph(Long childId) {
        return graphRepository.findChildGraph(childId);
    }
}