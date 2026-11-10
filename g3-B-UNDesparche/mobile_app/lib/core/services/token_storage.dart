import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Guarda el token de sesión de forma segura (Keystore en Android), para que
/// la sesión sobreviva al cierre de la app (#3). Equivale al localStorage
/// `undesparche_auth_token` de la web, pero cifrado.
class TokenStorage {
  static const _key = 'undesparche_auth_token';
  final FlutterSecureStorage _storage;

  TokenStorage({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  Future<void> save(String token) => _storage.write(key: _key, value: token);

  Future<String?> read() => _storage.read(key: _key);

  Future<void> clear() => _storage.delete(key: _key);
}
