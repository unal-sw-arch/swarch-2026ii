using ReportingService.Models;

namespace ReportingService.Tests;

public class DateRangeTests
{
    private static readonly TimeZoneInfo Bogota = TimeZoneInfo.FindSystemTimeZoneById("America/Bogota");

    [Fact]
    public void Por_defecto_son_los_ultimos_30_dias_en_la_zona_del_negocio()
    {
        // 2026-11-08 02:00 UTC = 2026-11-07 21:00 en Bogotá
        var range = DateRange.Resolve(null, null, 366, Bogota, new DateTime(2026, 11, 8, 2, 0, 0, DateTimeKind.Utc));
        Assert.Equal(new DateOnly(2026, 11, 7), range.To);
        Assert.Equal(new DateOnly(2026, 10, 9), range.From);
        Assert.Equal(new DateTime(2026, 11, 8, 5, 0, 0, DateTimeKind.Utc), range.ToUtcExclusive);
    }

    [Fact]
    public void El_rango_es_inclusivo_en_ambos_extremos()
    {
        var range = DateRange.Resolve(new DateOnly(2026, 11, 1), new DateOnly(2026, 11, 1), 366, Bogota);
        Assert.Equal(TimeSpan.FromDays(1), range.ToUtcExclusive - range.FromUtc);
    }

    [Fact]
    public void Rango_invertido_o_demasiado_largo_es_422()
    {
        var inverted = Assert.Throws<ApiException>(() =>
            DateRange.Resolve(new DateOnly(2026, 11, 5), new DateOnly(2026, 11, 1), 366, Bogota));
        Assert.Equal(422, inverted.Status);
        var tooLong = Assert.Throws<ApiException>(() =>
            DateRange.Resolve(new DateOnly(2025, 1, 1), new DateOnly(2026, 11, 1), 366, Bogota));
        Assert.Equal(422, tooLong.Status);
    }
}
