part of '../admin_api.dart';

Future<List<EmployeeAccount>> getEmployees(String token) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/admin/employees');
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 ||
      body is! Map<String, dynamic> ||
      body['employees'] is! List) {
    throw AdminRegistrationException(
      'EMPLOYEES_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return (body['employees'] as List)
      .map((value) => EmployeeAccount.fromJson(value as Map<String, dynamic>))
      .toList();
}

Future<EmployeeAccount> createEmployee({
  required String token,
  required String fullName,
  required String username,
  required String password,
  required String role,
  required List<int> roomIds,
}) => _writeEmployee(
  token: token,
  method: 'POST',
  path: '/api/v1/admin/employees',
  body: {
    'fullName': fullName,
    'username': username,
    'password': password,
    'role': role,
    'roomIds': roomIds,
  },
);

Future<EmployeeAccount> updateEmployee({
  required String token,
  required int id,
  required String role,
  required List<int> roomIds,
  String? password,
}) => _writeEmployee(
  token: token,
  method: 'PATCH',
  path: '/api/v1/admin/employees/$id',
  body: {
    'role': role,
    'roomIds': roomIds,
    if (password != null && password.isNotEmpty) 'password': password,
  },
);

Future<EmployeeAccount> _writeEmployee({
  required String token,
  required String method,
  required String path,
  required Map<String, Object> body,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(path);
  final request = http.Request(method, endpoint)
    ..headers.addAll(_headers(token, mutation: true, json: true))
    ..body = jsonEncode(body);
  final streamed = await request.send().timeout(const Duration(seconds: 12));
  final response = await http.Response.fromStream(streamed);
  final decoded = jsonDecode(response.body);
  if ((response.statusCode == 200 || response.statusCode == 201) &&
      decoded is Map<String, dynamic> &&
      decoded['employee'] is Map<String, dynamic>) {
    return EmployeeAccount.fromJson(
      decoded['employee'] as Map<String, dynamic>,
    );
  }
  final code = decoded is Map<String, dynamic> && decoded['error'] is String
      ? decoded['error'] as String
      : 'EMPLOYEE_WRITE_FAILED';
  throw AdminRegistrationException(code, statusCode: response.statusCode);
}

Future<void> deleteEmployee({required String token, required int id}) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/admin/employees/$id');
  final response = await http
      .delete(endpoint, headers: _headers(token, mutation: true))
      .timeout(const Duration(seconds: 8));
  if (response.statusCode != 204) {
    throw AdminRegistrationException(
      'EMPLOYEE_DELETE_FAILED',
      statusCode: response.statusCode,
    );
  }
}
