import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _storage = FlutterSecureStorage();
const _tokenKey = 'admin_session_token';

Future<void> saveAdminSessionToken(String token) =>
    _storage.write(key: _tokenKey, value: token);

Future<String?> readAdminSessionToken() => _storage.read(key: _tokenKey);

Future<void> clearAdminSessionToken() => _storage.delete(key: _tokenKey);
