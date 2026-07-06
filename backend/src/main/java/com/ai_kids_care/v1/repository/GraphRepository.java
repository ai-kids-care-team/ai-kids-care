package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
import com.ai_kids_care.v1.vo.graph.TeacherGraphVO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The only hand-written Cypher repository. Reads exclusively from the Neo4j derived graph (which
 * holds no S0/PII per INC-003) and maps driver node/relationship values straight into VOs; it MUST
 * NOT depend on any JPA repository or otherwise join back to PostgreSQL (that would let PII re-enter
 * via the read path). Tenant isolation is enforced INSIDE the Cypher: every query anchors on
 * {@code {kindergarten_id: $kgId}} (supplied by the service from the ThreadLocal active tenant, never
 * from the request) and constrains the traversed nodes to the same tenant. A miss — non-existent id
 * or an id that exists only in another tenant — raises {@link EntityNotFoundException}, mapped to a
 * 404 by {@code ApiExceptionHandler} so cross-tenant existence is hidden.
 */
@Repository
@RequiredArgsConstructor
public class GraphRepository {

    private final Driver driver;

    public ChildGraphVO findChildGraph(Long childId, Long kindergartenId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {

                Result result = tx.run("""
                    MATCH (ch:Child {child_id: $childId, kindergarten_id: $kgId})
                    OPTIONAL MATCH (c:Class {kindergarten_id: $kgId})-[:HAS_CHILD]->(ch)
                    OPTIONAL MATCH (t:Teacher {kindergarten_id: $kgId})-[:HAS_CLASS]->(c)
                    OPTIONAL MATCH (k:Kindergarten {kindergarten_id: $kgId})-[:HAS_TEACHER]->(t)
                    OPTIONAL MATCH (ch)-[rg:HAS_GUARDIAN]->(g:Guardian {kindergarten_id: $kgId})
                    RETURN ch, c, t, k,
                           collect({
                               guardian: g,
                               relationship: rg.relationship,
                               is_primary: rg.is_primary,
                               priority: rg.priority
                           }) AS guardians
                    """, Values.parameters("childId", childId, "kgId", kindergartenId));

                if (!result.hasNext()) {
                    throw new EntityNotFoundException("Child graph not found");
                }

                Record record = result.single();
                return mapToChildGraphVO(record);
            });
        }
    }

    public TeacherGraphVO findTeacherGraph(Long teacherId, Long kindergartenId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {

                Result result = tx.run("""
                    MATCH (t:Teacher {teacher_id: $teacherId, kindergarten_id: $kgId})
                    OPTIONAL MATCH (k:Kindergarten {kindergarten_id: $kgId})-[:HAS_TEACHER]->(t)
                    OPTIONAL MATCH (t)-[:HAS_CLASS]->(c:Class {kindergarten_id: $kgId})
                    OPTIONAL MATCH (c)-[:HAS_CHILD]->(ch:Child {kindergarten_id: $kgId})
                    WITH t, k, c, collect(ch) AS children
                    RETURN t, k, collect({ class: c, children: children }) AS classes
                    """, Values.parameters("teacherId", teacherId, "kgId", kindergartenId));

                if (!result.hasNext()) {
                    throw new EntityNotFoundException("Teacher graph not found");
                }

                Record record = result.single();
                return mapToTeacherGraphVO(record);
            });
        }
    }

    private ChildGraphVO mapToChildGraphVO(Record record) {
        Node childNode = getNode(record, "ch");
        Node classNode = getNullableNode(record, "c");
        Node teacherNode = getNullableNode(record, "t");
        Node kindergartenNode = getNullableNode(record, "k");

        List<ChildGraphVO.GuardianNodeVO> guardians = mapGuardians(record.get("guardians"));

        return ChildGraphVO.builder()
                .child(mapChildNode(childNode))
                .classInfo(mapClassNode(classNode))
                .teacher(mapTeacherNode(teacherNode))
                .kindergarten(mapKindergartenNode(kindergartenNode))
                .guardians(guardians)
                .build();
    }

    private TeacherGraphVO mapToTeacherGraphVO(Record record) {
        Node teacherNode = getNode(record, "t");
        Node kindergartenNode = getNullableNode(record, "k");

        return TeacherGraphVO.builder()
                .teacher(mapTeacherNode(teacherNode))
                .kindergarten(mapKindergartenNode(kindergartenNode))
                .classes(mapClassesWithChildren(record.get("classes")))
                .build();
    }

    private List<TeacherGraphVO.ClassWithChildrenVO> mapClassesWithChildren(Value classesValue) {
        if (classesValue == null || classesValue.isNull()) {
            return List.of();
        }

        List<TeacherGraphVO.ClassWithChildrenVO> list = new ArrayList<>();

        for (Value item : classesValue.values()) {
            if (item == null || item.isNull()) continue;

            Value classValue = item.get("class");
            // A teacher with no assigned class yields a single {class: null, children: []} entry; skip it.
            if (classValue == null || classValue.isNull()) continue;

            Node classNode = classValue.asNode();

            List<ChildGraphVO.ChildNodeVO> children = new ArrayList<>();
            Value childrenValue = item.get("children");
            if (childrenValue != null && !childrenValue.isNull()) {
                for (Value childValue : childrenValue.values()) {
                    if (childValue == null || childValue.isNull()) continue;
                    children.add(mapChildNode(childValue.asNode()));
                }
            }
            children.sort(Comparator.comparing(
                    ChildGraphVO.ChildNodeVO::getChildId,
                    Comparator.nullsLast(Long::compareTo)));

            list.add(TeacherGraphVO.ClassWithChildrenVO.builder()
                    .classInfo(mapClassNode(classNode))
                    .children(children)
                    .build());
        }

        list.sort(Comparator.comparing(
                c -> c.getClassInfo().getClassId(),
                Comparator.nullsLast(Long::compareTo)));

        return list;
    }

    private ChildGraphVO.ChildNodeVO mapChildNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.ChildNodeVO.builder()
                .childId(getLong(node, "child_id"))
                .name(getString(node, "name"))
                .childNo(getString(node, "child_no"))
                .status(getString(node, "status"))
                .build();
    }

    private ChildGraphVO.ClassNodeVO mapClassNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.ClassNodeVO.builder()
                .classId(getLong(node, "class_id"))
                .name(getString(node, "name"))
                .grade(getString(node, "grade"))
                .academicYear(getInteger(node, "academic_year"))
                .status(getString(node, "status"))
                .build();
    }

    private ChildGraphVO.TeacherNodeVO mapTeacherNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.TeacherNodeVO.builder()
                .teacherId(getLong(node, "teacher_id"))
                .name(getString(node, "name"))
                .level(getString(node, "level"))
                .status(getString(node, "status"))
                .build();
    }

    private ChildGraphVO.KindergartenNodeVO mapKindergartenNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.KindergartenNodeVO.builder()
                .kindergartenId(getLong(node, "kindergarten_id"))
                .name(getString(node, "name"))
                .status(getString(node, "status"))
                .build();
    }

    private List<ChildGraphVO.GuardianNodeVO> mapGuardians(Value guardiansValue) {
        if (guardiansValue == null || guardiansValue.isNull()) {
            return List.of();
        }

        List<ChildGraphVO.GuardianNodeVO> list = new ArrayList<>();

        for (Value item : guardiansValue.values()) {
            if (item == null || item.isNull()) continue;

            Value guardianValue = item.get("guardian");
            if (guardianValue == null || guardianValue.isNull()) continue;

            Node guardianNode = guardianValue.asNode();

            list.add(
                    ChildGraphVO.GuardianNodeVO.builder()
                            .guardianId(getLong(guardianNode, "guardian_id"))
                            .name(getString(guardianNode, "name"))
                            .status(getString(guardianNode, "status"))
                            .relationship(getNullableString(item, "relationship"))
                            .isPrimary(getNullableBoolean(item, "is_primary"))
                            .priority(getNullableInteger(item, "priority"))
                            .build()
            );
        }

        list.sort(Comparator.comparing(
                ChildGraphVO.GuardianNodeVO::getPriority,
                Comparator.nullsLast(Integer::compareTo)
        ));

        return list;
    }

    private Node getNode(Record record, String key) {
        Value value = record.get(key);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("Required node is missing: " + key);
        }
        return value.asNode();
    }

    private Node getNullableNode(Record record, String key) {
        Value value = record.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asNode();
    }

    private String getString(Node node, String key) {
        Value value = node.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private Long getLong(Node node, String key) {
        Value value = node.get(key);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private Integer getInteger(Node node, String key) {
        Value value = node.get(key);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private String getNullableString(Value value, String key) {
        Value v = value.get(key);
        return v == null || v.isNull() ? null : v.asString();
    }

    private Boolean getNullableBoolean(Value value, String key) {
        Value v = value.get(key);
        return v == null || v.isNull() ? null : v.asBoolean();
    }

    private Integer getNullableInteger(Value value, String key) {
        Value v = value.get(key);
        return v == null || v.isNull() ? null : v.asInt();
    }
}