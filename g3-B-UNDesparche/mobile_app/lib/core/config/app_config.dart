/// Configuracion global de la aplicacion movil UNDesparche.
abstract class AppConfig {
  /// Flag para habilitar o deshabilitar las llamadas mockeadas.
  /// Por defecto en false para conectar al API Gateway real.
  static const bool useMockData = bool.fromEnvironment('USE_MOCKS', defaultValue: false);

  /// Autenticación real con Firebase. Es independiente de [useMockData]:
  /// Firebase Auth funciona sin el gateway de datos. El perfil del backend
  /// (users_service) se consulta solo cuando [useMockData] es false.
  static const bool useFirebaseAuth = bool.fromEnvironment('USE_FIREBASE_AUTH', defaultValue: true);

  /// Dominio institucional exigido por la regla RN-01.
  static const String institutionalDomain = 'unal.edu.co';

  /// URL base para el API Gateway centralizado (FastAPI en puerto 8000).
  /// Con adb reverse tcp:8000 tcp:8000 en teléfono físico o en Web, se usa http://127.0.0.1:8000.
  static const String apiGatewayUrl = String.fromEnvironment(
    'API_GATEWAY_URL',
    defaultValue: 'http://127.0.0.1:8000',
  );

  /// URLs individuales si se conectara directo sin gateway (modo dev antiguo)
  static const String eventsServiceUrl = 'http://10.0.2.2:8002';
  static const String usersServiceUrl = 'http://10.0.2.2:8001';
  static const String inventoryServiceUrl = 'http://10.0.2.2:8003';

  /// Latencia de red simulada en los repositorios mockeados (milisegundos)
  static const int mockNetworkDelayMs = 600;

  /// Coordenadas centrales del campus de la Universidad Nacional (Sede Bogota)
  static const double unalBogotaLat = 4.6372;
  static const double unalBogotaLng = -74.0841;

  /// Regla de negocio RN-02: Minutos maximos para reclamar un implemento reservado
  static const int reservationTimeoutMinutes = 10;
}
