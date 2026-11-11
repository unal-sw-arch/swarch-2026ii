using System.Text.Json;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using ReportingService.Models;
using ReportingService.Repositories;
using ReportingService.Security;
using ReportingService.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers().AddJsonOptions(o =>
{
    // snake_case como el resto de servicios (order_id, customer_id...).
    o.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower;
});

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(o =>
{
    o.SwaggerDoc("v1", new OpenApiInfo { Title = "Panadería Express — Reporting Service", Version = "0.1.0" });
    o.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http, Scheme = "bearer", BearerFormat = "JWT", In = ParameterLocation.Header,
    });
    o.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { new OpenApiSecurityScheme { Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" } }, Array.Empty<string>() },
    });
});

// ---- Autenticación: JWT RS256 emitido por identity-service (claves vía JWKS). La configuración se lee al resolver
// las opciones (no al construir el host) para que las pruebas puedan sobrescribirla. ----
builder.Services.AddSingleton<JwksKeyProvider>();
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer();
builder.Services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
    .Configure<IConfiguration, JwksKeyProvider>((options, config, keys) =>
    {
        options.MapInboundClaims = false; // conserva `sub`, `roles` y `permissions` tal cual
        var issuer = config["JWT_ISSUER"] ?? config["Jwt:Issuer"];
        var audience = config["JWT_AUDIENCE"] ?? config["Jwt:Audience"];
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = !string.IsNullOrEmpty(issuer),
            ValidIssuer = issuer,
            ValidateAudience = !string.IsNullOrEmpty(audience),
            ValidAudience = audience,
            ValidateLifetime = true,
            RequireExpirationTime = true,
            ClockSkew = TimeSpan.FromSeconds(10),
            ValidAlgorithms = new[] { SecurityAlgorithms.RsaSha256 },
            ValidateIssuerSigningKey = true,
            IssuerSigningKeyResolver = (token, securityToken, kid, parameters) => keys.GetKeys(kid),
        };
    });

static bool HasAny(System.Security.Claims.ClaimsPrincipal user, params string[] permissions) =>
    user.FindAll("permissions").Any(c => permissions.Contains(c.Value));

builder.Services.AddAuthorization(o =>
{
    o.AddPolicy("HistoryRead", p => p.RequireAuthenticatedUser()
        .RequireAssertion(ctx => HasAny(ctx.User, "history:read_own", "history:read_all")));
    o.AddPolicy("ReportsRead", p => p.RequireAuthenticatedUser()
        .RequireAssertion(ctx => HasAny(ctx.User, "reports:read")));
});

builder.Services.AddSingleton<IHistoryRepository, MongoHistoryRepository>();
builder.Services.AddSingleton<IOrdersReadRepository, PostgresOrdersRepository>();
builder.Services.AddSingleton<IObjectStorage, MinioStorage>();
builder.Services.AddSingleton<HistoryService>();
builder.Services.AddSingleton<ReportService>();
builder.Services.AddSingleton<ExportService>();

var app = builder.Build();

// ---- Errores uniformes: { code, message, request_id, details? } ----
static Task WriteError(HttpContext ctx, int status, string code, string message, object? details = null)
{
    ctx.Response.StatusCode = status;
    return ctx.Response.WriteAsJsonAsync(new
    {
        code,
        message,
        request_id = ctx.Items["request_id"] as string,
        details,
    }, new JsonSerializerOptions { DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull });
}

app.Use(async (ctx, next) =>
{
    var requestId = ctx.Request.Headers["X-Request-Id"].FirstOrDefault() ?? Guid.NewGuid().ToString("N");
    ctx.Items["request_id"] = requestId;
    ctx.Response.Headers["X-Request-Id"] = requestId;
    await next();
});

app.Use(async (ctx, next) =>
{
    try
    {
        await next();
    }
    catch (ApiException ex)
    {
        await WriteError(ctx, ex.Status, ex.Code, ex.Message, ex.Details);
    }
    catch (BadHttpRequestException ex)
    {
        await WriteError(ctx, 400, "bad_request", ex.Message);
    }
    catch (Exception ex)
    {
        app.Logger.LogError(ex, "Error no controlado request_id={RequestId}", ctx.Items["request_id"]);
        await WriteError(ctx, 500, "internal_error", "Error interno del servidor");
    }
});

app.UseStatusCodePages(ctx =>
{
    var status = ctx.HttpContext.Response.StatusCode;
    var (code, message) = status switch
    {
        401 => ("unauthorized", "Token ausente, inválido o expirado"),
        403 => ("forbidden", "No tienes permiso para esta operación"),
        404 => ("not_found", "Recurso no encontrado"),
        _ => ($"http_{status}", "Error"),
    };
    return WriteError(ctx.HttpContext, status, code, message);
});

app.UseSwagger();
app.UseSwaggerUI(o =>
{
    o.RoutePrefix = "docs";
    o.SwaggerEndpoint("/swagger/v1/swagger.json", "Reporting Service v1");
});

app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();

app.MapGet("/health", async (IOrdersReadRepository orders, HttpContext ctx) =>
{
    try
    {
        await orders.PingAsync(ctx.RequestAborted);
        return Results.Json(new { status = "ok", service = "reporting-service" });
    }
    catch (Exception ex)
    {
        return Results.Json(new { code = "service_unavailable", message = $"PostgreSQL no disponible: {ex.Message}" }, statusCode: 503);
    }
}).AllowAnonymous();

app.Run();

public partial class Program { }
