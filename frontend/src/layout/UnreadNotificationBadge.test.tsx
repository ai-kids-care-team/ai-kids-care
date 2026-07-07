import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { UnreadNotificationBadge } from './UnreadNotificationBadge';

/**
 * wire-notification-read-state (6.3): ambient 미읽음 배지가 `GET /unread-count` 값을
 * 그대로 반영하는지 검증한다 — 0 이면 숨김(읽음 처리로 카운트가 0에 수렴하면 배지가 사라짐),
 * 양수면 표시, 100 이상이면 "99+" 로 캡한다.
 */
describe('UnreadNotificationBadge', () => {
  it('renders nothing when the unread count is zero', () => {
    const { container } = render(<UnreadNotificationBadge count={0} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders nothing when the unread count is negative (defensive)', () => {
    const { container } = render(<UnreadNotificationBadge count={-3} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the exact count when positive', () => {
    render(<UnreadNotificationBadge count={5} />);
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('caps the displayed count at "99+" once it exceeds 99', () => {
    render(<UnreadNotificationBadge count={150} />);
    expect(screen.getByText('99+')).toBeInTheDocument();
    expect(screen.queryByText('150')).not.toBeInTheDocument();
  });
});
