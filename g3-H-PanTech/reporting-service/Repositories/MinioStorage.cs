using Minio;
using Minio.DataModel.Args;
using ReportingService.Models;

namespace ReportingService.Repositories;

/// <summary>Reportes en RustFS (compatible con S3). Usa las variables S3_* del compose; MinIO:* queda como alternativa local.</summary>
public sealed class MinioStorage : IObjectStorage
{
    private readonly IConfiguration _config;
    private readonly IMinioClient _client;
    private readonly IMinioClient _presignClient;
    private readonly string _bucket;
    private bool _bucketReady;

    public MinioStorage(IConfiguration config)
    {
        _config = config;
        _bucket = config["S3_BUCKET_REPORTS"] ?? config["MinIO:ReportsBucket"] ?? "reports";
        var region = config["S3_REGION"] ?? "us-east-1";
        var (host, secure) = ParseEndpoint(config["S3_ENDPOINT"] ?? config["MinIO:Endpoint"] ?? "http://rustfs:9000", UseSsl(config));
        _client = Build(host, secure, region);

        // La URL firmada debe usar el host que verá el navegador (S3_PUBLIC_ENDPOINT); firmar no requiere red si se fija la región.
        var publicEndpoint = config["S3_PUBLIC_ENDPOINT"] ?? config["MinIO:PublicEndpoint"];
        if (string.IsNullOrWhiteSpace(publicEndpoint)) _presignClient = _client;
        else
        {
            var (publicHost, publicSecure) = ParseEndpoint(publicEndpoint, secure);
            _presignClient = Build(publicHost, publicSecure, region);
        }
    }

    private static bool UseSsl(IConfiguration config) =>
        bool.TryParse(config["S3_USE_SSL"] ?? config["MinIO:Secure"], out var s) && s;

    /// <summary>Acepta "http://host:9000" o "host:9000" y devuelve (host:puerto, https?).</summary>
    public static (string Host, bool Secure) ParseEndpoint(string endpoint, bool defaultSecure)
    {
        if (!endpoint.Contains("://")) return (endpoint, defaultSecure);
        var uri = new Uri(endpoint);
        return (uri.Authority, uri.Scheme == Uri.UriSchemeHttps);
    }

    private IMinioClient Build(string host, bool secure, string region) =>
        new MinioClient()
            .WithEndpoint(host)
            .WithCredentials(_config["S3_ACCESS_KEY"] ?? _config["MinIO:AccessKey"], _config["S3_SECRET_KEY"] ?? _config["MinIO:SecretKey"])
            .WithSSL(secure)
            .WithRegion(region)
            .Build();

    public async Task<ExportResult> SaveAsync(string objectName, byte[] data, string contentType, string format, CancellationToken ct)
    {
        if (!_bucketReady)
        {
            var exists = await _client.BucketExistsAsync(new BucketExistsArgs().WithBucket(_bucket), ct);
            if (!exists) await _client.MakeBucketAsync(new MakeBucketArgs().WithBucket(_bucket), ct); // normalmente ya lo crea rustfs-init
            _bucketReady = true;
        }

        using var stream = new MemoryStream(data);
        await _client.PutObjectAsync(new PutObjectArgs()
            .WithBucket(_bucket)
            .WithObject(objectName)
            .WithStreamData(stream)
            .WithObjectSize(data.Length)
            .WithContentType(contentType), ct);

        var expiry = int.TryParse(_config["MinIO:UrlExpirySeconds"], out var e) ? e : 3600;
        var url = await _presignClient.PresignedGetObjectAsync(new PresignedGetObjectArgs()
            .WithBucket(_bucket).WithObject(objectName).WithExpiry(expiry));
        return new ExportResult(url, $"{_bucket}/{objectName}", format, contentType, data.Length, DateTime.UtcNow.AddSeconds(expiry));
    }
}
