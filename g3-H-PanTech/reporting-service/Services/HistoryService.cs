using ReportingService.Models;
using ReportingService.Repositories;
using ReportingService.Security;

namespace ReportingService.Services;

/// <summary>Historial con alcance por permiso: `history:read_all` ve todo; `history:read_own` solo lo propio.</summary>
public sealed class HistoryService
{
    public const int MaxPageSize = 100;

    private readonly IHistoryRepository _history;
    private readonly IOrdersReadRepository _orders;
    private readonly TimeZoneInfo _tz;

    public HistoryService(IHistoryRepository history, IOrdersReadRepository orders, IConfiguration config)
    {
        _history = history;
        _orders = orders;
        _tz = TimeZoneInfo.FindSystemTimeZoneById(config["Reporting:BusinessTimeZone"] ?? "America/Bogota");
    }

    public async Task<PagedResult<HistoryOrder>> ListAsync(UserContext user, HistoryQuery q, CancellationToken ct)
    {
        string? customerId;
        if (user.Has("history:read_all")) customerId = q.CustomerId;       // el personal puede filtrar por cliente
        else if (user.Has("history:read_own")) customerId = user.Id;       // el cliente: siempre lo propio, ignora lo que pida
        else throw ApiException.Forbidden();

        var page = Math.Max(1, q.Page);
        var size = Math.Clamp(q.PageSize, 1, MaxPageSize);

        DateTime? fromUtc = null, toUtc = null;
        if (q.From != null || q.To != null)
        {
            // Con un solo extremo, el otro queda abierto; con ambos se valida el orden y el tamaño.
            var range = DateRange.Resolve(q.From ?? q.To, q.To ?? q.From, maxDays: 3660, _tz);
            fromUtc = q.From == null ? null : range.FromUtc;
            toUtc = q.To == null ? null : range.ToUtcExclusive;
        }

        var (items, total) = await _history.QueryAsync(
            new HistoryFilter(fromUtc, toUtc, q.Status, customerId, (page - 1) * size, size), ct);
        return new PagedResult<HistoryOrder>(items, total, page, size);
    }

    public async Task<HistoryOrder> GetOrderAsync(UserContext user, string orderId, CancellationToken ct)
    {
        var all = user.Has("history:read_all");
        if (!all && !user.Has("history:read_own")) throw ApiException.Forbidden();

        // El read model puede ir unos segundos por detrás del pago: si no está, se lee de PostgreSQL.
        var order = await _history.GetByOrderIdAsync(orderId, ct) ?? await _orders.GetOrderAsync(orderId, ct);

        // Pedido ajeno = 404 (no revela que existe).
        if (order is null || (!all && order.CustomerId != user.Id))
            throw ApiException.NotFound("Pedido no encontrado");
        return order;
    }
}
