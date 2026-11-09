import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/session_store.dart';
import '../models/queue_item.dart';

/// Error de la API ya traducido, con un mensaje claro para mostrar en pantalla.
sealed class ApiException implements Exception {
  const ApiException(this.message);

  /// Mensaje para la persona que usa el KDS.
  final String message;

  @override
  String toString() => message;
}

/// 401: sin token, vencido o inválido. Se vuelve a la pantalla de inicio de sesión.
class UnauthenticatedException extends ApiException {
  const UnauthenticatedException([super.message = 'Su sesión expiró. Inicie sesión de nuevo.']);
}

/// 403: la cuenta no tiene permiso.
class ForbiddenException extends ApiException {
  const ForbiddenException([super.message = 'Su cuenta no tiene permiso para esta operación.']);
}

class NotFoundException extends ApiException {
  const NotFoundException([super.message = 'El pedido ya no existe en la cola.']);
}

class ValidationException extends ApiException {
  const ValidationException(super.message);
}

/// 409 INVALID_STATE: otra persona cambió el pedido antes.
class InvalidStateException extends ApiException {
  InvalidStateException({this.current, this.requested})
      : super('El pedido cambió de estado${current == null ? '' : ' (ahora está "$current")'}. '
            'Se actualizó la cola.');

  final String? current;
  final String? requested;
}

/// 409 KITCHEN_AT_CAPACITY: el pedido sigue pendiente, no se pierde.
class CapacityException extends ApiException {
  CapacityException({this.max, this.inPreparation})
      : super('La cocina está llena${max == null ? '' : ' ($inPreparation de $max en preparación)'}. '
            'El pedido sigue pendiente; termine uno e inténtelo de nuevo.');

  final int? max;
  final int? inPreparation;
}

class RateLimitedException extends ApiException {
  const RateLimitedException([super.message = 'Demasiadas consultas seguidas. Espere unos segundos.']);
}

/// Sin conexión con el Gateway.
class NetworkException extends ApiException {
  const NetworkException([super.message = 'Sin conexión con el Gateway.']);
}

/// 5xx u otra respuesta inesperada.
class ServerException extends ApiException {
  const ServerException(super.message, {this.status});

  final int? status;
}

/// Respuesta de `POST /auth/login` y `POST /auth/refresh`.
///
/// SUPUESTO (Diego no definió el cuerpo): `{access_token, refresh_token, token_type, expires_in}`.
/// Es el único lugar donde se interpreta; si Identity responde otra forma, se cambia aquí.
class LoginResponse {
  const LoginResponse({required this.accessToken, this.refreshToken, this.tokenType = 'Bearer', this.expiresIn});

  final String accessToken;
  final String? refreshToken;
  final String tokenType;
  final int? expiresIn;

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    final access = json['access_token'];
    if (access is! String || access.isEmpty) {
      throw const FormatException('La respuesta de inicio de sesión no trae access_token');
    }
    return LoginResponse(
      accessToken: access,
      refreshToken: json['refresh_token'] as String?,
      tokenType: json['token_type'] as String? ?? 'Bearer',
      expiresIn: (json['expires_in'] as num?)?.toInt(),
    );
  }
}

/// Cliente del API Gateway. El KDS nunca llama a Kitchen directo.
class GatewayClient {
  GatewayClient({
    required String baseUrl,
    required http.Client httpClient,
    this.timeout = const Duration(seconds: 8),
  })  : _base = Uri.parse(baseUrl),
        _http = httpClient;

  final Uri _base;
  final http.Client _http;
  final Duration timeout;

  /// Sesión vigente; la asigna el controlador de sesión.
  Session? session;

  /// Se llama con la sesión renovada, o con `null` cuando el Gateway rechazó el token (401) y
  /// no se pudo renovar: la app debe volver al inicio de sesión.
  Future<void> Function(Session? session)? onSessionChanged;

  Future<Session>? _refreshing;

  Uri _uri(String path, [Map<String, String>? query]) =>
      _base.replace(path: '${_base.path}$path'.replaceAll('//', '/'), queryParameters: query);

  // --- Autenticación -----------------------------------------------------------------------

  /// `POST /auth/login`. SUPUESTO: cuerpo `{email, password}`.
  Future<Session> login(String email, String password) async {
    final res = await _send(() => _http.post(
          _uri('/auth/login'),
          headers: const {'Content-Type': 'application/json', 'Accept': 'application/json'},
          body: jsonEncode({'email': email, 'password': password}),
        ));
    final body = _checked(res, loginRequest: true);
    try {
      final login = LoginResponse.fromJson(body);
      return Session.fromTokens(
        accessToken: login.accessToken,
        refreshToken: login.refreshToken,
        tokenType: login.tokenType,
      );
    } on FormatException catch (e) {
      throw ServerException('Respuesta de inicio de sesión no válida: ${e.message}');
    }
  }

  /// `POST /auth/refresh`. SUPUESTO: cuerpo `{refresh_token}`.
  Future<Session> _refresh(Session current) {
    final inFlight = _refreshing;
    if (inFlight != null) return inFlight;
    return _refreshing = _doRefresh(current).whenComplete(() => _refreshing = null);
  }

  Future<Session> _doRefresh(Session current) async {
    final refreshToken = current.refreshToken;
    if (refreshToken == null) throw const UnauthenticatedException();
    final res = await _send(() => _http.post(
          _uri('/auth/refresh'),
          headers: const {'Content-Type': 'application/json', 'Accept': 'application/json'},
          body: jsonEncode({'refresh_token': refreshToken}),
        ));
    final body = _checked(res);
    try {
      final login = LoginResponse.fromJson(body);
      return current.withTokens(
        accessToken: login.accessToken,
        refreshToken: login.refreshToken,
        tokenType: login.tokenType,
      );
    } on FormatException {
      throw const UnauthenticatedException();
    }
  }

  // --- Cola de cocina ----------------------------------------------------------------------

  Future<QueueSnapshot> fetchQueue({String? status}) async {
    final body = await _authed((headers) => _http.get(
          _uri('/queue', status == null ? null : {'status': status}),
          headers: headers,
        ));
    return QueueSnapshot.fromJson(body);
  }

  /// `PATCH /queue/orders/{id}/status` (`en_preparacion` o `listo`).
  Future<QueueItem> setStatus(String orderId, String status) async {
    final body = await _authed((headers) => _http.patch(
          _uri('/queue/orders/$orderId/status'),
          headers: {...headers, 'Content-Type': 'application/json'},
          body: jsonEncode({'status': status}),
        ));
    return QueueItem.fromJson(body);
  }

  /// `POST /queue/orders/{id}/deliver`.
  Future<QueueItem> deliver(String orderId) async {
    final body = await _authed((headers) => _http.post(_uri('/queue/orders/$orderId/deliver'), headers: headers));
    return QueueItem.fromJson(body);
  }

  // --- Internos ----------------------------------------------------------------------------

  /// Envía con el token; ante un 401 intenta renovar la sesión una vez y reintenta. Si no se
  /// puede, avisa con `onSessionChanged(null)` y lanza [UnauthenticatedException].
  Future<Map<String, dynamic>> _authed(Future<http.Response> Function(Map<String, String> headers) send) async {
    var current = session;
    if (current == null) throw const UnauthenticatedException();

    Map<String, String> headers(Session s) =>
        {'Authorization': '${s.tokenType} ${s.accessToken}', 'Accept': 'application/json'};

    var res = await _send(() => send(headers(current!)));
    if (res.statusCode == 401) {
      try {
        current = await _refresh(current);
        session = current;
        await onSessionChanged?.call(current);
      } on UnauthenticatedException {
        session = null;
        await onSessionChanged?.call(null);
        throw const UnauthenticatedException();
      }
      res = await _send(() => send(headers(current!)));
      if (res.statusCode == 401) {
        session = null;
        await onSessionChanged?.call(null);
        throw const UnauthenticatedException();
      }
    }
    return _checked(res);
  }

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request().timeout(timeout);
    } on TimeoutException {
      throw const NetworkException('El Gateway no respondió a tiempo.');
    } on Exception {
      throw const NetworkException();
    }
  }

  /// Devuelve el cuerpo JSON de una respuesta 2xx o lanza la excepción que corresponda.
  Map<String, dynamic> _checked(http.Response res, {bool loginRequest = false}) {
    if (res.statusCode >= 200 && res.statusCode < 300) {
      try {
        return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
      } on Object {
        throw const ServerException('El Gateway devolvió una respuesta que no es JSON.');
      }
    }

    Map<String, dynamic> error = const {};
    try {
      error = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    } on Object {
      error = const {};
    }
    final code = error['code'] as String?;
    final details = error['details'] as Map<String, dynamic>? ?? const {};

    switch (res.statusCode) {
      case 400:
        throw ValidationException(error['message'] as String? ?? 'La petición no es válida.');
      case 401:
        throw loginRequest
            ? const UnauthenticatedException('Correo o contraseña incorrectos.')
            : const UnauthenticatedException();
      case 403:
        throw const ForbiddenException();
      case 404:
        throw const NotFoundException();
      case 409:
        if (code == 'KITCHEN_AT_CAPACITY') {
          throw CapacityException(
            max: (details['max'] as num?)?.toInt(),
            inPreparation: (details['in_preparation'] as num?)?.toInt(),
          );
        }
        throw InvalidStateException(
          current: details['current_status'] as String?,
          requested: details['requested_status'] as String?,
        );
      case 429:
        throw const RateLimitedException();
      case 502 || 503 || 504:
        throw ServerException('El servicio no está disponible. Reintente en unos segundos.', status: res.statusCode);
      default:
        throw ServerException('Error inesperado del servidor (${res.statusCode}).', status: res.statusCode);
    }
  }
}
