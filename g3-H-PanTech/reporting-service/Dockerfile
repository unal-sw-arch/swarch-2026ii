# ---- build ----
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY ReportingService.csproj ./
RUN dotnet restore
COPY . .
RUN dotnet publish ReportingService.csproj -c Release -o /app/publish --no-restore

# ---- runtime ----
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
# QuestPDF (exportaciones PDF) necesita fontconfig; curl es para el healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends libfontconfig1 curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/publish .
ENV PORT=8003
ENV ASPNETCORE_URLS=http://+:8003
EXPOSE 8003
USER app
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=20s \
  CMD curl -fsS http://localhost:8003/health || exit 1
ENTRYPOINT ["dotnet", "ReportingService.dll"]
