import { NextResponse } from 'next/server';
import { ApiError, apiFetch } from '@/lib/api';
import type { QueueResponse } from '@/lib/types';

export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const queue = await apiFetch<QueueResponse>('/queue');
    return NextResponse.json(queue);
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json(
        { code: error.code, message: error.message, details: error.details ?? {} },
        { status: error.status },
      );
    }
    return NextResponse.json(
      { code: 'INTERNAL_ERROR', message: 'No se pudo consultar la cola de cocina' },
      { status: 502 },
    );
  }
}
