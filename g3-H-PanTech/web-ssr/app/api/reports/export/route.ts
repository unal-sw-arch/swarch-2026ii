import { NextResponse } from 'next/server';
import { ApiError, apiFetch } from '@/lib/api';
import type { ExportResult } from '@/lib/types';

export const dynamic = 'force-dynamic';

export async function POST(request: Request) {
  let payload: unknown;
  try {
    payload = await request.json();
  } catch {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'El cuerpo de la petición no es válido' },
      { status: 400 },
    );
  }

  try {
    const result = await apiFetch<ExportResult>('/reports/export', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return NextResponse.json(result);
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json(
        { code: error.code, message: error.message, details: error.details ?? {} },
        { status: error.status },
      );
    }
    return NextResponse.json(
      { code: 'INTERNAL_ERROR', message: 'No se pudo generar la exportación' },
      { status: 502 },
    );
  }
}
