//TEST 중

export type AnnouncementItem = {
    id: number;
    title: string;
    date: string;
    isNew: boolean;
    views: number;
    href: string;
};


export type StatusCode = 'ACTIVE' | 'PENDING' | 'DISABLED';