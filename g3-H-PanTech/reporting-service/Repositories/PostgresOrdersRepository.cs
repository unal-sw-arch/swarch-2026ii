using Npgsql;
using NpgsqlTypes;
using ReportingService.Models;

namespace ReportingService.Repositories;

/// <summary>
/// Solo lectura sobre `orders.*` (Order Service) y `kitchen.order_status_events` (Kitchen Service).
/// La cadena de conexión por defecto fuerza `default_transaction_read_only=on`.
/// </summary>
public sealed class PostgresOrdersRepository : IOrdersReadRepository
{
    private readonly NpgsqlDataSource _db;

    public PostgresOrdersRepository(IConfiguration config)
    {
        // El compose entrega DATABASE_URL (DATABASE_URL_DOTNET); ConnectionStrings:Postgres es la alternativa local.
        var connection = config["DATABASE_URL"] ?? config.GetConnectionString("Postgres")
            ?? throw new InvalidOperationException("Falta DATABASE_URL o ConnectionStrings:Postgres");
        if (!connection.Contains("default_transaction_read_only", StringComparison.OrdinalIgnoreCase))
            connection = connection.TrimEnd(';') + ";Options=-c default_transaction_read_only=on"; // PostgreSQL rechaza escrituras
        _db = NpgsqlDataSource.Create(connection);
    }

    public async Task PingAsync(CancellationToken ct)
    {
        await using var cmd = _db.CreateCommand("SELECT 1");
        await cmd.ExecuteScalarAsync(ct);
    }

    public async Task<HistoryOrder?> GetOrderAsync(string orderId, CancellationToken ct)
    {
        if (!Guid.TryParse(orderId, out var id)) return null;

        await using var cmd = _db.CreateCommand(
            "SELECT customer_id, status, total, order_type, table_number FROM orders.orders WHERE id = @id");
        cmd.Parameters.AddWithValue("id", id);
        await using var r = await cmd.ExecuteReaderAsync(ct);
        if (!await r.ReadAsync(ct)) return null;
        var customerId = r.IsDBNull(0) ? null : r.GetString(0);
        var status = r.GetString(1);
        var total = r.GetDecimal(2);
        var type = r.GetString(3);
        int? table = r.IsDBNull(4) ? null : r.GetInt32(4);
        await r.CloseAsync();

        var items = new List<HistoryItem>();
        await using (var ic = _db.CreateCommand(
            "SELECT product_id, product_name, quantity, unit_price, prep_minutes FROM orders.order_items WHERE order_id = @id"))
        {
            ic.Parameters.AddWithValue("id", id);
            await using var ir = await ic.ExecuteReaderAsync(ct);
            while (await ir.ReadAsync(ct))
                items.Add(new HistoryItem(ir.GetString(0), ir.GetString(1), ir.GetInt32(2), ir.GetDecimal(3), ir.GetInt32(4)));
        }

        DateTime? paidAt = null;
        await using (var pc = _db.CreateCommand("SELECT created_at FROM orders.payments WHERE order_id = @id"))
        {
            pc.Parameters.AddWithValue("id", id);
            var value = await pc.ExecuteScalarAsync(ct);
            if (value is DateTime dt) paidAt = DateTime.SpecifyKind(dt, DateTimeKind.Utc);
        }

        return new HistoryOrder(orderId, customerId, status, total, type, table, paidAt, null, null, items,
            paidAt is null ? new List<TimelineEntry>() : new List<TimelineEntry> { new("pagado", paidAt.Value) });
    }

    public async Task<SalesReport> GetSalesAsync(DateRange range, string timeZoneId, CancellationToken ct)
    {
        var byDay = new List<DailySales>();
        await using (var cmd = _db.CreateCommand(@"
            SELECT (p.created_at AT TIME ZONE @tz)::date AS day, count(*)::int AS orders, COALESCE(sum(p.amount), 0) AS revenue
            FROM orders.payments p
            JOIN orders.orders o ON o.id = p.order_id
            WHERE p.created_at >= @from AND p.created_at < @to AND o.status <> 'cancelado'
            GROUP BY 1 ORDER BY 1"))
        {
            AddRange(cmd, range);
            cmd.Parameters.AddWithValue("tz", timeZoneId);
            await using var r = await cmd.ExecuteReaderAsync(ct);
            while (await r.ReadAsync(ct))
                byDay.Add(new DailySales(DateOnly.FromDateTime(r.GetDateTime(0)), r.GetInt32(1), r.GetDecimal(2)));
        }

        var top = new List<TopProduct>();
        await using (var cmd = _db.CreateCommand(@"
            SELECT i.product_id, max(i.product_name), sum(i.quantity)::bigint AS qty, sum(i.line_total) AS revenue
            FROM orders.order_items i
            JOIN orders.payments p ON p.order_id = i.order_id
            JOIN orders.orders o ON o.id = i.order_id
            WHERE p.created_at >= @from AND p.created_at < @to AND o.status <> 'cancelado'
            GROUP BY i.product_id ORDER BY qty DESC, revenue DESC LIMIT 10"))
        {
            AddRange(cmd, range);
            await using var r = await cmd.ExecuteReaderAsync(ct);
            while (await r.ReadAsync(ct))
                top.Add(new TopProduct(r.GetString(0), r.GetString(1), r.GetInt64(2), r.GetDecimal(3)));
        }

        var orders = byDay.Sum(d => d.Orders);
        var revenue = byDay.Sum(d => d.Revenue);
        var avg = orders == 0 ? 0m : Math.Round(revenue / orders, 2);
        return new SalesReport(range.From, range.To, orders, revenue, avg, byDay, top);
    }

    public async Task<TimesReport> GetTimesAsync(DateRange range, CancellationToken ct)
    {
        // Tiempos desde los eventos de estado de Kitchen (una fila por transición).
        // Se compara order_id como texto porque cada servicio define su propio tipo de id.
        const string sql = @"
            WITH t AS (
              SELECT order_id::text AS order_id,
                     min(changed_at) FILTER (WHERE status = 'pendiente') AS queued_at,
                     min(changed_at) FILTER (WHERE status = 'listo') AS ready_at,
                     min(changed_at) FILTER (WHERE status = 'entregado') AS delivered_at
              FROM kitchen.order_status_events
              GROUP BY order_id)
            SELECT count(*)::int,
                   avg(extract(epoch FROM (ready_at - queued_at)) / 60.0) FILTER (WHERE ready_at IS NOT NULL),
                   avg(extract(epoch FROM (delivered_at - ready_at)) / 60.0) FILTER (WHERE delivered_at IS NOT NULL AND ready_at IS NOT NULL),
                   avg(extract(epoch FROM (delivered_at - queued_at)) / 60.0) FILTER (WHERE delivered_at IS NOT NULL)
            FROM t
            WHERE queued_at >= @from AND queued_at < @to";
        try
        {
            await using var cmd = _db.CreateCommand(sql);
            AddRange(cmd, range);
            await using var r = await cmd.ExecuteReaderAsync(ct);
            await r.ReadAsync(ct);
            double? D(int i) => r.IsDBNull(i) ? null : Math.Round(Convert.ToDouble(r.GetValue(i)), 2);
            return new TimesReport(range.From, range.To, r.GetInt32(0), D(1), D(2), D(3));
        }
        catch (PostgresException ex) when (ex.SqlState is "42P01" or "3F000" or "42501")
        {
            // Tabla/esquema inexistente o sin permiso: Kitchen aún no está desplegado o no concede lectura.
            throw new ApiException(503, "service_unavailable",
                "Los tiempos de cocina aún no están disponibles (kitchen.order_status_events)");
        }
    }

    private static void AddRange(NpgsqlCommand cmd, DateRange range)
    {
        cmd.Parameters.AddWithValue("from", NpgsqlDbType.TimestampTz, range.FromUtc);
        cmd.Parameters.AddWithValue("to", NpgsqlDbType.TimestampTz, range.ToUtcExclusive);
    }
}
