import { NextResponse } from 'next/server';
import { ApiError, apiFetch } from '@/lib/api';
import type { OrderStatusResponse } from '@/lib/types';

export const dynamic = 'force-dynamic';

export async function GET(
  _request: Request,
  { params }: { params: { id: string } },
) {
  try {
    const status = await apiFetch<OrderStatusResponse>(`/orders/${params.id}/status`);
    return NextResponse.json(status);
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json(
        { code: error.code, message: error.message, details: error.details ?? {} },
        { status: error.status },
      );
    }
    return NextResponse.json(
      { code: 'INTERNAL_ERROR', message: 'No se pudo consultar el pedido' },
      { status: 502 },
    );
  }
}
