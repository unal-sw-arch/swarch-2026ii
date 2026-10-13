import 'dart:convert';
import 'dart:io';

import '../models/client_user.dart';
import 'client_session_store.dart';
import 'cloud_api.dart';

class ClientLoginException implements Exception {
  const ClientLoginException(this.code);
  final String code;
}

Future<ClientSession> loginClient(String username, String password) async {
  final client = HttpClient();
  try {
    final request = await client.postUrl(cloudApiUri('/api/v1/auth/login'));
    request.headers.contentType = ContentType.json;
    request.write(jsonEncode({'username': username, 'password': password}));
    final response = await request.close().timeout(const Duration(seconds: 10));
    final body = await utf8.decoder.bind(response).join();
    if (response.statusCode == HttpStatus.unauthorized) {
      throw const ClientLoginException('INVALID_CREDENTIALS');
    }
    if (response.statusCode != HttpStatus.ok) {
      throw ClientLoginException('HTTP_${response.statusCode}');
    }
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic> ||
        decoded['user'] is! Map<String, dynamic> ||
        decoded['token'] is! String ||
        decoded['expiresAt'] is! String) {
      throw const ClientLoginException('INVALID_RESPONSE');
    }
    final session = ClientSession(
      token: decoded['token'] as String,
      expiresAt: DateTime.parse(decoded['expiresAt'] as String),
      user: ClientUser.fromJson(decoded['user'] as Map<String, dynamic>),
    );
    try {
      await saveClientSessionToken(session.token);
    } on Object catch (error) {
      stderr.writeln('No se pudo guardar la sesión de forma segura: $error');
    }
    return session;
  } on ClientLoginException {
    rethrow;
  } on Object {
    throw const ClientLoginException('CONNECTION_FAILED');
  } finally {
    client.close(force: true);
  }
}

Future<ClientSession?> verifyClientSession(String token) async {
  final client = HttpClient();
  try {
    final request = await client.getUrl(cloudApiUri('/api/v1/auth/session'));
    request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
    final response = await request.close().timeout(const Duration(seconds: 10));
    final body = await utf8.decoder.bind(response).join();
    if (response.statusCode == HttpStatus.unauthorized) return null;
    if (response.statusCode != HttpStatus.ok) {
      throw ClientLoginException('HTTP_${response.statusCode}');
    }
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic> ||
        decoded['user'] is! Map<String, dynamic> ||
        decoded['expiresAt'] is! String) {
      throw const ClientLoginException('INVALID_RESPONSE');
    }
    return ClientSession(
      token: token,
      expiresAt: DateTime.parse(decoded['expiresAt'] as String),
      user: ClientUser.fromJson(decoded['user'] as Map<String, dynamic>),
    );
  } on ClientLoginException {
    rethrow;
  } on Object {
    throw const ClientLoginException('CONNECTION_FAILED');
  } finally {
    client.close(force: true);
  }
}

Future<ClientSession?> restoreStoredClientSession({
  Future<String?> Function() readToken = readClientSessionToken,
  Future<void> Function() clearToken = clearClientSessionToken,
  Future<ClientSession?> Function(String token) verify = verifyClientSession,
}) async {
  String? token;
  try {
    token = await readToken();
  } on Object {
    return null;
  }
  if (token == null || token.isEmpty) return null;

  try {
    final session = await verify(token);
    if (session == null) await clearToken();
    return session;
  } on Object {
    // Una falla temporal no invalida las credenciales. Se conserva el token
    // para volver a comprobarlo en el siguiente arranque.
    return null;
  }
}

Future<void> logoutClient(ClientSession session) async {
  final client = HttpClient();
  try {
    final request = await client.postUrl(cloudApiUri('/api/v1/auth/logout'));
    request.headers.set(
      HttpHeaders.authorizationHeader,
      'Bearer ${session.token}',
    );
    final response = await request.close().timeout(const Duration(seconds: 10));
    await response.drain<void>();
  } on Object {
    // El cierre local debe funcionar incluso si el servidor está desconectado.
  } finally {
    client.close(force: true);
    try {
      await clearClientSessionToken();
    } on Object {
      // La interfaz igualmente debe volver al login.
    }
  }
}
