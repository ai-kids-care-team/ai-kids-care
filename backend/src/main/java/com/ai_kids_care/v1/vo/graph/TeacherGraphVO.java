package com.ai_kids_care.v1.vo.graph;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Teacher-centric relationship-graph projection: a teacher, its kindergarten, and the classes the
 * teacher is assigned to with the children in each class
 * ({@code (Teacher)-[:HAS_CLASS]->(Class)-[:HAS_CHILD]->(Child)}).
 *
 * <p>Like {@link ChildGraphVO}, every field is mapped <strong>exclusively</strong> from Neo4j driver
 * node/relationship values returned by {@code GraphRepository}; the mapping never joins back to
 * PostgreSQL (INC-003 extended to the read path — no PII may re-enter). Node shapes are reused from
 * {@link ChildGraphVO} so the two query paths share the same non-PII projection.
 */
@Getter
@Builder
public class TeacherGraphVO {

    private ChildGraphVO.TeacherNodeVO teacher;
    private ChildGraphVO.KindergartenNodeVO kindergarten;
    private List<ClassWithChildrenVO> classes;

    @Getter
    @Builder
    public static class ClassWithChildrenVO {
        private ChildGraphVO.ClassNodeVO classInfo;
        private List<ChildGraphVO.ChildNodeVO> children;
    }
}
