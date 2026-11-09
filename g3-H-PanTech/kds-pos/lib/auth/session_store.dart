import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Roles que pueden entrar al KDS.
const kdsRoles = {'cocinero', 'despachador', 'supervisor', 'admin'};

/// Sesión del personal. Los datos de identidad (roles, permisos) se leen de los claims del
/// JWT de acceso; solo se guardan los tokens.
///
/// SUPUESTO de Diego (cuerpo de `POST /auth/login`): `{access_token, refresh_token, token_type,
/// expires_in}`. Si cambia, se ajusta `LoginResponse.fromJson` en `gateway_client.dart`.
class Session {
  Session({
    required this.accessToken,
    this.refreshToken,
    this.tokenType = 'Bearer',
    required this.userId,
    required this.email,
    required this.roles,
    required this.permissions,
  });

  final String accessToken;
  final String? refreshToken;
  final String tokenType;
  final String userId;
  final String email;
  final List<String> roles;
  final List<String> permissions;

  /// Lee los claims del JWT (`sub`, `email`, `roles`, `permissions`). No verifica la firma:
  /// eso lo hace el Gateway en cada petición. Lanza [FormatException] si el token no es un JWT.
  factory Session.fromTokens({
    required String accessToken,
    String? refreshToken,
    String tokenType = 'Bearer',
  }) {
    final claims = decodeClaims(accessToken);
    List<String> list(Object? v) => [for (final e in (v as List<dynamic>? ?? const [])) e.toString()];
    return Session(
      accessToken: accessToken,
      refreshToken: refreshToken,
      tokenType: tokenType,
      userId: claims['sub'] as String? ?? '',
      email: claims['email'] as String? ?? '',
      roles: list(claims['roles']),
      permissions: list(claims['permissions']),
    );
  }

  bool get canAccessKds => roles.any(kdsRoles.contains);

  /// Vista de cocina: cocinero, supervisor o admin.
  bool get canSeeKitchen => roles.any(const {'cocinero', 'supervisor', 'admin'}.contains);

  /// Vista de despacho: despachador, supervisor o admin.
  bool get canSeeDispatch => roles.any(const {'despachador', 'supervisor', 'admin'}.contains);

  /// El Gateway y Kitchen validan el permiso; aquí solo se decide qué botones mostrar.
  bool get canUpdate => permissions.contains('kitchen:update');
  bool get canDeliver => permissions.contains('kitchen:deliver');

  Session withTokens({required String accessToken, String? refreshToken, String? tokenType}) =>
      Session.fromTokens(
        accessToken: accessToken,
        refreshToken: refreshToken ?? this.refreshToken,
        tokenType: tokenType ?? this.tokenType,
      );

  Map<String, dynamic> toJson() => {
        'access_token': accessToken,
        'refresh_token': refreshToken,
        'token_type': tokenType,
      };

  static Map<String, dynamic> decodeClaims(String jwt) {
    final parts = jwt.split('.');
    if (parts.length != 3) throw const FormatException('El token de acceso no es un JWT');
    try {
      final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
      return jsonDecode(payload) as Map<String, dynamic>;
    } on Object {
      throw const FormatException('No se pudo leer el contenido del token de acceso');
    }
  }
}

/// Dónde se guarda la sesión entre ejecuciones.
abstract class SessionStore {
  Future<Session?> read();
  Future<void> write(Session session);
  Future<void> clear();
}

/// Almacenamiento seguro del sistema (Windows Credential Locker / DPAPI). No se guarda en
/// texto plano ni en un archivo del proyecto. En web (solo demo) lo cifra el navegador.
class SecureSessionStore implements SessionStore {
  SecureSessionStore([FlutterSecureStorage? storage]) : _storage = storage ?? const FlutterSecureStorage();

  static const _key = 'kds_session';
  final FlutterSecureStorage _storage;

  @override
  Future<Session?> read() async {
    try {
      final raw = await _storage.read(key: _key);
      if (raw == null) return null;
      final json = jsonDecode(raw) as Map<String, dynamic>;
      return Session.fromTokens(
        accessToken: json['access_token'] as String,
        refreshToken: json['refresh_token'] as String?,
        tokenType: json['token_type'] as String? ?? 'Bearer',
      );
    } on Object {
      await clear();
      return null;
    }
  }

  @override
  Future<void> write(Session session) => _storage.write(key: _key, value: jsonEncode(session.toJson()));

  @override
  Future<void> clear() => _storage.delete(key: _key);
}

/// Para pruebas.
class MemorySessionStore implements SessionStore {
  MemorySessionStore([this.session]);

  Session? session;

  @override
  Future<Session?> read() async => session;

  @override
  Future<void> write(Session value) async => session = value;

  @override
  Future<void> clear() async => session = null;
}
