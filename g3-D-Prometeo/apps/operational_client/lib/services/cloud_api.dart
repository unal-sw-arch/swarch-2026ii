import 'dart:convert';
import 'dart:math' as math;

import 'package:http/http.dart' as http;

/// Origin of the Cloudflare Worker used by the application.
///
/// Production/profile builds should provide it with:
/// `--dart-define=RESTAURANT_API_URL=https://example.workers.dev`.
const _configuredOrigin = String.fromEnvironment(
  'RESTAURANT_API_URL',
  defaultValue: 'https://restauranteapp-cloud.daaguilarc.workers.dev',
);

Uri get cloudApiOrigin {
  final value = Uri.parse(_configuredOrigin.trim());
  if (!value.hasScheme || value.host.isEmpty) {
    throw const FormatException('INVALID_RESTAURANT_API_URL');
  }
  return value.replace(path: '/', query: null, fragment: null);
}

Uri cloudApiUri(String path) => cloudApiOrigin.resolve(path);

Uri cloudWebSocketUri(String path) {
  final uri = cloudApiUri(path);
  return uri.replace(scheme: uri.scheme == 'https' ? 'wss' : 'ws');
}

/// Realtime endpoint URI carrying the session token as a query parameter.
///
/// Browsers cannot set custom headers on the WebSocket handshake, so the token
/// travels as `access_token`. The Worker accepts it as an alternative to the
/// Authorization header. Over `wss` the query string is encrypted in transit.
Uri cloudRealtimeUri(String token) {
  final uri = cloudWebSocketUri('/api/v1/realtime');
  return uri.replace(
    queryParameters: {...uri.queryParameters, 'access_token': token},
  );
}

Future<void> verifyCloudApiIsRunning() async {
  final response = await http
      .get(cloudApiUri('/health'), headers: {'Accept': 'application/json'})
      .timeout(const Duration(seconds: 8));
  if (response.statusCode != 200) {
    throw HttpException('HTTP_${response.statusCode}');
  }
  final decoded = jsonDecode(response.body);
  if (decoded is! Map<String, dynamic> || decoded['status'] != 'ok') {
    throw const FormatException('INVALID_HEALTH_RESPONSE');
  }
}

/// Minimal replacement for `dart:io`'s HttpException so error messages stay the
/// same without pulling in `dart:io` (unavailable on web).
class HttpException implements Exception {
  const HttpException(this.message);
  final String message;

  @override
  String toString() => 'HttpException: $message';
}

String newIdempotencyKey() {
  final source = math.Random.secure();
  final random = List<int>.generate(18, (_) => source.nextInt(256));
  final key = 'flutter-${base64Url.encode(random).replaceAll('=', '')}';
  _localMutationIds.add(key);
  if (_localMutationIds.length > 256) {
    _localMutationIds.remove(_localMutationIds.first);
  }
  return key;
}

final _localMutationIds = <String>{};

bool isLocalMutation(String? mutationId) =>
    mutationId != null && _localMutationIds.contains(mutationId);
