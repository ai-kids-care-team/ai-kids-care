'use client';

import { useSearchParams } from 'next/navigation';
import { AppreciationLettersListPage } from '@/components/letters/AppreciationLettersListPage';

export function LettersPageClient() {
  const sp = useSearchParams();
  const reloadKey = sp?.get('reload') ?? 'base';

  return <AppreciationLettersListPage key={reloadKey} />;
}

