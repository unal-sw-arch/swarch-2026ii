using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.IdentityModel.Tokens;
using ReportingService.Repositories;

namespace ReportingService.Tests;

/// <summary>Pruebas de punta a punta de permisos sobre la API real (JWT RS256 y repositorios falsos).</summary>
public class ApiTests : IClassFixture<ApiTests.Factory>
{
    private static readonly RSA Key = RSA.Create(2048);
    private static readonly RSA OtherKey = RSA.Create(2048);

    public sealed class Factory : WebApplicationFactory<Program>
    {
        public FakeHistoryRepository History { get; } = new();
        public FakeStorage Storage { get; } = new();

        protected override void ConfigureWebHost(Microsoft.AspNetCore.Hosting.IWebHostBuilder builder)
        {
            builder.ConfigureAppConfiguration((_, cfg) => cfg.AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Jwt:PublicKeyPem"] = Key.ExportSubjectPublicKeyInfoPem(),
                ["Jwt:Issuer"] = "identity-service",
                ["Jwt:Audience"] = "panaderia-express",
            }));
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<IHistoryRepository>();
                services.RemoveAll<IOrdersReadRepository>();
                services.RemoveAll<IObjectStorage>();
                services.AddSingleton<IHistoryRepository>(History);
                services.AddSingleton<IOrdersReadRepository>(new FakeOrdersRepository());
                services.AddSingleton<IObjectStorage>(Storage);
            });
        }
    }

    private readonly Factory _factory;

    public ApiTests(Factory factory)
    {
        _factory = factory;
        if (_factory.History.Orders.Count == 0)
        {
            _factory.History.Orders.Add(Samples.Order("o-1", "cust-1"));
            _factory.History.Orders.Add(Samples.Order("o-2", "cust-2"));
        }
    }

    private static string Token(string sub, string[] permissions, bool wrongKey = false, int expiresInMinutes = 15, string audience = "panaderia-express")
    {
        var claims = new List<Claim> { new("sub", sub) };
        claims.AddRange(permissions.Select(p => new Claim("permissions", p)));
        var credentials = new SigningCredentials(new RsaSecurityKey(wrongKey ? OtherKey : Key), SecurityAlgorithms.RsaSha256);
        var now = DateTime.UtcNow;
        var token = new JwtSecurityToken("identity-service", audience, claims,
            notBefore: now.AddMinutes(Math.Min(-1, expiresInMinutes - 1)),
            expires: now.AddMinutes(expiresInMinutes), signingCredentials: credentials);
        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    private HttpClient Client(string? token = null)
    {
        var client = _factory.CreateClient();
        if (token != null) client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        return client;
    }

    private static readonly string[] CustomerPerms = { "history:read_own" };
    private static readonly string[] SupervisorPerms = { "history:read_all", "reports:read" };

    [Fact]
    public async Task Health_no_requiere_token()
    {
        Assert.Equal(HttpStatusCode.OK, (await Client().GetAsync("/health")).StatusCode);
    }

    [Fact]
    public async Task Sin_token_o_token_invalido_es_401_con_formato_uniforme()
    {
        var response = await Client().GetAsync("/history");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal("unauthorized", body.RootElement.GetProperty("code").GetString());
        Assert.True(response.Headers.Contains("X-Request-Id"));

        Assert.Equal(HttpStatusCode.Unauthorized, (await Client("basura").GetAsync("/history")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await Client(Token("c", CustomerPerms, wrongKey: true)).GetAsync("/history")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await Client(Token("c", CustomerPerms, expiresInMinutes: -30)).GetAsync("/history")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await Client(Token("c", CustomerPerms, audience: "otra-app")).GetAsync("/history")).StatusCode);
    }

    [Fact]
    public async Task Cliente_ve_solo_su_historial()
    {
        var response = await Client(Token("cust-1", CustomerPerms)).GetAsync("/history?customer_id=cust-2");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var items = body.RootElement.GetProperty("items");
        Assert.Equal(1, items.GetArrayLength());
        Assert.Equal("o-1", items[0].GetProperty("order_id").GetString());
    }

    [Fact]
    public async Task Cliente_no_accede_a_pedidos_ajenos_ni_a_reportes()
    {
        var client = Client(Token("cust-1", CustomerPerms));
        Assert.Equal(HttpStatusCode.NotFound, (await client.GetAsync("/history/orders/o-2")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/history/orders/o-1")).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, (await client.GetAsync("/reports/sales")).StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, (await client.GetAsync("/reports/times")).StatusCode);
        var export = await client.PostAsJsonAsync("/reports/export", new { report = "sales", format = "csv" });
        Assert.Equal(HttpStatusCode.Forbidden, export.StatusCode);
    }

    [Fact]
    public async Task Usuario_sin_permisos_de_historial_es_403()
    {
        var response = await Client(Token("cook-1", new[] { "kitchen:read" })).GetAsync("/history");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
    }

    [Fact]
    public async Task Supervisor_ve_todo_y_consulta_reportes()
    {
        var client = Client(Token("sup-1", SupervisorPerms));
        using var history = JsonDocument.Parse(await (await client.GetAsync("/history")).Content.ReadAsStringAsync());
        Assert.Equal(2, history.RootElement.GetProperty("total").GetInt64());

        var sales = await client.GetAsync("/reports/sales?from=2026-11-01&to=2026-11-07");
        Assert.Equal(HttpStatusCode.OK, sales.StatusCode);
        using var salesBody = JsonDocument.Parse(await sales.Content.ReadAsStringAsync());
        Assert.Equal(2, salesBody.RootElement.GetProperty("orders_count").GetInt32());

        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/reports/times")).StatusCode);
        Assert.Equal(HttpStatusCode.UnprocessableEntity, (await client.GetAsync("/reports/sales?from=2026-11-07&to=2026-11-01")).StatusCode);
    }

    [Fact]
    public async Task Supervisor_exporta_y_recibe_la_url()
    {
        var client = Client(Token("sup-1", SupervisorPerms));
        var response = await client.PostAsJsonAsync("/reports/export", new { report = "sales", format = "csv", from = "2026-11-01", to = "2026-11-07" });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.StartsWith("http://minio.test/reports/exports/", body.RootElement.GetProperty("url").GetString());
        Assert.Single(_factory.Storage.Saved);
    }
}
