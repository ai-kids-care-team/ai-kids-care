#!/bin/bash

echo "=== Neo4j 데이터 적재 시작 ==="

python no100_insert_users.py
python db100_insert_users.py
python no200_insert_kindergarter.py
python no300_insert_teachers.py
python no400_insert_classes.py
python no500_insert_children.py
python no600_insert_guardians.py
python no700_insert_user_role_assignments.py
python no800_child_guadian_relationships.py
python no900_class_teacher_assignments.py
python no950_child_class_assignments.py
python no1000_create_relationships.py

echo "=== Neo4j 데이터 적재 완료 ==="
