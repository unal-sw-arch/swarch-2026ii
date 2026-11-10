import '../config/app_config.dart';

/// Definicion centralizada de endpoints para los 3 microservicios y el API Gateway
abstract class ApiEndpoints {
  // Base URLs
  static String get baseUrl => AppConfig.apiGatewayUrl;

  // --- MICROSERVICIO DE EVENTOS (events_service :8002 vía Gateway :8000) ---
  static const String events = '/events/';
  static String eventDetail(String id) => '/events/$id/';
  static String eventSubscribe(String id) => '/events/$id/subscribe/';
  static String eventUnsubscribe(String id) => '/events/$id/unsubscribe/';

  // --- MICROSERVICIO DE INVENTARIO (inventory_service :8003 vía Gateway :8000) ---
  static const String implements = '/inventory/implements/';
  static String implementDetail(String id) => '/inventory/implements/$id/';
  static const String reserves = '/inventory/reserves/';
  static String reserveDetail(String id) => '/inventory/reserves/$id/';
  static String reserveCancel(String id) => '/inventory/reserves/$id/cancel/';
  static String reserveConfirm(String id) => '/inventory/reserves/$id/confirm/';

  // Aliases para retrocompatibilidad
  static const String reservations = reserves;
  static String reservationDetail(String id) => reserveDetail(id);

  // --- MICROSERVICIO DE USUARIOS (users_service :8001 vía Gateway :8000) ---
  static const String users = '/users/';
  static const String currentUser = '/users/me/';
  static String userDetail(String id) => '/users/$id/';
}
