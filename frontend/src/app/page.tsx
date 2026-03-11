import { redirect } from 'next/navigation';

export default function Home() {
  // 앱 접속 시 기본적으로 로그인 페이지로 보냅니다.
  redirect('/login');
}