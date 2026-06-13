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
    level: string | null;
    status: string | null;
  } | null;
  kindergarten: {
    kindergartenId: number;
    name: string;
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

