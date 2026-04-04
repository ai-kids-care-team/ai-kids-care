// ============================================================
// Child Domain Types (One File)
// ============================================================

// ------------------------------
// Enum Types
// ------------------------------
export type GenderEnum = 'MALE' | 'FEMALE';

export type StatusEnum =
    | 'ACTIVE'
    | 'PENDING'
    | 'DISABLED';

// ------------------------------
// Base Entity
// ------------------------------
export interface Child {
    childId: number;
    kindergartenId: number;

    name: string;
    childNo: string;

    rrnFirst6: string;
    rrnEncrypted: string;

    birthDate: string;       // YYYY-MM-DD
    gender: GenderEnum;

    address: string;

    enrollDate: string;
    leaveDate?: string | null;

    status: StatusEnum;

    createdAt: string;       // ISO String
    updatedAt: string;
}

// ------------------------------
// Create Request DTO
// ------------------------------
export interface ChildCreateRequest {
    kindergartenId: number;

    name: string;
    childNo: string;

    rrnFirst6: string;
    rrnEncrypted: string;

    birthDate: string;
    gender: GenderEnum;

    address: string;

    enrollDate: string;
    leaveDate?: string | null;

    status: StatusEnum;
}

// ------------------------------
// Update Request DTO
// ------------------------------
export interface ChildUpdateRequest {
    name?: string;
    childNo?: string;

    birthDate?: string;
    gender?: GenderEnum;

    address?: string;

    enrollDate?: string;
    leaveDate?: string | null;

    status?: StatusEnum;
}

// ------------------------------
// Pagination (Spring Page 대응)
// ------------------------------
export interface PageResponse<T> {
    content: T[];

    totalElements: number;
    totalPages: number;
    size: number;
    number: number;

    first: boolean;
    last: boolean;
}

// ------------------------------
// API Response Types
// ------------------------------
export type ChildListResponse = PageResponse<Child>;
export type ChildDetailResponse = Child;