namespace ReportingService.Models;

public sealed class ApiException : Exception
{
    public int Status { get; }
    public string Code { get; }
    public object? Details { get; }

    public ApiException(int status, string code, string message, object? details = null) : base(message)
    {
        Status = status;
        Code = code;
        Details = details;
    }

    public static ApiException Forbidden(string message = "No tienes permiso para esta operación") => new(403, "forbidden", message);
    public static ApiException NotFound(string message) => new(404, "not_found", message);
    public static ApiException Invalid(string message, object? details = null) => new(422, "validation_error", message, details);
}

public sealed record HistoryItem(string ProductId, string Name, int Quantity, decimal UnitPrice, int PrepMinutes);

public sealed record TimelineEntry(string Status, DateTime At);

public sealed record HistoryOrder(
    string OrderId,
    string? CustomerId,
    string Status,
    decimal Total,
    string? OrderType,
    int? TableNumber,
    DateTime? PaidAt,
    DateTime? ReadyAt,
    string? ReceiptObject,
    IReadOnlyList<HistoryItem> Items,
    IReadOnlyList<TimelineEntry> Timeline);

public sealed record PagedResult<T>(IReadOnlyList<T> Items, long Total, int Page, int PageSize);

public sealed record HistoryQuery(DateOnly? From, DateOnly? To, string? Status, string? CustomerId, int Page, int PageSize);

/// <summary>Filtro ya resuelto que reciben los repositorios (UTC, alcance aplicado).</summary>
public sealed record HistoryFilter(DateTime? FromUtc, DateTime? ToUtcExclusive, string? Status, string? CustomerId, int Skip, int Take);

public sealed record DailySales(DateOnly Day, int Orders, decimal Revenue);
public sealed record TopProduct(string ProductId, string Name, long Quantity, decimal Revenue);

public sealed record SalesReport(
    DateOnly From, DateOnly To, int OrdersCount, decimal Revenue, decimal AverageTicket,
    IReadOnlyList<DailySales> ByDay, IReadOnlyList<TopProduct> TopProducts);

public sealed record TimesReport(
    DateOnly From, DateOnly To, int OrdersMeasured,
    double? AvgPrepMinutes, double? AvgDeliveryMinutes, double? AvgTotalMinutes);

public sealed record ExportRequest(string Report, string Format, DateOnly? From, DateOnly? To);

public sealed record ExportResult(string Url, string Object, string Format, string ContentType, int Bytes, DateTime ExpiresAt);
