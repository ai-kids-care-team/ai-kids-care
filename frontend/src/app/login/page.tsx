'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link'; // 추가됨: 회원가입 링크용
import { useAuth } from '@/app/context/AuthContext';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Eye, EyeOff, Shield } from 'lucide-react';

export default function LoginPage() {
  // 첫 번째 코드에 맞춰 username 대신 loginId로 변수명 통일
  const [loginId, setLoginId] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const router = useRouter();
  // 추가됨: isAuthenticated 상태 가져오기
  const { login, isAuthenticated } = useAuth();

  // 추가됨: 이미 로그인된 상태라면 바로 대시보드로 이동
  if (isAuthenticated) {
    router.push('/dashboard');
    return null;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      const success = await login(loginId, password);

      // 반환된 success 값이 true일 때만 대시보드로 이동
      if (success) {
        router.push('/dashboard');
      } else {
        setError('아이디 또는 비밀번호가 올바르지 않습니다.');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인 중 오류가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };
//     try {
//       // 첫 번째 코드의 에러 핸들링 및 실행 방식 적용
//       await login(loginId, password);
//       router.push('/dashboard');
//     } catch (err) {
//       // 추가됨: 더 상세한 에러 메시지 처리
//       setError(err instanceof Error ? err.message : '아이디 또는 비밀번호가 올바르지 않거나 로그인 중 오류가 발생했습니다.');
//     } finally {
//       setIsLoading(false);
//     }
//   };



  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 to-indigo-700 flex items-center justify-center p-4">
      <Card className="w-full max-w-md p-8 bg-white shadow-2xl">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-600 rounded-2xl mb-4">
            <Shield className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">햇살유치원</h1>
          <p className="text-sm text-gray-600">CCTV 통합 관리 시스템</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="loginId" className="block text-sm font-medium text-gray-700 mb-1">
              로그인 ID
            </label>
            <input
              id="loginId"
              type="text"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
              placeholder="아이디를 입력하세요"
              required
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
              비밀번호
            </label>
            <div className="relative">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all pr-10"
                placeholder="비밀번호를 입력하세요"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
              {error}
            </div>
          )}

          <Button
            type="submit"
            className="w-full bg-purple-600 hover:bg-purple-700 text-white py-2.5"
            disabled={isLoading}
          >
            {isLoading ? '로그인 중...' : '로그인'}
          </Button>
        </form>

        {/* 첫 번째 코드에서 가져온 회원가입 링크 */}
        <div className="mt-4 text-center">
          <Link href="/signup" className="text-sm text-purple-600 hover:text-purple-800 font-medium transition-colors">
            아직 계정이 없으신가요? 회원가입 하러가기
          </Link>
        </div>

        <div className="mt-6 pt-6 border-t border-gray-200">
          <p className="text-xs text-gray-500 text-center mb-3">데모 계정</p>
          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="bg-purple-50 p-2 rounded border border-purple-200">
              <p className="font-medium text-purple-900">슈퍼관리자</p>
              <p className="text-purple-700">super / admin123</p>
            </div>
            <div className="bg-indigo-50 p-2 rounded border border-indigo-200">
              <p className="font-medium text-indigo-900">시스템관리자</p>
              <p className="text-indigo-700">system / admin123</p>
            </div>
            <div className="bg-blue-50 p-2 rounded border border-blue-200">
              <p className="font-medium text-blue-900">원장(관리자)</p>
              <p className="text-blue-700">admin / admin123</p>
            </div>
            <div className="bg-green-50 p-2 rounded border border-green-200">
              <p className="font-medium text-green-900">선생님</p>
              <p className="text-green-700">teacher / teacher123</p>
            </div>
            <div className="bg-orange-50 p-2 rounded border border-orange-200 col-span-2">
              <p className="font-medium text-orange-900">학부모</p>
              <p className="text-orange-700">guardian / parent123</p>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}

// 'use client';
//
// import { useState } from 'react';
// import { useRouter } from 'next/navigation';
// import { useAuth } from '@/app/context/AuthContext';
// import { Button } from '../components/ui/button';
// import { Card } from '../components/ui/card';
// import { Eye, EyeOff, Shield } from 'lucide-react';
//
// export default function LoginPage() {
//   const [username, setUsername] = useState('');
//   const [password, setPassword] = useState('');
//   const [showPassword, setShowPassword] = useState(false);
//   const [error, setError] = useState('');
//   const [isLoading, setIsLoading] = useState(false);
//   //const navigate = useNavigate();
//   const router = useRouter();
//   const { login } = useAuth();
//
//   const handleSubmit = async (e: React.FormEvent) => {
//     e.preventDefault();
//     setError('');
//     setIsLoading(true);
//
//     try {
//       const success = await login(username, password);
//       if (success) {
//         //navigate('/dashboard');
//         router.push('/dashboard');
//       } else {
//         setError('아이디 또는 비밀번호가 올바르지 않습니다.');
//       }
//     } catch (err) {
//       setError('로그인 중 오류가 발생했습니다.');
//     } finally {
//       setIsLoading(false);
//     }
//   };
//
//   return (
//       <div className="min-h-screen bg-gradient-to-br from-purple-600 to-indigo-700 flex items-center justify-center p-4">
//         <Card className="w-full max-w-md p-8 bg-white shadow-2xl">
//           <div className="text-center mb-8">
//             <div className="inline-flex items-center justify-center w-16 h-16 bg-purple-600 rounded-2xl mb-4">
//               <Shield className="w-8 h-8 text-white" />
//             </div>
//             <h1 className="text-2xl font-bold text-gray-900 mb-2">햇살유치원</h1>
//             <p className="text-sm text-gray-600">CCTV 통합 관리 시스템</p>
//           </div>
//
//           <form onSubmit={handleSubmit} className="space-y-4">
//             <div>
//               <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1">
//                 아이디
//               </label>
//               <input
//                   id="username"
//                   type="text"
//                   value={username}
//                   onChange={(e) => setUsername(e.target.value)}
//                   className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all"
//                   placeholder="아이디를 입력하세요"
//                   required
//               />
//             </div>
//
//             <div>
//               <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
//                 비밀번호
//               </label>
//               <div className="relative">
//                 <input
//                     id="password"
//                     type={showPassword ? 'text' : 'password'}
//                     value={password}
//                     onChange={(e) => setPassword(e.target.value)}
//                     className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-600 focus:border-transparent outline-none transition-all pr-10"
//                     placeholder="비밀번호를 입력하세요"
//                     required
//                 />
//                 <button
//                     type="button"
//                     onClick={() => setShowPassword(!showPassword)}
//                     className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-700"
//                 >
//                   {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
//                 </button>
//               </div>
//             </div>
//
//             {error && (
//                 <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
//                   {error}
//                 </div>
//             )}
//
//             <Button
//                 type="submit"
//                 className="w-full bg-purple-600 hover:bg-purple-700 text-white py-2.5"
//                 disabled={isLoading}
//             >
//               {isLoading ? '로그인 중...' : '로그인'}
//             </Button>
//           </form>
//
//           <div className="mt-6 pt-6 border-t border-gray-200">
//             <p className="text-xs text-gray-500 text-center mb-3">데모 계정</p>
//             <div className="grid grid-cols-2 gap-2 text-xs">
//               <div className="bg-purple-50 p-2 rounded border border-purple-200">
//                 <p className="font-medium text-purple-900">슈퍼관리자</p>
//                 <p className="text-purple-700">super / admin123</p>
//               </div>
//               <div className="bg-indigo-50 p-2 rounded border border-indigo-200">
//                 <p className="font-medium text-indigo-900">시스템관리자</p>
//                 <p className="text-indigo-700">system / admin123</p>
//               </div>
//               <div className="bg-blue-50 p-2 rounded border border-blue-200">
//                 <p className="font-medium text-blue-900">원장(관리자)</p>
//                 <p className="text-blue-700">admin / admin123</p>
//               </div>
//               <div className="bg-green-50 p-2 rounded border border-green-200">
//                 <p className="font-medium text-green-900">선생님</p>
//                 <p className="text-green-700">teacher / teacher123</p>
//               </div>
//               <div className="bg-orange-50 p-2 rounded border border-orange-200">
//                 <p className="font-medium text-orange-900">학부모</p>
//                 <p className="text-orange-700">guardian / parent123</p>
//               </div>
//             </div>
//           </div>
//         </Card>
//       </div>
//   );
// }
