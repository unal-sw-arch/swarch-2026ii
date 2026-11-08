using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ReportingService.Models;
using ReportingService.Services;

namespace ReportingService.Controllers;

[ApiController]
[Route("reports")]
[Authorize(Policy = "ReportsRead")]
public sealed class ReportsController : ControllerBase
{
    private readonly ReportService _reports;
    private readonly ExportService _export;

    public ReportsController(ReportService reports, ExportService export)
    {
        _reports = reports;
        _export = export;
    }

    /// <summary>Ventas por día y productos más vendidos. Rango [from, to] inclusivo (por defecto, 30 días).</summary>
    [HttpGet("sales")]
    public Task<SalesReport> Sales(
        [FromQuery(Name = "from")] DateOnly? from, [FromQuery(Name = "to")] DateOnly? to, CancellationToken ct) =>
        _reports.SalesAsync(from, to, ct);

    /// <summary>Tiempos promedio de preparación y entrega (desde los eventos de estado de cocina).</summary>
    [HttpGet("times")]
    public Task<TimesReport> Times(
        [FromQuery(Name = "from")] DateOnly? from, [FromQuery(Name = "to")] DateOnly? to, CancellationToken ct) =>
        _reports.TimesAsync(from, to, ct);

    /// <summary>Exporta `sales`, `times` o `history` en `csv` o `pdf` a MinIO y devuelve una URL firmada.</summary>
    [HttpPost("export")]
    public Task<ExportResult> Export([FromBody] ExportRequest request, CancellationToken ct) =>
        _export.ExportAsync(request, ct);
}
