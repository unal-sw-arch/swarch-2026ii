using System.Text;
using Microsoft.Extensions.Configuration;
using ReportingService.Models;
using ReportingService.Services;

namespace ReportingService.Tests;

public class ExportServiceTests
{
    private readonly FakeStorage _storage = new();
    private readonly FakeHistoryRepository _history = new();
    private readonly ExportService _export;

    public ExportServiceTests()
    {
        var config = new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>
        {
            ["Reporting:BusinessTimeZone"] = "America/Bogota",
        }).Build();
        var reports = new ReportService(new FakeOrdersRepository(), config);
        _export = new ExportService(reports, _history, _storage, config);
        _history.Orders.Add(Samples.Order("o-1", "=HYPERLINK(\"http://malo\")"));
    }

    private static ExportRequest Req(string report, string format) =>
        new(report, format, new DateOnly(2026, 11, 1), new DateOnly(2026, 11, 7));

    [Fact]
    public async Task Exporta_ventas_a_csv_y_devuelve_la_url()
    {
        var result = await _export.ExportAsync(Req("sales", "csv"), default);
        var saved = Assert.Single(_storage.Saved);
        Assert.EndsWith(".csv", saved.Name);
        Assert.StartsWith("exports/", saved.Name);
        Assert.StartsWith("text/csv", saved.ContentType);
        var text = Encoding.UTF8.GetString(saved.Data);
        Assert.StartsWith("fecha,pedidos,ingresos\r\n", text);
        Assert.Contains("TOTAL,2,18000.00", text);
        Assert.Contains(saved.Name, result.Url);
        Assert.Equal("csv", result.Format);
    }

    [Fact]
    public async Task El_historial_exportado_neutraliza_formulas_en_el_csv()
    {
        await _export.ExportAsync(Req("history", "csv"), default);
        var text = Encoding.UTF8.GetString(_storage.Saved[0].Data);
        Assert.Contains("\"'=HYPERLINK(\"\"http://malo\"\")\"", text);
    }

    [Fact]
    public async Task Exporta_tiempos_a_pdf()
    {
        await _export.ExportAsync(Req("times", "pdf"), default);
        var saved = Assert.Single(_storage.Saved);
        Assert.EndsWith(".pdf", saved.Name);
        Assert.Equal("application/pdf", saved.ContentType);
        Assert.StartsWith("%PDF", Encoding.ASCII.GetString(saved.Data, 0, 4));
    }

    [Theory]
    [InlineData("otro", "csv")]
    [InlineData("sales", "xlsx")]
    [InlineData("", "")]
    public async Task Reporte_o_formato_invalido_es_422(string report, string format)
    {
        var ex = await Assert.ThrowsAsync<ApiException>(() => _export.ExportAsync(Req(report, format), default));
        Assert.Equal(422, ex.Status);
        Assert.Empty(_storage.Saved);
    }

    [Fact]
    public void El_csv_escapa_comas_comillas_y_saltos_de_linea()
    {
        var csv = ExportService.BuildCsv(new[] { "a", "b" }, new[] { new[] { "x,y", "dice \"hola\"\nchao" } });
        Assert.Equal("a,b\r\n\"x,y\",\"dice \"\"hola\"\"\nchao\"\r\n", csv);
    }
}

public class StorageEndpointTests
{
    [Theory]
    [InlineData("http://rustfs:9000", false, "rustfs:9000", false)]
    [InlineData("https://s3.example.com", false, "s3.example.com", true)]
    [InlineData("rustfs:9000", false, "rustfs:9000", false)]
    [InlineData("localhost:9000", true, "localhost:9000", true)]
    public void El_endpoint_S3_se_interpreta_con_o_sin_esquema(string endpoint, bool defaultSecure, string host, bool secure)
    {
        var parsed = ReportingService.Repositories.MinioStorage.ParseEndpoint(endpoint, defaultSecure);
        Assert.Equal((host, secure), parsed);
    }
}
