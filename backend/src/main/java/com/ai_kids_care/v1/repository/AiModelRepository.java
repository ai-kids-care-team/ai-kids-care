package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.entity.AiModel;
import com.ai_kids_care.v1.type.StatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Headless list read (no frontend consumer; {@code PLATFORM_METADATA_READ}, platform-scoped, not
     * tenant-scoped — {@code ai_models} carries no {@code kindergarten_id}). Blank/absent
     * {@code keyword} is a no-op (mirrors {@link #findAll(Pageable)}). Matches only {@code name}: the
     * design's "name/description" wording assumed a description column that does not exist on
     * {@link AiModel} / the {@code ai_models} table (see {@code db/dbml/schema.dbml}); adding one would
     * be a schema change, out of scope here (tracked as a note for the maintainer, not implemented).
     */
    @Query("""
            select m from AiModel m
            where (:keyword is null or :keyword = ''
                   or lower(m.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<AiModel> searchAll(@Param("keyword") String keyword, Pageable pageable);
}
