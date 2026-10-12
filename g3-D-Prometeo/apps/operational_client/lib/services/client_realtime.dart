import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import '../models/client_user.dart';
import 'cloud_api.dart';

class ClientRealtimeEvent {
  const ClientRealtimeEvent({
    required this.type,
    this.roomId,
    this.resource,
    this.version,
    this.mutationId,
    this.payload = const {},
    this.versions = const {},
    this.staleResources = const {},
  });

  final String type;
  final int? roomId;
  final String? resource;
  final int? version;
  final String? mutationId;
  final Map<String, dynamic> payload;
  final Map<String, int> versions;

  /// Resources whose announced revision changed while the socket was away.
  final Set<String> staleResources;

  bool get requiresResync => type == 'realtime.resync-required';
}

enum ClientRealtimeConnectionState {
  disconnected,
  connecting,
  connected,
  closed,
}

class ClientRealtimeService {
  ClientRealtimeService(this.session);

  final ClientSession session;
  final _events = StreamController<ClientRealtimeEvent>.broadcast();
  final _connectionStates =
      StreamController<ClientRealtimeConnectionState>.broadcast();
  final _subscriptions = <int>{};
  final _versions = <String, int>{};
  WebSocket? _socket;
  Timer? _reconnectTimer;
  bool _closed = false;
  bool _connecting = false;
  int _attempt = 0;
  ClientRealtimeConnectionState _connectionState =
      ClientRealtimeConnectionState.disconnected;

  Stream<ClientRealtimeEvent> get events => _events.stream;
  Stream<ClientRealtimeConnectionState> get connectionStates =>
      _connectionStates.stream;
  ClientRealtimeConnectionState get connectionState => _connectionState;
  bool get isConnected =>
      _connectionState == ClientRealtimeConnectionState.connected;

  Future<void> start() async {
    _closed = false;
    await _connect();
  }

  void subscribe(int roomId) {
    _subscriptions.add(roomId);
    _send({'type': 'subscribe', 'hallId': roomId});
  }

  void unsubscribe(int roomId) {
    _subscriptions.remove(roomId);
    _send({'type': 'unsubscribe', 'hallId': roomId});
  }

  Future<void> _connect() async {
    if (_closed || _connecting || _socket != null) return;
    _connecting = true;
    _setConnectionState(ClientRealtimeConnectionState.connecting);
    try {
      final socket = await WebSocket.connect(
        cloudWebSocketUri('/api/v1/realtime').toString(),
        headers: {HttpHeaders.authorizationHeader: 'Bearer ${session.token}'},
      ).timeout(const Duration(seconds: 10));
      if (_closed) {
        await socket.close();
        return;
      }
      _socket = socket;
      socket.pingInterval = const Duration(seconds: 10);
      _attempt = 0;
      _setConnectionState(ClientRealtimeConnectionState.connected);
      socket.listen(
        _receive,
        onError: (_) => _disconnected(socket),
        onDone: () => _disconnected(socket),
        cancelOnError: true,
      );
      for (final roomId in _subscriptions) {
        _send({'type': 'subscribe', 'hallId': roomId});
      }
    } on Object {
      _setConnectionState(ClientRealtimeConnectionState.disconnected);
      _scheduleReconnect();
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
      final hallId = _int(value['hallId'] ?? value['roomId']);
      final resource = value['resource'] as String?;
      final version = _int(value['version']);
      final payload = value['payload'] is Map
          ? Map<String, dynamic>.from(value['payload'] as Map)
          : const <String, dynamic>{};
      final mutationId = value['mutationId'] as String?;
      if (isLocalMutation(mutationId)) {
        if (resource != null && version != null) _versions[resource] = version;
        return;
      }
      final announcedVersions = value['versions'] is Map
          ? (value['versions'] as Map).map(
              (key, current) => MapEntry('$key', _int(current) ?? 0),
            )
          : const <String, int>{};

      if (type == 'subscribed') {
        final staleResources = <String>{};
        for (final entry in announcedVersions.entries) {
          final known = _versions[entry.key];
          if (known != null && known != entry.value) {
            staleResources.add(entry.key);
          }
          _versions[entry.key] = entry.value;
        }
        _emit(
          ClientRealtimeEvent(
            type: staleResources.isNotEmpty ? 'realtime.resync-required' : type,
            roomId: hallId,
            versions: announcedVersions,
            staleResources: staleResources,
          ),
        );
        return;
      }

      if (resource != null && version != null) {
        final previous = _versions[resource];
        // A mutation can touch several rows and therefore advance a D1-backed
        // resource revision by more than one. WebSocket delivery is ordered;
        // missed changes are detected when the reconnect subscription reports
        // a revision different from the last one seen by this client.
        if (previous != null && version <= previous) return;
        _versions[resource] = version;
      }
      _emit(
        ClientRealtimeEvent(
          type: type,
          roomId: hallId,
          resource: resource,
          version: version,
          mutationId: mutationId,
          payload: payload,
          versions: announcedVersions,
        ),
      );
    } on FormatException {
      // Messages outside the versioned application protocol are ignored.
    }
  }

  void _emit(ClientRealtimeEvent event) {
    if (!_events.isClosed) _events.add(event);
  }

  void _send(Map<String, dynamic> message) {
    if (_socket?.readyState == WebSocket.open) {
      _socket!.add(jsonEncode(message));
    }
  }

  void _disconnected(WebSocket socket) {
    if (!identical(socket, _socket)) return;
    _socket = null;
    _setConnectionState(ClientRealtimeConnectionState.disconnected);
    _scheduleReconnect();
  }

  void _setConnectionState(ClientRealtimeConnectionState state) {
    if (_connectionState == state) return;
    _connectionState = state;
    if (!_connectionStates.isClosed) _connectionStates.add(state);
  }

  void _scheduleReconnect() {
    if (_closed || _reconnectTimer?.isActive == true) return;
    final seconds = math.min(30, 1 << math.min(_attempt++, 5));
    _reconnectTimer = Timer(Duration(seconds: seconds), _connect);
  }

  Future<void> dispose() async {
    _closed = true;
    _reconnectTimer?.cancel();
    final socket = _socket;
    _socket = null;
    if (socket != null) await socket.close(1000, 'Client closed');
    _setConnectionState(ClientRealtimeConnectionState.closed);
    await _events.close();
    await _connectionStates.close();
  }
}

int? _int(Object? value) => value is int
    ? value
    : value is num
    ? value.toInt()
    : int.tryParse('$value');
