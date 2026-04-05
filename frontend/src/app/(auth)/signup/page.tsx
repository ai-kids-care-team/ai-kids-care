import { SignupForm } from '@/components/auth/SignupForm';

export default function SignupPage() {
  return (
    <div className="flex min-h-screen justify-center bg-gradient-to-b from-emerald-50 to-white px-4 py-8">
      <div className="w-full max-w-4xl origin-top" style={{ zoom: 1 }}>
        <SignupForm />
      </div>
    </div>
  );
}
