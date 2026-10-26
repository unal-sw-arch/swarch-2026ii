import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'cloud_api.dart';

class AdminRealtimeMessage {
  const AdminRealtimeMessage({
    required this.type,
    this.hallId,
    this.resource,
    this.version,
    this.mutationId,
    this.payload = const {},
    this.staleResources = const {},
  });

  final String type;
  final int? hallId;
  final String? resource;
  final int? version;
  final String? mutationId;
  final Map<String, dynamic> payload;
  final Set<String> staleResources;

  bool get changesCatalog =>
      type.startsWith('catalog.') ||
      type == 'hall.catalog.changed' ||
      (type == 'realtime.resync-required' &&
          staleResources.contains('catalog'));

  bool get changesActivity =>
      type == 'activity.created' ||
      (type == 'realtime.resync-required' &&
          staleResources.contains('activity'));

  bool get changesDashboard =>
      type == 'dashboard.changed' ||
      (type == 'realtime.resync-required' &&
          staleResources.contains('dashboard'));
}

class AdminRealtimeService {
  AdminRealtimeService(this.token);

  final String token;
  final _messages = StreamController<AdminRealtimeMessage>.broadcast();
  final _versions = <String, int>{};
  WebSocket? _socket;
  Timer? _retry;
  bool _closed = false;
  bool _connecting = false;
  int _attempt = 0;

  Stream<AdminRealtimeMessage> get messages => _messages.stream;

  Future<void> start() async {
    _closed = false;
    await _connect();
  }

  Future<void> _connect() async {
    if (_closed || _connecting || _socket != null || token.isEmpty) return;
    _connecting = true;
    try {
      final base = getCloudApiBaseUri();
      final endpoint = base
          .resolve('/api/v1/realtime')
          .replace(scheme: base.scheme == 'https' ? 'wss' : 'ws');
      final socket = await WebSocket.connect(
        endpoint.toString(),
        headers: {HttpHeaders.authorizationHeader: 'Bearer $token'},
      ).timeout(const Duration(seconds: 10));
      if (_closed) return socket.close();
      _socket = socket;
      _attempt = 0;
      socket.listen(
        _receive,
        onDone: () => _disconnected(socket),
        onError: (_) => _disconnected(socket),
        cancelOnError: true,
      );
      socket.add(jsonEncode({'type': 'subscribe-admin'}));
    } on Object {
      _scheduleRetry();
    } finally {
      _connecting = false;
    }
  }

  void _receive(dynamic raw) {
    if (raw is! String) return;
    try {
      final value = jsonDecode(raw);
      if (value is! Map<String, dynamic> || value['type'] is! String) return;
      final type = value['type'] as String;
      if (type == 'subscribed-admin' && value['versions'] is Map) {
        final announced = value['versions'] as Map;
        final staleResources = <String>{};
        for (final entry in announced.entries) {
          final current = _asInt(entry.value) ?? 0;
          final previous = _versions['${entry.key}'];
          _versions['${entry.key}'] = current;
          if (previous != null && previous != current) {
            staleResources.add('${entry.key}');
          }
        }
        if (staleResources.isNotEmpty) {
          _emit(
            AdminRealtimeMessage(
              type: 'realtime.resync-required',
              staleResources: staleResources,
            ),
          );
        }
        return;
      }
      final resource = value['resource'] as String?;
      final version = _asInt(value['version']);
      if (resource != null && version != null) {
        final previous = _versions[resource];
        if (previous != null && version <= previous) return;
        _versions[resource] = version;
      }
      _emit(
        AdminRealtimeMessage(
          type: type,
          hallId: _asInt(value['hallId']),
          resource: resource,
          version: version,
          mutationId: value['mutationId'] as String?,
          payload: value['payload'] is Map
              ? Map<String, dynamic>.from(value['payload'] as Map)
              : const {},
        ),
      );
    } on FormatException {
      // Ignore messages from another protocol version.
    }
  }

  void _emit(AdminRealtimeMessage value) {
    if (!_messages.isClosed) _messages.add(value);
  }

  void _disconnected(WebSocket socket) {
    if (!identical(socket, _socket)) return;
    _socket = null;
    _scheduleRetry();
  }

  void _scheduleRetry() {
    if (_closed || _retry?.isActive == true) return;
    final seconds = math.min(30, 1 << math.min(_attempt++, 5));
    _retry = Timer(Duration(seconds: seconds), _connect);
  }

  Future<void> dispose() async {
    _closed = true;
    _retry?.cancel();
    final socket = _socket;
    _socket = null;
    if (socket != null) await socket.close(1000, 'Admin client closed');
    await _messages.close();
  }
}

int? _asInt(Object? value) => value is int
    ? value
    : value is num
    ? value.toInt()
    : int.tryParse('$value');
