import { NextResponse } from 'next/server';
import { ApiError, apiFetch } from '@/lib/api';

export const dynamic = 'force-dynamic';

type RouteContext = { params: { id: string; action: string } };

async function updateQueueOrder(request: Request, { params }: RouteContext, method: 'PATCH' | 'POST') {
  const { id, action } = params;
  if (action !== 'status' && action !== 'deliver') {
    return NextResponse.json(
      { code: 'NOT_FOUND', message: 'Acción de cocina no encontrada', details: {} },
      { status: 404 },
    );
  }

  let body: string | undefined;
  if (method === 'PATCH') {
    try {
      body = JSON.stringify(await request.json());
    } catch {
      return NextResponse.json(
        { code: 'VALIDATION_ERROR', message: 'El cuerpo de la petición no es válido', details: {} },
        { status: 400 },
      );
    }
  }

  try {
    const result = await apiFetch(`/queue/orders/${encodeURIComponent(id)}/${action}`, {
      method,
      body,
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
      { code: 'INTERNAL_ERROR', message: 'No se pudo actualizar el pedido de cocina' },
      { status: 502 },
    );
  }
}

export async function PATCH(request: Request, context: RouteContext) {
  if (context.params.action !== 'status') {
    return NextResponse.json(
      { code: 'METHOD_NOT_ALLOWED', message: 'Método no permitido para esta acción', details: {} },
      { status: 405 },
    );
  }
  return updateQueueOrder(request, context, 'PATCH');
}

export async function POST(request: Request, context: RouteContext) {
  if (context.params.action !== 'deliver') {
    return NextResponse.json(
      { code: 'METHOD_NOT_ALLOWED', message: 'Método no permitido para esta acción', details: {} },
      { status: 405 },
    );
  }
  return updateQueueOrder(request, context, 'POST');
}
