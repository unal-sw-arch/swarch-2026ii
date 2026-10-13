part of '../admin_api.dart';

Future<RoomLayoutModel> getRoomLayout({
  required String token,
  required int roomId,
}) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/halls/$roomId/layout');
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 || body is! Map<String, dynamic>) {
    throw AdminRegistrationException(
      'LAYOUT_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return RoomLayoutModel.fromJson(body);
}

Future<List<RoomSummary>> getRooms(String token) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/halls');
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 ||
      body is! Map<String, dynamic> ||
      body['halls'] is! List) {
    throw AdminRegistrationException(
      'ROOMS_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return (body['halls'] as List)
      .map((room) => RoomSummary.fromJson(room as Map<String, dynamic>))
      .toList();
}

Future<RoomSummary> createRoom({
  required String token,
  required String name,
}) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/admin/halls');
  final response = await http
      .post(
        endpoint,
        headers: _headers(token, mutation: true, json: true),
        body: jsonEncode({'name': name}),
      )
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 201 ||
      body is! Map<String, dynamic> ||
      body['room'] is! Map<String, dynamic>) {
    final code = body is Map<String, dynamic> && body['error'] is String
        ? body['error'] as String
        : 'ROOM_CREATE_FAILED';
    throw AdminRegistrationException(code, statusCode: response.statusCode);
  }
  return RoomSummary.fromJson(body['room'] as Map<String, dynamic>);
}

Future<AdminOrderHistorySummary> getOrderHistorySummary({
  required String token,
  required int roomId,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/halls/$roomId/order-history-summary',
  );
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  Object? body;
  try {
    body = jsonDecode(response.body);
  } on FormatException {
    body = null;
  }
  if (response.statusCode != 200 || body is! Map<String, dynamic>) {
    throw AdminRegistrationException(
      'ORDER_HISTORY_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return AdminOrderHistorySummary.fromJson(body);
}

Future<List<AdminOrderHistoryEntry>> getOrderHistoryEntries({
  required String token,
  required int roomId,
  int limit = 50,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/halls/$roomId/order-history?limit=$limit',
  );
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  Object? body;
  try {
    body = jsonDecode(response.body);
  } on FormatException {
    body = null;
  }
  if (response.statusCode != 200 ||
      body is! Map<String, dynamic> ||
      body['entries'] is! List) {
    throw AdminRegistrationException(
      'ORDER_HISTORY_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return (body['entries'] as List)
      .map(
        (entry) =>
            AdminOrderHistoryEntry.fromJson(entry as Map<String, dynamic>),
      )
      .toList();
}

Future<void> deleteRoomOrder({
  required String token,
  required int roomId,
  required int orderId,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/halls/$roomId/orders/$orderId',
  );
  final request = http.Request('DELETE', endpoint)
    ..headers.addAll(_headers(token, mutation: true));
  final streamed = await request.send().timeout(const Duration(seconds: 15));
  final response = await http.Response.fromStream(streamed);
  if (response.statusCode != 200) {
    Object? body;
    try {
      body = jsonDecode(response.body);
    } on FormatException {
      body = null;
    }
    final code = body is Map<String, dynamic> && body['error'] is String
        ? body['error'] as String
        : 'ORDER_HISTORY_DELETE_FAILED';
    throw AdminRegistrationException(code, statusCode: response.statusCode);
  }
}

Future<int> deleteRoomOrders({
  required String token,
  required int roomId,
  required Set<String> statuses,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/halls/$roomId/orders',
  );
  final request = http.Request('DELETE', endpoint)
    ..headers.addAll(_headers(token, mutation: true, json: true))
    ..body = jsonEncode({'statuses': statuses.toList()});
  final streamed = await request.send().timeout(const Duration(seconds: 15));
  final response = await http.Response.fromStream(streamed);
  Object? body;
  try {
    body = jsonDecode(response.body);
  } on FormatException {
    body = null;
  }
  if (response.statusCode != 200 ||
      body is! Map<String, dynamic> ||
      body['deletedCount'] is! num) {
    final code = body is Map<String, dynamic> && body['error'] is String
        ? body['error'] as String
        : 'ORDER_HISTORY_DELETE_FAILED';
    throw AdminRegistrationException(code, statusCode: response.statusCode);
  }
  return (body['deletedCount'] as num).toInt();
}

Future<RoomLayoutModel> saveRoomLayout({
  required String token,
  required int roomId,
  required RoomLayoutModel layout,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/halls/$roomId/layout',
  );
  final response = await http
      .put(
        endpoint,
        headers: _headers(token, mutation: true, json: true),
        body: jsonEncode(layout.toJson()),
      )
      .timeout(const Duration(seconds: 12));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 || body is! Map<String, dynamic>) {
    final code = body is Map<String, dynamic> && body['error'] is String
        ? body['error'] as String
        : 'LAYOUT_SAVE_FAILED';
    throw AdminRegistrationException(code, statusCode: response.statusCode);
  }
  return RoomLayoutModel.fromJson(body);
}
