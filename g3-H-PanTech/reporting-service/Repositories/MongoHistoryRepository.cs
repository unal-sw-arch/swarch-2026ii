using MongoDB.Bson;
using MongoDB.Driver;
using ReportingService.Models;

namespace ReportingService.Repositories;

public sealed class MongoHistoryRepository : IHistoryRepository
{
    private readonly Lazy<IMongoCollection<BsonDocument>> _collection;

    public MongoHistoryRepository(IConfiguration config)
    {
        _collection = new Lazy<IMongoCollection<BsonDocument>>(() =>
        {
            var url = new MongoUrl(config["MONGODB_URL"] ?? config["Mongo:Url"] ?? "mongodb://mongodb:27017/panaderia");
            var client = new MongoClient(url);
            return client.GetDatabase(url.DatabaseName ?? config["Mongo:Database"] ?? "panaderia")
                .GetCollection<BsonDocument>("order_history");
        });
    }

    public async Task<(IReadOnlyList<HistoryOrder> Items, long Total)> QueryAsync(HistoryFilter f, CancellationToken ct)
    {
        var b = Builders<BsonDocument>.Filter;
        var filters = new List<FilterDefinition<BsonDocument>> { b.Exists("paid_at") };
        if (f.CustomerId != null) filters.Add(b.Eq("customer_id", f.CustomerId));
        if (f.Status != null) filters.Add(b.Eq("status", f.Status));
        if (f.FromUtc != null) filters.Add(b.Gte("paid_at", f.FromUtc.Value));
        if (f.ToUtcExclusive != null) filters.Add(b.Lt("paid_at", f.ToUtcExclusive.Value));
        var filter = b.And(filters);

        var total = await _collection.Value.CountDocumentsAsync(filter, cancellationToken: ct);
        var docs = await _collection.Value
            .Find(filter)
            .Sort(Builders<BsonDocument>.Sort.Descending("paid_at"))
            .Skip(f.Skip)
            .Limit(f.Take)
            .ToListAsync(ct);
        return (docs.Select(ToOrder).ToList(), total);
    }

    public async Task<HistoryOrder?> GetByOrderIdAsync(string orderId, CancellationToken ct)
    {
        var doc = await _collection.Value
            .Find(Builders<BsonDocument>.Filter.Eq("order_id", orderId))
            .FirstOrDefaultAsync(ct);
        return doc is null ? null : ToOrder(doc);
    }

    private static string? Str(BsonDocument d, string name) =>
        d.TryGetValue(name, out var v) && !v.IsBsonNull ? v.ToString() : null;

    private static DateTime? Date(BsonDocument d, string name) =>
        d.TryGetValue(name, out var v) && v.IsValidDateTime ? v.ToUniversalTime() : null;

    private static HistoryOrder ToOrder(BsonDocument d)
    {
        var items = d.TryGetValue("items", out var rawItems) && rawItems.IsBsonArray
            ? rawItems.AsBsonArray.Select(i => i.AsBsonDocument).Select(i => new HistoryItem(
                Str(i, "product_id") ?? "",
                Str(i, "name") ?? "",
                i.GetValue("quantity", 0).ToInt32(),
                i.TryGetValue("unit_price", out var up) && !up.IsBsonNull ? decimal.Parse(up.ToString()!, System.Globalization.CultureInfo.InvariantCulture) : 0m,
                i.GetValue("prep_minutes", 0).ToInt32())).ToList()
            : new List<HistoryItem>();

        var timeline = d.TryGetValue("timeline", out var rawTl) && rawTl.IsBsonArray
            ? rawTl.AsBsonArray.Select(t => t.AsBsonDocument)
                .Where(t => t.GetValue("at", BsonNull.Value).IsValidDateTime)
                .Select(t => new TimelineEntry(Str(t, "status") ?? "", t["at"].ToUniversalTime()))
                .OrderBy(t => t.At).ToList()
            : new List<TimelineEntry>();

        return new HistoryOrder(
            Str(d, "order_id") ?? "",
            Str(d, "customer_id"),
            Str(d, "status") ?? "",
            d.TryGetValue("total", out var total) && !total.IsBsonNull ? total.ToDecimal() : 0m,
            Str(d, "order_type"),
            d.TryGetValue("table_number", out var table) && !table.IsBsonNull ? table.ToInt32() : null,
            Date(d, "paid_at"),
            Date(d, "ready_at"),
            Str(d, "receipt_object"),
            items,
            timeline);
    }
}
