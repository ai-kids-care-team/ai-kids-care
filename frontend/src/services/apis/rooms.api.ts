import { apiClient } from './apiClient';
import type { PageResponse } from './appreciationLetters.api';

/**
 * 백엔드 `RoomVO`(`GET/POST/PUT/DELETE /api/v1/rooms`) 와 1:1.
 * `classes.api.ts` 와 대칭 — kindergartenId 는 응답 표시용일 뿐 요청에는 싣지 않는다.
 */
export type RoomVO = {
  roomId: number;
  kindergartenId: number;
  name: string;
  roomCode: string | null;
  locationNote: string | null;
  roomType: string;
  status: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

/** 백엔드 `RoomCreateDTO` — `status` 는 Class 와 달리 선택값(서버 `@NotNull` 없음). */
export type RoomCreatePayload = {
  name: string;
  roomCode?: string | null;
  locationNote?: string | null;
  roomType: string;
  status?: string | null;
};

/** 백엔드 `RoomUpdateDTO` — `name`/`roomType` 은 update 에서도 여전히 `@NotBlank` 필수. */
export type RoomUpdatePayload = {
  name: string;
  roomCode?: string | null;
  locationNote?: string | null;
  roomType: string;
  status?: string | null;
};

export type GetRoomsParams = {
  keyword?: string;
  page?: number;
  size?: number;
  sort?: string | string[];
};

/** `GET /api/v1/rooms?keyword=&page=&size=` → `Page<RoomVO>` */
export async function getRooms(params?: GetRoomsParams): Promise<PageResponse<RoomVO>> {
  const page = params?.page ?? 0;
  const size = params?.size ?? 20;
  const keyword = params?.keyword?.trim();
  const sort = params?.sort;
  const response = await apiClient.get<PageResponse<RoomVO>>('/rooms', {
    params: {
      page,
      size,
      ...(keyword ? { keyword } : {}),
      ...(sort ? { sort } : {}),
    },
  });
  return response.data;
}

/** `GET /api/v1/rooms/{id}` */
export async function getRoom(id: number): Promise<RoomVO> {
  const response = await apiClient.get<RoomVO>(`/rooms/${id}`);
  return response.data;
}

/** `POST /api/v1/rooms` */
export async function createRoom(payload: RoomCreatePayload): Promise<RoomVO> {
  const response = await apiClient.post<RoomVO>('/rooms', payload);
  return response.data;
}

/** `PUT /api/v1/rooms/{id}` */
export async function updateRoom(id: number, payload: RoomUpdatePayload): Promise<RoomVO> {
  const response = await apiClient.put<RoomVO>(`/rooms/${id}`, payload);
  return response.data;
}

/** `DELETE /api/v1/rooms/{id}` — 204 No Content */
export async function deleteRoom(id: number): Promise<void> {
  await apiClient.delete(`/rooms/${id}`);
}
