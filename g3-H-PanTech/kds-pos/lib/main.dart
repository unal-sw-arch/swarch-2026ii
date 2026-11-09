import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'api/gateway_client.dart';
import 'app.dart';
import 'auth/session_controller.dart';
import 'auth/session_store.dart';
import 'config.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final AppConfig config;
  try {
    config = await AppConfig.load();
  } on FormatException catch (e) {
    runApp(MaterialApp(home: Scaffold(body: Center(child: Text('Configuración inválida: ${e.message}')))));
    return;
  }

  final client = GatewayClient(baseUrl: config.gatewayUrl, httpClient: http.Client());
  final session = SessionController(client: client, store: SecureSessionStore());
  runApp(KdsApp(sessionController: session, client: client, pollInterval: config.pollInterval));
}
