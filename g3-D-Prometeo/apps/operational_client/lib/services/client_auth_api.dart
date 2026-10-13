import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../models/client_user.dart';
import 'client_session_store.dart';
import 'cloud_api.dart';

class ClientLoginException implements Exception {
  const ClientLoginException(this.code);
  final String code;
}

Future<ClientSession> loginClient(String username, String password) async {
  try {
    final response = await http
        .post(
          cloudApiUri('/api/v1/auth/login'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'username': username, 'password': password}),
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 401) {
      throw const ClientLoginException('INVALID_CREDENTIALS');
    }
    if (response.statusCode != 200) {
      throw ClientLoginException('HTTP_${response.statusCode}');
    }
    final decoded = jsonDecode(response.body);
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
      debugPrint('No se pudo guardar la sesión de forma segura: $error');
    }
    return session;
  } on ClientLoginException {
    rethrow;
  } on Object {
    throw const ClientLoginException('CONNECTION_FAILED');
  }
}

Future<ClientSession?> verifyClientSession(String token) async {
  try {
    final response = await http
        .get(
          cloudApiUri('/api/v1/auth/session'),
          headers: {'Authorization': 'Bearer $token'},
        )
        .timeout(const Duration(seconds: 10));
    if (response.statusCode == 401) return null;
    if (response.statusCode != 200) {
      throw ClientLoginException('HTTP_${response.statusCode}');
    }
    final decoded = jsonDecode(response.body);
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
  try {
    await http
        .post(
          cloudApiUri('/api/v1/auth/logout'),
          headers: {'Authorization': 'Bearer ${session.token}'},
        )
        .timeout(const Duration(seconds: 10));
  } on Object {
    // El cierre local debe funcionar incluso si el servidor está desconectado.
  } finally {
    try {
      await clearClientSessionToken();
    } on Object {
      // La interfaz igualmente debe volver al login.
    }
  }
}
