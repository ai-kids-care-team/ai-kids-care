import { apiClient } from './apiClient';
import {PageResponse} from "@/services/apis/announcements.api";
import {Child} from "@/types/child";

export async function searchChildrenByName(
    keyword: string,
    page = 0,
    size = 20,
): Promise<PageResponse<Child>> {
    const response = await apiClient.get<PageResponse<Child>>('/children', {
        params: { keyword: keyword.trim(), page, size },
    });
    return response.data;
}