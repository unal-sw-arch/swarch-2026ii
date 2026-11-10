'use client';

import { useState, type FormEvent } from 'react';
import type { ExportResult } from '@/lib/types';

type Props = {
  defaultFrom: string;
  defaultTo: string;
};

export default function ExportReportForm({ defaultFrom, defaultTo }: Props) {
  const [result, setResult] = useState<ExportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    const data = new FormData(event.currentTarget);
    try {
      const response = await fetch('/api/reports/export', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          report: String(data.get('report') ?? 'sales'),
          format: String(data.get('format') ?? 'csv'),
          from: String(data.get('from') ?? '') || null,
          to: String(data.get('to') ?? '') || null,
        }),
      });
      const body = (await response.json().catch(() => null)) as
        | (ExportResult & { message?: string })
        | null;
      if (!response.ok || !body?.url) {
        setError(body?.message ?? 'No se pudo generar la exportación.');
        return;
      }
      setResult(body);
    } catch {
      setError('No se pudo contactar al servidor.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label>
        Reporte
        <select name="report" defaultValue="sales">
          <option value="sales">Ventas</option>
          <option value="times">Tiempos</option>
          <option value="history">Historial</option>
        </select>
      </label>
      <label>
        Formato
        <select name="format" defaultValue="csv">
          <option value="csv">CSV</option>
          <option value="pdf">PDF</option>
        </select>
      </label>
      <label>
        Desde
        <input type="date" name="from" defaultValue={defaultFrom} />
      </label>
      <label>
        Hasta
        <input type="date" name="to" defaultValue={defaultTo} />
      </label>
      <button className="button button-secondary" type="submit" disabled={loading}>
        {loading ? 'Generando…' : 'Exportar'}
      </button>
      {error ? <p className="form-error">{error}</p> : null}
      {result ? (
        <p className="form-ok">
          Exportación lista: <a href={result.url}>descargar {result.format.toUpperCase()}</a> (
          {result.bytes} bytes)
        </p>
      ) : null}
    </form>
  );
}
