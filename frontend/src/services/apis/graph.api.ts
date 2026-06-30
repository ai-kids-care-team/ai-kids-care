import { baseApi } from '@/services/apis/base.api';

/** Non-PII graph node shapes — mirror backend ChildGraphVO / TeacherGraphVO (Neo4j-projected only). */
export type ChildNode = {
  childId: number;
  name: string;
  childNo: string | null;
  gender: string | null;
  status: string | null;
};

export type ClassNode = {
  classId: number;
  name: string;
  grade: string | null;
  academicYear: number | null;
  status: string | null;
};

export type TeacherNode = {
  teacherId: number;
  name: string;
  level: string | null;
  status: string | null;
};

export type KindergartenNode = {
  kindergartenId: number;
  name: string;
  status: string | null;
};

export type GuardianNode = {
  guardianId: number;
  name: string;
  gender: string | null;
  status: string | null;
  relationship: string | null;
  isPrimary: boolean | null;
  priority: number | null;
};

/** Backend: ChildGraphVO — GET /api/v1/graph/children/{childId}. */
export type ChildGraph = {
  child: ChildNode | null;
  classInfo: ClassNode | null;
  teacher: TeacherNode | null;
  kindergarten: KindergartenNode | null;
  guardians: GuardianNode[];
};

/** Backend: TeacherGraphVO — GET /api/v1/graph/teachers/{teacherId}. */
export type TeacherGraph = {
  teacher: TeacherNode | null;
  kindergarten: KindergartenNode | null;
  classes: Array<{
    classInfo: ClassNode | null;
    children: ChildNode[];
  }>;
};

/**
 * Relationship-graph read endpoints over the Neo4j derived graph. Tenant scope is enforced by the
 * backend from the session's active kindergarten (GRAPH_READ) — the client never sends a
 * kindergartenId. A child/teacher absent for the caller's tenant yields 404 (existence hidden).
 * Injected through {@code baseApi} so session cookie + CSRF posture apply.
 */
export const graphApi = baseApi.injectEndpoints({
  endpoints: (build) => ({
    getChildGraph: build.query<ChildGraph, number>({
      query: (childId) => `/graph/children/${childId}`,
    }),
    getTeacherGraph: build.query<TeacherGraph, number>({
      query: (teacherId) => `/graph/teachers/${teacherId}`,
    }),
  }),
  overrideExisting: false,
});

export const { useGetChildGraphQuery, useGetTeacherGraphQuery } = graphApi;
