import { apiClient } from './apiClient';

export type ChildGraph = {
  child: {
    childId: number;
    name: string;
    childNo: string | null;
    gender: string | null;
    status: string | null;
  } | null;
  classInfo: {
    classId: number;
    name: string;
    grade: string | null;
    academicYear: number | null;
    status: string | null;
  } | null;
  teacher: {
    teacherId: number;
    name: string;
    staffNo: string | null;
    level: string | null;
    status: string | null;
  } | null;
  kindergarten: {
    kindergartenId: number;
    name: string;
    address: string | null;
    status: string | null;
  } | null;
  guardians: Array<{
    guardianId: number;
    name: string;
    gender: string | null;
    status: string | null;
    relationship: string | null;
    isPrimary: boolean | null;
    priority: number | null;
  }>;
};

export async function getChildGraph(childId: number): Promise<ChildGraph> {
  if (!Number.isFinite(childId) || childId <= 0) {
    throw new Error('유효하지 않은 childId 입니다.');
  }

  const response = await apiClient.get<ChildGraph>(`/graph/children/${childId}`);
  return response.data;
}

