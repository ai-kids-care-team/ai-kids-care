-- V11: Add OTHER to relationship_enum (INT-03a)
-- Allows guardian_relationship to represent catch-all relationships
-- (grandparent, legal guardian, etc.) matching RelationshipEnum.OTHER in Java.
-- PostgreSQL requires ADD VALUE outside a transaction; safe on fresh or migrated DBs.
ALTER TYPE "relationship_enum" ADD VALUE IF NOT EXISTS 'OTHER';
