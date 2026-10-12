import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

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

Future<void> verifyCloudApiIsRunning() async {
  final client = HttpClient()..connectionTimeout = const Duration(seconds: 5);
  try {
    final request = await client.getUrl(cloudApiUri('/health'));
    request.headers.set(HttpHeaders.acceptHeader, ContentType.json.mimeType);
    final response = await request.close().timeout(const Duration(seconds: 8));
    final body = await utf8.decoder.bind(response).join();
    if (response.statusCode != HttpStatus.ok) {
      throw HttpException('HTTP_${response.statusCode}');
    }
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic> || decoded['status'] != 'ok') {
      throw const FormatException('INVALID_HEALTH_RESPONSE');
    }
  } finally {
    client.close(force: true);
  }
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
