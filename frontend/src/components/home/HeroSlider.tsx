'use client';

import { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

const heroTitleStyle = {
  fontFamily: '"Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans KR", "Segoe UI", sans-serif',
} satisfies React.CSSProperties;

const slides = [
  {
    id: 1,
    image: '/home/slide-1.jpg',
    title: '안심 유치원',
    subtitle: 'AI Kids Care 시스템과 함께 합니다.',
  },
  {
    id: 2,
    image: '/home/slide-2.jpg',
    title: '아이 안심 돌봄',
    subtitle: '신청대상 : 양육자, 유치원, 행정청 누구나 회원가입',
  },
  {
    id: 3,
    image: '/home/slide-3.jpg',
    title: '유치원 선생님에게',
    subtitle: '감사편지 작성',
  },
  {
    id: 4,
    image: '/home/slide-4.jpg',
    title: '함께 성장하는 우리 아이',
    subtitle: 'AI Kids Care가 함께합니다',
  },
];

export function HeroSlider() {
  const [currentSlide, setCurrentSlide] = useState(0);
  const intervalRef = useRef<number | null>(null);

  const nextSlide = () => {
    setCurrentSlide((prev) => (prev + 1) % slides.length);
  };

  const prevSlide = () => {
    setCurrentSlide((prev) => (prev - 1 + slides.length) % slides.length);
  };

  const goToSlide = (index: number) => {
    setCurrentSlide(index);
  };

  useEffect(() => {
    intervalRef.current = window.setInterval(() => {
      nextSlide();
    }, 2000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const handleManualChange = (callback: () => void) => {
    
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }
    callback();
    intervalRef.current = window.setInterval(() => {
      nextSlide();
    }, 2000);
  };

  return (
    <div className="group relative h-full overflow-hidden rounded-[clamp(1.25rem,1.8vw,1.75rem)] shadow-2xl">
      <div className="relative h-full min-h-[clamp(28rem,54vh,50rem)]">
        {slides.map((slide, index) => (
          <div
            key={slide.id}
            className={`absolute inset-0 transition-opacity duration-700 ${
              index === currentSlide ? 'opacity-100 z-10' : 'opacity-0 z-0'
            }`}
          >
            <img src={slide.image} alt={slide.title} className="w-full h-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/20 to-transparent" />

            <div
              className="absolute inset-0 flex flex-col items-center justify-center text-center px-4"
              style={heroTitleStyle}
            >
              <h2
                className="mb-3 max-w-[min(100%,42rem)] text-3xl font-bold leading-[1.2] tracking-tight text-white antialiased animate-in fade-in slide-in-from-bottom-4 duration-700 md:mb-4 md:text-5xl lg:text-6xl [text-shadow:0_1px_2px_rgb(0_0_0_/_0.65),0_8px_32px_rgb(0_0_0_/_0.45)]"
              >
                {slide.title}
              </h2>
              <p
                className="max-w-[min(100%,36rem)] text-base font-semibold leading-snug tracking-tight text-white/95 antialiased animate-in fade-in slide-in-from-bottom-6 duration-700 delay-100 md:text-xl lg:text-2xl [text-shadow:0_1px_3px_rgb(0_0_0_/_0.7),0_4px_20px_rgb(0_0_0_/_0.35)]"
              >
                {slide.subtitle}
              </p>
            </div>
          </div>
        ))}
      </div>

      <button
        onClick={() => handleManualChange(prevSlide)}
        className="absolute left-4 top-1/2 -translate-y-1/2 z-20 p-3 bg-white/80 hover:bg-white rounded-full shadow-lg transition-all opacity-0 group-hover:opacity-100"
        aria-label="이전 슬라이드"
      >
        <ChevronLeft className="w-6 h-6 text-gray-800" />
      </button>

      <button
        onClick={() => handleManualChange(nextSlide)}
        className="absolute right-4 top-1/2 -translate-y-1/2 z-20 p-3 bg-white/80 hover:bg-white rounded-full shadow-lg transition-all opacity-0 group-hover:opacity-100"
        aria-label="다음 슬라이드"
      >
        <ChevronRight className="w-6 h-6 text-gray-800" />
      </button>

      <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-20 flex gap-3">
        {slides.map((_, index) => (
          <button
            key={index}
            onClick={() => handleManualChange(() => goToSlide(index))}
            className={`transition-all ${
              index === currentSlide ? 'w-8 h-3 bg-white' : 'w-3 h-3 bg-white/50 hover:bg-white/80'
            } rounded-full`}
            aria-label={`슬라이드 ${index + 1}로 이동`}
          />
        ))}
      </div>
    </div>
  );
}
