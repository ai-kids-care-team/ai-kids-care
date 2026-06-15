'use client';

import React from 'react';
import { Provider } from 'react-redux';
import { index } from '@/store';
import { SessionBootstrap } from '@/components/auth/SessionBootstrap';

export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <Provider store={index}>
      <SessionBootstrap />
      {children}
    </Provider>
  );
}
