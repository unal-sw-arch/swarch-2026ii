using System.Globalization;
using System.Text;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;
using ReportingService.Models;
using ReportingService.Repositories;

namespace ReportingService.Services;

/// <summary>Genera CSV o PDF de un reporte, lo guarda en MinIO (`reports`) y devuelve una URL firmada.</summary>
public sealed class ExportService
{
    private static readonly string[] Reports = { "sales", "times", "history" };
    private static readonly string[] Formats = { "csv", "pdf" };

    private readonly ReportService _reports;
    private readonly IHistoryRepository _history;
    private readonly IObjectStorage _storage;
    private readonly int _maxRows;

    public ExportService(ReportService reports, IHistoryRepository history, IObjectStorage storage, IConfiguration config)
    {
        _reports = reports;
        _history = history;
        _storage = storage;
        _maxRows = int.TryParse(config["Reporting:MaxExportRows"], out var r) ? r : 5000;
        QuestPDF.Settings.License = LicenseType.Community;
    }

    public async Task<ExportResult> ExportAsync(ExportRequest request, CancellationToken ct)
    {
        var report = request.Report?.Trim().ToLowerInvariant() ?? "";
        var format = request.Format?.Trim().ToLowerInvariant() ?? "";
        if (!Reports.Contains(report))
            throw ApiException.Invalid("report debe ser 'sales', 'times' o 'history'", new { allowed = Reports });
        if (!Formats.Contains(format))
            throw ApiException.Invalid("format debe ser 'csv' o 'pdf'", new { allowed = Formats });

        var range = _reports.Range(request.From, request.To);
        var (title, headers, rows) = await BuildTableAsync(report, range, request, ct);

        var (bytes, contentType) = format == "csv"
            ? (Encoding.UTF8.GetBytes(BuildCsv(headers, rows)), "text/csv; charset=utf-8")
            : (BuildPdf(title, headers, rows), "application/pdf");

        var name = $"exports/{DateTime.UtcNow:yyyyMMdd}/{report}-{range.From:yyyyMMdd}-{range.To:yyyyMMdd}-{Guid.NewGuid():N}.{format}";
        return await _storage.SaveAsync(name, bytes, contentType, format, ct);
    }

    private async Task<(string Title, string[] Headers, List<string[]> Rows)> BuildTableAsync(
        string report, DateRange range, ExportRequest request, CancellationToken ct)
    {
        var inv = CultureInfo.InvariantCulture;
        switch (report)
        {
            case "sales":
            {
                var s = await _reports.SalesAsync(request.From, request.To, ct);
                var rows = s.ByDay.Select(d => new[] { d.Day.ToString("yyyy-MM-dd"), d.Orders.ToString(inv), d.Revenue.ToString("0.00", inv) }).ToList();
                rows.Add(new[] { "TOTAL", s.OrdersCount.ToString(inv), s.Revenue.ToString("0.00", inv) });
                return ($"Ventas {range.From:yyyy-MM-dd} a {range.To:yyyy-MM-dd}", new[] { "fecha", "pedidos", "ingresos" }, rows);
            }
            case "times":
            {
                var t = await _reports.TimesAsync(request.From, request.To, ct);
                string F(double? v) => v?.ToString("0.00", inv) ?? "";
                var rows = new List<string[]>
                {
                    new[] { "pedidos_medidos", t.OrdersMeasured.ToString(inv) },
                    new[] { "preparacion_promedio_min", F(t.AvgPrepMinutes) },
                    new[] { "entrega_promedio_min", F(t.AvgDeliveryMinutes) },
                    new[] { "total_promedio_min", F(t.AvgTotalMinutes) },
                };
                return ($"Tiempos {range.From:yyyy-MM-dd} a {range.To:yyyy-MM-dd}", new[] { "metrica", "valor" }, rows);
            }
            default: // history
            {
                var filter = new HistoryFilter(range.FromUtc, range.ToUtcExclusive, null, null, 0, _maxRows);
                var (items, _) = await _history.QueryAsync(filter, ct);
                var rows = items.Select(o => new[]
                {
                    o.OrderId, SafeText(o.CustomerId ?? ""), o.Status, o.OrderType ?? "",
                    o.Total.ToString("0.00", inv), o.PaidAt?.ToString("o", inv) ?? "", o.ReadyAt?.ToString("o", inv) ?? "",
                }).ToList();
                return ($"Historial {range.From:yyyy-MM-dd} a {range.To:yyyy-MM-dd}",
                    new[] { "pedido", "cliente", "estado", "tipo", "total", "pagado", "listo" }, rows);
            }
        }
    }

    /// <summary>Evita inyección de fórmulas al abrir el CSV en una hoja de cálculo.</summary>
    public static string SafeText(string value) =>
        value.Length > 0 && "=+-@\t\r".Contains(value[0]) ? "'" + value : value;

    public static string BuildCsv(IEnumerable<string> headers, IEnumerable<IEnumerable<string>> rows)
    {
        static string Cell(string v) =>
            v.IndexOfAny(new[] { ',', '"', '\n', '\r' }) >= 0 ? "\"" + v.Replace("\"", "\"\"") + "\"" : v;
        var sb = new StringBuilder();
        sb.Append(string.Join(",", headers.Select(Cell))).Append("\r\n");
        foreach (var row in rows) sb.Append(string.Join(",", row.Select(Cell))).Append("\r\n");
        return sb.ToString();
    }

    private static byte[] BuildPdf(string title, string[] headers, List<string[]> rows) =>
        Document.Create(container => container.Page(page =>
        {
            page.Size(PageSizes.A4);
            page.Margin(30);
            page.DefaultTextStyle(x => x.FontSize(9));
            page.Header().Text($"Panadería Express — {title}").FontSize(14).Bold();
            page.Content().PaddingVertical(10).Table(table =>
            {
                table.ColumnsDefinition(cols => { foreach (var _ in headers) cols.RelativeColumn(); });
                table.Header(h => { foreach (var header in headers) h.Cell().Text(header).Bold(); });
                foreach (var row in rows)
                    foreach (var cell in row)
                        table.Cell().Text(cell);
            });
            page.Footer().AlignCenter().Text(x => { x.Span("Página "); x.CurrentPageNumber(); });
        })).GeneratePdf();
}
