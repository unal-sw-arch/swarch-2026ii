using ReportingService.Models;
using ReportingService.Repositories;

namespace ReportingService.Services;

public sealed class ReportService
{
    private readonly IOrdersReadRepository _orders;
    private readonly TimeZoneInfo _tz;
    private readonly string _tzId;
    private readonly int _maxDays;

    public ReportService(IOrdersReadRepository orders, IConfiguration config)
    {
        _orders = orders;
        _tzId = config["Reporting:BusinessTimeZone"] ?? "America/Bogota";
        _tz = TimeZoneInfo.FindSystemTimeZoneById(_tzId);
        _maxDays = int.TryParse(config["Reporting:MaxRangeDays"], out var d) ? d : 366;
    }

    public DateRange Range(DateOnly? from, DateOnly? to) => DateRange.Resolve(from, to, _maxDays, _tz);

    public Task<SalesReport> SalesAsync(DateOnly? from, DateOnly? to, CancellationToken ct) =>
        _orders.GetSalesAsync(Range(from, to), _tzId, ct);

    public Task<TimesReport> TimesAsync(DateOnly? from, DateOnly? to, CancellationToken ct) =>
        _orders.GetTimesAsync(Range(from, to), ct);
}
