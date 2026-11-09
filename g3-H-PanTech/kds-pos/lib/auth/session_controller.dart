import 'package:flutter/foundation.dart';

import '../api/gateway_client.dart';
import 'session_store.dart';

class SessionController extends ChangeNotifier {
  SessionController({required this.client, required this.store}) {
    client.onSessionChanged = _onClientSession;
  }

  final GatewayClient client;
  final SessionStore store;

  Session? session;
  bool restoring = true;

  String? notice;

  Future<void> restore() async {
    final saved = await store.read();
    if (saved != null && saved.canAccessKds) {
      session = saved;
      client.session = saved;
    } else if (saved != null) {
      await store.clear();
    }
    restoring = false;
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final next = await client.login(email.trim(), password);
    if (!next.canAccessKds) {
      throw const ForbiddenException(
        'Esta cuenta no tiene acceso al KDS. Se requiere el rol cocinero, despachador, supervisor o admin.',
      );
    }
    await store.write(next);
    client.session = next;
    session = next;
    notice = null;
    notifyListeners();
  }

  Future<void> logout() async {
    client.session = null;
    session = null;
    notice = null;
    await store.clear();
    notifyListeners();
  }

  Future<void> _onClientSession(Session? renewed) async {
    if (renewed != null) {
      session = renewed;
      await store.write(renewed);
      return;
    }
    session = null;
    notice = 'Su sesión expiró. Inicie sesión de nuevo.';
    await store.clear();
    notifyListeners();
  }
}
