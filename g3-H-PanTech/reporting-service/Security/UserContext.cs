using System.Security.Claims;
using ReportingService.Models;

namespace ReportingService.Security;

public sealed record UserContext(string Id, IReadOnlySet<string> Permissions)
{
    public bool Has(string permission) => Permissions.Contains(permission);

    public static UserContext From(ClaimsPrincipal principal)
    {
        var id = principal.FindFirst("sub")?.Value;
        if (string.IsNullOrEmpty(id))
            throw new ApiException(401, "unauthorized", "Token sin sub");
        var permissions = principal.FindAll("permissions").Select(c => c.Value).ToHashSet();
        return new UserContext(id, permissions);
    }
}
