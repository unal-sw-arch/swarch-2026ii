namespace ReportingService.Models;

/// <summary>Rango de fechas [From, To] inclusivo en la zona del negocio, convertido a UTC [FromUtc, ToUtcExclusive).</summary>
public sealed record DateRange(DateOnly From, DateOnly To, DateTime FromUtc, DateTime ToUtcExclusive)
{
    public static DateRange Resolve(DateOnly? from, DateOnly? to, int maxDays, TimeZoneInfo tz, DateTime? nowUtc = null)
    {
        var today = DateOnly.FromDateTime(TimeZoneInfo.ConvertTimeFromUtc(nowUtc ?? DateTime.UtcNow, tz));
        var end = to ?? today;
        var start = from ?? end.AddDays(-29); // por defecto, los últimos 30 días

        if (start > end)
            throw ApiException.Invalid("'from' no puede ser posterior a 'to'");
        if (end.DayNumber - start.DayNumber + 1 > maxDays)
            throw ApiException.Invalid($"El rango máximo es de {maxDays} días", new { max_days = maxDays });

        return new DateRange(start, end, ToUtc(start, tz), ToUtc(end.AddDays(1), tz));
    }

    public static DateTime ToUtc(DateOnly date, TimeZoneInfo tz)
    {
        var local = DateTime.SpecifyKind(date.ToDateTime(TimeOnly.MinValue), DateTimeKind.Unspecified);
        return TimeZoneInfo.ConvertTimeToUtc(local, tz);
    }
}
