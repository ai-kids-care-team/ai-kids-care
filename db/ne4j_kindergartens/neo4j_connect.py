"""공유 Neo4j 드라이버. no000_scrub_sensitive.py 가 import 하여 사용한다.
자격증명/URI 는 config.py(환경변수 주입) 단일 출처에서 가져온다."""

from neo4j import GraphDatabase

from config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD

driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
