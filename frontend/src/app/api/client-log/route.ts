import { NextResponse } from 'next/server';

type ClientLogPayload = {
  source?: string;
  message?: string;
  detail?: unknown;
};

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as ClientLogPayload;
    const source = body?.source ?? 'unknown';
    const message = body?.message ?? 'client error';
    const detail = body?.detail ?? null;

    console.error(`[CLIENT_LOG] [${source}] ${message}`, detail);
    return NextResponse.json({ ok: true });
  } catch (error) {
    console.error('[CLIENT_LOG] failed to parse log payload', error);
    return NextResponse.json({ ok: false }, { status: 400 });
  }
}
