import { redirect } from 'next/navigation';

/** DB `menu.path` 는 `/cctvCameras` — 실제 페이지는 `/cctvCamera` */
export default function CctvCamerasMenuAliasPage() {
  redirect('/cctvCamera');
}
