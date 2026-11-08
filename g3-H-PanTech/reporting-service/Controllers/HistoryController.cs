using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ReportingService.Models;
using ReportingService.Security;
using ReportingService.Services;

namespace ReportingService.Controllers;

[ApiController]
[Route("history")]
[Authorize(Policy = "HistoryRead")]
public sealed class HistoryController : ControllerBase
{
    private readonly HistoryService _history;

    public HistoryController(HistoryService history) => _history = history;

    /// <summary>Historial de pedidos pagados. El cliente solo ve los suyos; supervisor y admin ven todos.</summary>
    [HttpGet]
    public Task<PagedResult<HistoryOrder>> List(
        [FromQuery(Name = "from")] DateOnly? from,
        [FromQuery(Name = "to")] DateOnly? to,
        [FromQuery(Name = "status")] string? status,
        [FromQuery(Name = "customer_id")] string? customerId,
        [FromQuery(Name = "page")] int page = 1,
        [FromQuery(Name = "page_size")] int pageSize = 20,
        CancellationToken ct = default) =>
        _history.ListAsync(UserContext.From(User), new HistoryQuery(from, to, status, customerId, page, pageSize), ct);

    /// <summary>Detalle de un pedido: el dueño o quien tenga history:read_all (pedido ajeno → 404).</summary>
    [HttpGet("orders/{orderId}")]
    public Task<HistoryOrder> GetOrder(string orderId, CancellationToken ct) =>
        _history.GetOrderAsync(UserContext.From(User), orderId, ct);
}
