import 'dart:convert';

import 'package:http/http.dart' as http;

const _configuredOrigin = String.fromEnvironment(
  'RESTAURANT_API_URL',
  defaultValue: 'https://restauranteapp-cloud.daaguilarc.workers.dev',
);

class CloudApiUnavailableException implements Exception {
  const CloudApiUnavailableException();

  @override
  String toString() => 'No se encontró el servicio Restaurante en la nube.';
}

Uri getCloudApiBaseUri() {
  final value = Uri.parse(_configuredOrigin.trim());
  if (!value.hasScheme || value.host.isEmpty) {
    throw const CloudApiUnavailableException();
  }
  return value.replace(path: '/', query: null, fragment: null);
}

Future<void> verifyCloudApiIsRunning() async {
  try {
    final response = await http
        .get(getCloudApiBaseUri().resolve('/health'))
        .timeout(const Duration(seconds: 8));
    final decoded = jsonDecode(response.body);
    if (response.statusCode == 200 &&
        decoded is Map<String, dynamic> &&
        decoded['status'] == 'ok') {
      return;
    }
  } on Object {
    // All transport/configuration failures use the existing unavailable view.
  }
  throw const CloudApiUnavailableException();
}
