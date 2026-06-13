import {PageResponse} from "@/services/apis/announcements.api";
import {Child} from "@/types/child";

export async function searchChildrenByName(
    keyword: string,
    page = 0,
    size = 20,
): Promise<PageResponse<Child>> {
    void keyword;
    void page;
    void size;
    throw new Error('Child profile search is unavailable until relationship authorization exists');
}
