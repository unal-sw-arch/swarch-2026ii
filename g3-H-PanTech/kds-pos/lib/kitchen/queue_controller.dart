import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/gateway_client.dart';
import '../models/queue_item.dart';

class ActionResult {
  const ActionResult.ok(this.message) : ok = true;
  const ActionResult.failed(this.message) : ok = false;

  final bool ok;
  final String message;
}

class QueueController extends ChangeNotifier {
  QueueController({required this.client, this.pollInterval = const Duration(seconds: 4)});

  final GatewayClient client;
  final Duration pollInterval;

  QueueSnapshot? snapshot;
  ApiException? error;
  DateTime? lastUpdated;
  bool loading = false;
  final Set<String> busy = {};

  Timer? _timer;
  bool _inFlight = false;
  bool _disposed = false;

  void start() {
    _timer?.cancel();
    refresh();
    _timer = Timer.periodic(pollInterval, (_) => refresh());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
  }

  // No solapa consultas.
  Future<void> refresh() async {
    if (_inFlight || _disposed) return;
    _inFlight = true;
    loading = snapshot == null;
    notifyListeners();
    try {
      snapshot = await client.fetchQueue();
      lastUpdated = DateTime.now();
      error = null;
    } on UnauthenticatedException catch (e) {
      error = e;
      stop();
    } on ApiException catch (e) {
      error = e;
    } finally {
      _inFlight = false;
      loading = false;
      if (!_disposed) notifyListeners();
    }
  }

  Future<ActionResult> changeStatus(QueueItem item, String status) => _act(item, () async {
        await client.setStatus(item.orderId, status);
        return status == OrderStatus.enPreparacion
            ? 'Pedido #${item.shortId} en preparación.'
            : 'Pedido #${item.shortId} listo para despachar.';
      });

  Future<ActionResult> deliver(QueueItem item) => _act(item, () async {
        await client.deliver(item.orderId);
        return 'Pedido #${item.shortId} entregado.';
      });

  Future<ActionResult> _act(QueueItem item, Future<String> Function() action) async {
    if (!busy.add(item.orderId)) return const ActionResult.failed('Ya se está procesando este pedido.');
    notifyListeners();
    try {
      final message = await action();
      await _refreshNow();
      return ActionResult.ok(message);
    } on ApiException catch (e) {
      // 409 y 404: la cola cambió, se actualiza.
      if (e is InvalidStateException || e is CapacityException || e is NotFoundException) {
        await _refreshNow();
      }
      return ActionResult.failed(e.message);
    } finally {
      busy.remove(item.orderId);
      if (!_disposed) notifyListeners();
    }
  }

  Future<void> _refreshNow() async {
    while (_inFlight) {
      await Future<void>.delayed(const Duration(milliseconds: 10));
    }
    await refresh();
  }

  @override
  void dispose() {
    _disposed = true;
    stop();
    super.dispose();
  }
}
