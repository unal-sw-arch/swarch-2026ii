part of '../admin_api.dart';

Future<AdminOverviewMetrics> getAdminOverview(
  String token, {
  String period = 'day',
  int range = 7,
}) async {
  final endpoint = getCloudApiBaseUri().resolve(
    '/api/v1/admin/overview?period=$period&range=$range',
  );
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 || body is! Map<String, dynamic>) {
    throw AdminRegistrationException(
      'OVERVIEW_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return AdminOverviewMetrics.fromJson(body);
}

Future<List<AdminActivity>> getAdminActivities(String token) async {
  final endpoint = getCloudApiBaseUri().resolve('/api/v1/admin/activities');
  final response = await http
      .get(endpoint, headers: {'authorization': 'Bearer $token'})
      .timeout(const Duration(seconds: 8));
  final body = jsonDecode(response.body);
  if (response.statusCode != 200 ||
      body is! Map<String, dynamic> ||
      body['activities'] is! List) {
    throw AdminRegistrationException(
      'ACTIVITIES_LOAD_FAILED',
      statusCode: response.statusCode,
    );
  }
  return (body['activities'] as List)
      .map((value) => AdminActivity.fromJson(value as Map<String, dynamic>))
      .toList();
}

Stream<AdminActivity> watchAdminActivities(String token) async* {
  while (true) {
    WebSocket? socket;
    try {
      final base = getCloudApiBaseUri();
      final endpoint = base
          .resolve('/api/v1/realtime')
          .replace(scheme: base.scheme == 'https' ? 'wss' : 'ws');
      socket = await WebSocket.connect(
        endpoint.toString(),
        headers: {'authorization': 'Bearer $token'},
      ).timeout(const Duration(seconds: 10));
      socket.add(jsonEncode({'type': 'subscribe-admin'}));
      await for (final raw in socket) {
        if (raw is! String) continue;
        final value = jsonDecode(raw);
        if (value is! Map<String, dynamic> ||
            value['type'] != 'activity.created' ||
            value['payload'] is! Map) {
          continue;
        }
        final payload = Map<String, dynamic>.from(value['payload'] as Map);
        final activity = payload['activity'];
        if (activity is Map) {
          yield AdminActivity.fromJson(Map<String, dynamic>.from(activity));
        }
      }
    } on Object {
      await Future<void>.delayed(const Duration(seconds: 2));
    } finally {
      await socket?.close();
    }
  }
}
