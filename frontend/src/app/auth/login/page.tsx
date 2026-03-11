import { LoginForm } from '@/features/auth';

export default function LoginPage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-purple-600 to-indigo-700 flex items-center justify-center p-4">
      <LoginForm />
    </div>
  );
}