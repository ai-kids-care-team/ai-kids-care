'use client';

import React from 'react';
import { Provider } from 'react-redux';
import { index } from '@/store';

export function AppProviders({ children }: { children: React.ReactNode }) {
  return <Provider store={index}>{children}</Provider>;
}