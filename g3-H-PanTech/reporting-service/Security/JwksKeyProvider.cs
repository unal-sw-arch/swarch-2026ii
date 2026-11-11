using System.Security.Cryptography;
using Microsoft.IdentityModel.Tokens;

namespace ReportingService.Security;

/// <summary>
/// Claves públicas RS256 de identity-service (JWKS), cacheadas. Si llega un `kid` desconocido se
/// vuelve a consultar (como mucho una vez cada 30 s). `Jwt:PublicKeyPem` tiene prioridad (dev/pruebas).
/// </summary>
public sealed class JwksKeyProvider
{
    private static readonly TimeSpan Ttl = TimeSpan.FromMinutes(5);
    private static readonly TimeSpan MinRefresh = TimeSpan.FromSeconds(30);

    private readonly IConfiguration _config;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(5) };
    private readonly object _lock = new();
    private IReadOnlyList<SecurityKey> _keys = Array.Empty<SecurityKey>();
    private DateTime _fetchedAt = DateTime.MinValue;
    private DateTime _lastAttempt = DateTime.MinValue;

    public JwksKeyProvider(IConfiguration config) => _config = config;

    public IEnumerable<SecurityKey> GetKeys(string? kid)
    {
        var pem = _config["Jwt:PublicKeyPem"];
        var pemFile = _config["Jwt:PublicKeyFile"];
        if (string.IsNullOrWhiteSpace(pem) && !string.IsNullOrWhiteSpace(pemFile))
            pem = File.ReadAllText(pemFile);
        if (!string.IsNullOrWhiteSpace(pem))
        {
            var rsa = RSA.Create();
            rsa.ImportFromPem(pem);
            return new[] { new RsaSecurityKey(rsa) };
        }

        lock (_lock)
        {
            var expired = DateTime.UtcNow - _fetchedAt > Ttl;
            var unknownKid = kid != null && !_keys.Any(k => k.KeyId == kid);
            if ((expired || unknownKid || _keys.Count == 0) && DateTime.UtcNow - _lastAttempt > MinRefresh)
            {
                _lastAttempt = DateTime.UtcNow;
                try
                {
                    var json = _http.GetStringAsync(_config["JWKS_URL"] ?? _config["Jwt:JwksUrl"]).GetAwaiter().GetResult();
                    _keys = new JsonWebKeySet(json).GetSigningKeys().ToList();
                    _fetchedAt = DateTime.UtcNow;
                }
                catch (Exception)
                {
                    // Se conservan las claves previas; sin claves, la validación fallará con 401.
                }
            }
            return _keys;
        }
    }
}
