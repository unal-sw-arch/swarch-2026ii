part of '../admin_api.dart';

RegisteredAdmin _parseAdmin(Object? value) {
  if (value is! Map<String, dynamic> ||
      value['id'] is! int ||
      value['fullName'] is! String ||
      value['username'] is! String ||
      value['role'] is! String) {
    throw const AdminRegistrationException('INVALID_RESPONSE');
  }
  return RegisteredAdmin(
    id: value['id'] as int,
    fullName: value['fullName'] as String,
    username: value['username'] as String,
    role: value['role'] as String,
  );
}

Future<AdminSession> loginAdmin({
  required String username,
  required String password,
}) async {
  try {
    final endpoint = getCloudApiBaseUri().resolve('/api/v1/auth/login');
    final response = await http
        .post(
          endpoint,
          headers: const {'content-type': 'application/json'},
          body: jsonEncode({'username': username, 'password': password}),
        )
        .timeout(const Duration(seconds: 8));
    debugPrint('Admin login: HTTP ${response.statusCode}');
    final body = jsonDecode(response.body);
    if (response.statusCode == 200 &&
        body is Map<String, dynamic> &&
        body['token'] is String &&
        body['expiresAt'] is String) {
      return AdminSession(
        token: body['token'] as String,
        expiresAt: DateTime.parse(body['expiresAt'] as String),
        admin: _parseAdmin(body['user']),
      );
    }
    final code = body is Map<String, dynamic> && body['error'] is String
        ? body['error'] as String
        : 'INVALID_RESPONSE';
    throw AdminRegistrationException(code, statusCode: response.statusCode);
  } on AdminRegistrationException {
    rethrow;
  } on Object catch (error) {
    throw AdminRegistrationException(
      'SERVER_UNAVAILABLE',
      details: error.toString(),
    );
  }
}

Future<AdminSession?> verifyAdminSession(String token) async {
  try {
    final endpoint = getCloudApiBaseUri().resolve('/api/v1/auth/session');
    final response = await http
        .get(endpoint, headers: {'authorization': 'Bearer $token'})
        .timeout(const Duration(seconds: 8));
    if (response.statusCode == 401) return null;
    final body = jsonDecode(response.body);
    if (response.statusCode != 200 ||
        body is! Map<String, dynamic> ||
        body['expiresAt'] is! String) {
      return null;
    }
    return AdminSession(
      token: token,
      expiresAt: DateTime.parse(body['expiresAt'] as String),
      admin: _parseAdmin(body['user']),
    );
  } on Object {
    return null;
  }
}

Future<void> logoutAdmin(String token) async {
  try {
    final endpoint = getCloudApiBaseUri().resolve('/api/v1/auth/logout');
    await http
        .post(endpoint, headers: {'authorization': 'Bearer $token'})
        .timeout(const Duration(seconds: 5));
  } on Object {
    /* La sesión local se elimina incluso si el servidor se cerró. */
  }
}

class AdminRegistrationException implements Exception {
  const AdminRegistrationException(this.code, {this.statusCode, this.details});

  final String code;
  final int? statusCode;
  final String? details;

  @override
  String toString() =>
      'AdminRegistrationException(code: $code, '
      'statusCode: $statusCode, details: $details)';
}

Map<String, String> _headers(
  String token, {
  bool mutation = false,
  bool json = false,
}) {
  final key = mutation ? _newIdempotencyKey() : null;
  if (key != null) {
    _localMutationIds.add(key);
    if (_localMutationIds.length > 256) {
      _localMutationIds.remove(_localMutationIds.first);
    }
  }
  return {
    'authorization': 'Bearer $token',
    if (json) 'content-type': 'application/json',
    'Idempotency-Key': ?key,
  };
}

final _localMutationIds = <String>{};

bool isLocalAdminMutation(String? mutationId) =>
    mutationId != null && _localMutationIds.contains(mutationId);

String _newIdempotencyKey() {
  final random = math.Random.secure();
  final bytes = List<int>.generate(18, (_) => random.nextInt(256));
  return 'flutter-${base64Url.encode(bytes).replaceAll('=', '')}';
}

Future<RegisteredAdmin> registerAdmin({
  required String fullName,
  required String username,
  required String password,
}) async {
  try {
    final baseUri = getCloudApiBaseUri();
    final endpoint = baseUri.resolve('/api/v1/auth/register');
    debugPrint('Admin register: POST $endpoint');
    final response = await http
        .post(
          endpoint,
          headers: const {'content-type': 'application/json'},
          body: jsonEncode({
            'fullName': fullName,
            'username': username,
            'password': password,
          }),
        )
        .timeout(const Duration(seconds: 8));

    debugPrint('Admin register: HTTP ${response.statusCode} ${response.body}');
    Object? body;
    try {
      body = jsonDecode(response.body);
    } on FormatException {
      throw AdminRegistrationException(
        'HTTP_${response.statusCode}',
        statusCode: response.statusCode,
        details: response.body,
      );
    }
    if (response.statusCode == 201 && body is Map<String, dynamic>) {
      final admin = body['admin'];
      if (admin is Map<String, dynamic> &&
          admin['id'] is int &&
          admin['fullName'] is String &&
          admin['username'] is String &&
          admin['role'] is String) {
        return RegisteredAdmin(
          id: admin['id'] as int,
          fullName: admin['fullName'] as String,
          username: admin['username'] as String,
          role: admin['role'] as String,
        );
      }
    }

    if (body is Map<String, dynamic> && body['error'] is String) {
      throw AdminRegistrationException(
        body['error'] as String,
        statusCode: response.statusCode,
        details: body['message']?.toString(),
      );
    }
    throw AdminRegistrationException(
      'INVALID_RESPONSE',
      statusCode: response.statusCode,
      details: response.body,
    );
  } on AdminRegistrationException catch (error) {
    debugPrint('Admin register failed: $error');
    rethrow;
  } on Object catch (error, stackTrace) {
    debugPrint('Admin register failed: $error');
    debugPrintStack(stackTrace: stackTrace);
    throw AdminRegistrationException(
      'SERVER_UNAVAILABLE',
      details: error.toString(),
    );
  }
}
