using ReportingService.Models;
using ReportingService.Repositories;

namespace ReportingService.Tests;

public sealed class FakeHistoryRepository : IHistoryRepository
{
    public List<HistoryOrder> Orders { get; } = new();
    public HistoryFilter? LastFilter { get; private set; }

    public Task<(IReadOnlyList<HistoryOrder> Items, long Total)> QueryAsync(HistoryFilter filter, CancellationToken ct)
    {
        LastFilter = filter;
        var rows = Orders
            .Where(o => filter.CustomerId == null || o.CustomerId == filter.CustomerId)
            .Where(o => filter.Status == null || o.Status == filter.Status)
            .ToList();
        return Task.FromResult(((IReadOnlyList<HistoryOrder>)rows.Skip(filter.Skip).Take(filter.Take).ToList(), (long)rows.Count));
    }

    public Task<HistoryOrder?> GetByOrderIdAsync(string orderId, CancellationToken ct) =>
        Task.FromResult(Orders.FirstOrDefault(o => o.OrderId == orderId));
}

public sealed class FakeOrdersRepository : IOrdersReadRepository
{
    public List<HistoryOrder> Orders { get; } = new();
    public DateRange? LastRange { get; private set; }

    public Task PingAsync(CancellationToken ct) => Task.CompletedTask;

    public Task<HistoryOrder?> GetOrderAsync(string orderId, CancellationToken ct) =>
        Task.FromResult(Orders.FirstOrDefault(o => o.OrderId == orderId));

    public Task<SalesReport> GetSalesAsync(DateRange range, string timeZoneId, CancellationToken ct)
    {
        LastRange = range;
        var byDay = new List<DailySales> { new(range.From, 2, 18000m) };
        var top = new List<TopProduct> { new("p-cafe", "Café americano", 4, 18000m) };
        return Task.FromResult(new SalesReport(range.From, range.To, 2, 18000m, 9000m, byDay, top));
    }

    public Task<TimesReport> GetTimesAsync(DateRange range, CancellationToken ct)
    {
        LastRange = range;
        return Task.FromResult(new TimesReport(range.From, range.To, 3, 6.5, 2.0, 8.5));
    }
}

public sealed class FakeStorage : IObjectStorage
{
    public List<(string Name, byte[] Data, string ContentType)> Saved { get; } = new();

    public Task<ExportResult> SaveAsync(string objectName, byte[] data, string contentType, string format, CancellationToken ct)
    {
        Saved.Add((objectName, data, contentType));
        return Task.FromResult(new ExportResult(
            $"http://minio.test/reports/{objectName}?sig=x", $"reports/{objectName}", format, contentType, data.Length, DateTime.UtcNow.AddHours(1)));
    }
}

public static class Samples
{
    public static HistoryOrder Order(string id, string? customerId, string status = "pagado") =>
        new(id, customerId, status, 9000m, "para_llevar", null,
            new DateTime(2026, 11, 7, 15, 0, 0, DateTimeKind.Utc), null, null,
            new List<HistoryItem> { new("p-cafe", "Café americano", 2, 4500m, 3) },
            new List<TimelineEntry> { new("pagado", new DateTime(2026, 11, 7, 15, 0, 0, DateTimeKind.Utc)) });
}
