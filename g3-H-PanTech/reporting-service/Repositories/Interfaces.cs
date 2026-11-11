using ReportingService.Models;

namespace ReportingService.Repositories;

/// <summary>Read model `order_history` (MongoDB), mantenido por el Worker. Solo lectura.</summary>
public interface IHistoryRepository
{
    Task<(IReadOnlyList<HistoryOrder> Items, long Total)> QueryAsync(HistoryFilter filter, CancellationToken ct);
    Task<HistoryOrder?> GetByOrderIdAsync(string orderId, CancellationToken ct);
}

/// <summary>Lectura de PostgreSQL (esquemas `orders` y `kitchen`). Nunca escribe.</summary>
public interface IOrdersReadRepository
{
    Task<HistoryOrder?> GetOrderAsync(string orderId, CancellationToken ct);
    Task<SalesReport> GetSalesAsync(DateRange range, string timeZoneId, CancellationToken ct);
    Task<TimesReport> GetTimesAsync(DateRange range, CancellationToken ct);
    Task PingAsync(CancellationToken ct);
}

public interface IObjectStorage
{
    Task<ExportResult> SaveAsync(string objectName, byte[] data, string contentType, string format, CancellationToken ct);
}
