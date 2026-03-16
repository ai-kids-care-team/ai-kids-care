'use client';

import { useState } from 'react';

export function useAnnouncementsWrite() {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!title.trim()) {
      setError('제목을 입력해주세요.');
      return;
    }
    if (!content.trim()) {
      setError('내용을 입력해주세요.');
      return;
    }

    // TODO: API 연동 시 공지사항 저장 처리
    alert('공지사항 작성 저장 기능은 연동 예정입니다.');
  };

  return {
    title,
    setTitle,
    content,
    setContent,
    error,
    handleSubmit,
  };
}
