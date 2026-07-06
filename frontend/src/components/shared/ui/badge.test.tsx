import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from './badge';

/**
 * RTL + jsdom 통로 검증용 스모크 테스트. Badge는 부수효과 없는 순수 표시 컴포넌트
 * (span + 클래스 variant, hooks/API 호출 없음) — 테스트 하네스 자체가 React 19 컴포넌트를
 * 렌더링하고 DOM 단언을 할 수 있음을 증명한다.
 */
describe('Badge', () => {
  it('renders its children as text content', () => {
    render(<Badge>ACTIVE</Badge>);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  it('applies the default variant class when no variant is given', () => {
    render(<Badge>default</Badge>);
    expect(screen.getByText('default')).toHaveClass('bg-primary');
  });

  it('applies the destructive variant class when requested', () => {
    render(<Badge variant="destructive">danger</Badge>);
    expect(screen.getByText('danger')).toHaveClass('bg-destructive');
  });
});
