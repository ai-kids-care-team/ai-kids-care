package com.ai_kids_care.v1.repository;

import com.ai_kids_care.v1.vo.graph.ChildGraphVO;
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
import java.util.NoSuchElementException;

@Repository
@RequiredArgsConstructor
public class GraphRepository {

    private final Driver driver;

    public ChildGraphVO findChildGraph(Long childId) {
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {

                Result result = tx.run("""
                    MATCH (ch:Child {child_id: $childId})
                    OPTIONAL MATCH (c:Class)-[:HAS_CHILD]->(ch)
                    OPTIONAL MATCH (t:Teacher)-[:HAS_CLASS]->(c)
                    OPTIONAL MATCH (k:Kindergarten)-[:HAS_TEACHER]->(t)
                    OPTIONAL MATCH (ch)-[rg:HAS_GUARDIAN]->(g:Guardian)
                    RETURN ch, c, t, k,
                           collect({
                               guardian: g,
                               relationship: rg.relationship,
                               is_primary: rg.is_primary,
                               priority: rg.priority
                           }) AS guardians
                    """, Values.parameters("childId", childId));

                if (!result.hasNext()) {
                    throw new NoSuchElementException("Child graph not found. childId=" + childId);
                }

                Record record = result.single();
                return mapToChildGraphVO(record);
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

    private ChildGraphVO.ChildNodeVO mapChildNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.ChildNodeVO.builder()
                .childId(getLong(node, "child_id"))
                .name(getString(node, "name"))
                .childNo(getString(node, "child_no"))
                .gender(getString(node, "gender"))
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
                .staffNo(getString(node, "staff_no"))
                .level(getString(node, "level"))
                .status(getString(node, "status"))
                .build();
    }

    private ChildGraphVO.KindergartenNodeVO mapKindergartenNode(Node node) {
        if (node == null) return null;

        return ChildGraphVO.KindergartenNodeVO.builder()
                .kindergartenId(getLong(node, "kindergarten_id"))
                .name(getString(node, "name"))
                .address(getString(node, "address"))
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
                            .gender(getString(guardianNode, "gender"))
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