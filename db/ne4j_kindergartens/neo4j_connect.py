from neo4j import GraphDatabase
import os

driver = GraphDatabase.driver(
    "bolt://neo4j:7687",
    auth=(os.getenv("NEO4J_USER", "neo4j"), os.getenv("NEO4J_PASSWORD", "rose100!"))
)

with driver.session() as session:
    result = session.run("RETURN 'connected' AS msg")
    print(result.single()["msg"])
