import 'package:flutter_secure_storage/flutter_secure_storage.dart';

const _storage = FlutterSecureStorage();
const _tokenKey = 'employee_session_token';

Future<void> saveClientSessionToken(String token) =>
    _storage.write(key: _tokenKey, value: token);

Future<String?> readClientSessionToken() => _storage.read(key: _tokenKey);

Future<void> clearClientSessionToken() => _storage.delete(key: _tokenKey);
