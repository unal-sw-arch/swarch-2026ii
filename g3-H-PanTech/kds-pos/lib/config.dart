import 'dart:convert';
import 'dart:io' show File, Platform;

import 'package:flutter/foundation.dart' show kIsWeb;

class AppConfig {
  const AppConfig({required this.gatewayUrl, required this.pollInterval});

  static const defaultGatewayUrl = 'http://localhost:8080';
  static const _defineUrl = String.fromEnvironment('GATEWAY_URL');
  static const _definePollSeconds = int.fromEnvironment('POLL_SECONDS');
  static const fileName = 'kds_config.json';

  final String gatewayUrl;
  final Duration pollInterval;

  // Orden: --dart-define, kds_config.json y valor de desarrollo.
  factory AppConfig.resolve({
    String? define,
    int? definePollSeconds,
    Map<String, dynamic>? file,
  }) {
    final raw = _firstNonEmpty([define, file?['gateway_url'] as String?]) ?? defaultGatewayUrl;
    final uri = Uri.tryParse(raw.trim());
    if (uri == null || !(uri.scheme == 'http' || uri.scheme == 'https') || uri.host.isEmpty) {
      throw FormatException('GATEWAY_URL no es una URL http(s) válida: "$raw"');
    }

    final seconds = (definePollSeconds != null && definePollSeconds > 0)
        ? definePollSeconds
        : (file?['poll_seconds'] as num?)?.toInt() ?? 4;
    return AppConfig(
      gatewayUrl: raw.trim().replaceAll(RegExp(r'/+$'), ''),
      pollInterval: Duration(seconds: seconds.clamp(2, 30)),
    );
  }

  static Future<AppConfig> load() async {
    Map<String, dynamic>? file;
    if (!kIsWeb) {
      try {
        final dir = File(Platform.resolvedExecutable).parent.path;
        final f = File('$dir${Platform.pathSeparator}$fileName');
        if (await f.exists()) {
          file = jsonDecode(await f.readAsString()) as Map<String, dynamic>;
        }
      } on Exception {
        file = null;
      }
    }
    return AppConfig.resolve(
      define: _defineUrl,
      definePollSeconds: _definePollSeconds,
      file: file,
    );
  }

  static String? _firstNonEmpty(List<String?> values) {
    for (final v in values) {
      if (v != null && v.trim().isNotEmpty) return v;
    }
    return null;
  }
}
