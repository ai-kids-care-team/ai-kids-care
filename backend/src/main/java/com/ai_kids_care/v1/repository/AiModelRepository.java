package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.AiModel;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    /**
     * AI 模型 id（按状态过滤，id 升序）。供内部「活跃流清单」端点为每路流附上一个可用 {@code modelId}
     * （Open Question 4：当前无 per-stream 模型映射，V1 取平台活跃模型 {@code StatusEnum.ACTIVE}）。
     */
    @Query("select m.id from AiModel m where m.status = :status order by m.id asc")
    List<Long> findModelIdsByStatusOrderById(@Param("status") StatusEnum status);
}
