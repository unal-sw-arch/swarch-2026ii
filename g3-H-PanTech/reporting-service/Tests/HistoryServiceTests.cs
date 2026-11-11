using Microsoft.Extensions.Configuration;
using ReportingService.Models;
using ReportingService.Security;
using ReportingService.Services;

namespace ReportingService.Tests;

public class HistoryServiceTests
{
    private readonly FakeHistoryRepository _mongo = new();
    private readonly FakeOrdersRepository _pg = new();
    private readonly HistoryService _service;

    private static readonly UserContext Customer = new("cust-1", new HashSet<string> { "history:read_own" });
    private static readonly UserContext Supervisor = new("sup-1", new HashSet<string> { "history:read_all", "reports:read" });
    private static readonly UserContext Cook = new("cook-1", new HashSet<string> { "kitchen:read" });

    public HistoryServiceTests()
    {
        var config = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Reporting:BusinessTimeZone"] = "America/Bogota",
        }).Build();
        _service = new HistoryService(_mongo, _pg, config);
        _mongo.Orders.Add(Samples.Order("o-1", "cust-1"));
        _mongo.Orders.Add(Samples.Order("o-2", "cust-2"));
    }

    private static HistoryQuery Query(string? customerId = null, int pageSize = 20, DateOnly? from = null, DateOnly? to = null) =>
        new(from, to, null, customerId, 1, pageSize);

    [Fact]
    public async Task Cliente_solo_ve_lo_propio_aunque_pida_otro_cliente()
    {
        var result = await _service.ListAsync(Customer, Query(customerId: "cust-2"), default);
        Assert.Equal("cust-1", _mongo.LastFilter!.CustomerId);
        Assert.Single(result.Items);
        Assert.Equal("o-1", result.Items[0].OrderId);
    }

    [Fact]
    public async Task Supervisor_ve_todo_y_puede_filtrar_por_cliente()
    {
        Assert.Equal(2, (await _service.ListAsync(Supervisor, Query(), default)).Total);
        var filtered = await _service.ListAsync(Supervisor, Query(customerId: "cust-2"), default);
        Assert.Equal("o-2", Assert.Single(filtered.Items).OrderId);
    }

    [Fact]
    public async Task Sin_permiso_de_historial_es_403()
    {
        var ex = await Assert.ThrowsAsync<ApiException>(() => _service.ListAsync(Cook, Query(), default));
        Assert.Equal(403, ex.Status);
        await Assert.ThrowsAsync<ApiException>(() => _service.GetOrderAsync(Cook, "o-1", default));
    }

    [Fact]
    public async Task Detalle_del_dueno_ok_y_pedido_ajeno_es_404()
    {
        Assert.Equal("o-1", (await _service.GetOrderAsync(Customer, "o-1", default)).OrderId);
        var ex = await Assert.ThrowsAsync<ApiException>(() => _service.GetOrderAsync(Customer, "o-2", default));
        Assert.Equal(404, ex.Status);
        Assert.Equal("o-2", (await _service.GetOrderAsync(Supervisor, "o-2", default)).OrderId);
    }

    [Fact]
    public async Task Si_el_read_model_aun_no_lo_tiene_se_lee_de_postgresql()
    {
        _pg.Orders.Add(Samples.Order("o-3", "cust-1"));
        Assert.Equal("o-3", (await _service.GetOrderAsync(Customer, "o-3", default)).OrderId);
        await Assert.ThrowsAsync<ApiException>(() => _service.GetOrderAsync(Customer, "no-existe", default));
    }

    [Fact]
    public async Task El_tamano_de_pagina_se_limita()
    {
        await _service.ListAsync(Supervisor, Query(pageSize: 5000), default);
        Assert.Equal(HistoryService.MaxPageSize, _mongo.LastFilter!.Take);
    }

    [Fact]
    public async Task Las_fechas_se_convierten_a_UTC_desde_la_zona_del_negocio()
    {
        await _service.ListAsync(Supervisor, Query(from: new DateOnly(2026, 11, 1), to: new DateOnly(2026, 11, 3)), default);
        Assert.Equal(new DateTime(2026, 11, 1, 5, 0, 0, DateTimeKind.Utc), _mongo.LastFilter!.FromUtc);          // Bogotá = UTC-5
        Assert.Equal(new DateTime(2026, 11, 4, 5, 0, 0, DateTimeKind.Utc), _mongo.LastFilter!.ToUtcExclusive);   // 'to' es inclusivo
    }
}
