import 'dart:async';

import 'package:flutter/material.dart';

import '../../models/client_room.dart';
import '../../models/client_user.dart';
import '../../services/client_realtime.dart';
import '../../services/client_rooms_api.dart';
import 'live_room_page.dart';

typedef LoadAssignedRooms =
    Future<List<ClientRoomSummary>> Function(ClientSession session);
typedef LiveRoomBuilder =
    Widget Function(
      BuildContext context,
      ClientRoomSummary room,
      ClientRealtimeService realtime,
    );

class RoomsPage extends StatefulWidget {
  const RoomsPage({
    super.key,
    required this.session,
    required this.spanish,
    this.loadRooms = getAssignedRooms,
    this.realtime,
    this.liveRoomBuilder,
    this.onLogout,
  });

  final ClientSession session;
  final bool spanish;
  final LoadAssignedRooms loadRooms;
  final ClientRealtimeService? realtime;
  final LiveRoomBuilder? liveRoomBuilder;
  final Future<void> Function()? onLogout;

  @override
  State<RoomsPage> createState() => _RoomsPageState();
}

class _RoomsPageState extends State<RoomsPage> {
  late final ClientRealtimeService _realtime =
      widget.realtime ?? ClientRealtimeService(widget.session);
  late final bool _ownsRealtime = widget.realtime == null;
  StreamSubscription<ClientRealtimeEvent>? _events;
  StreamSubscription<ClientRealtimeConnectionState>? _connectionEvents;
  OverlayEntry? _connectionBlocker;
  List<ClientRoomSummary> _rooms = const [];
  bool _loading = true;
  bool _loggingOut = false;
  String? _error;

  Future<void> _logout() async {
    if (_loggingOut || widget.onLogout == null) return;
    setState(() => _loggingOut = true);
    try {
      await widget.onLogout!();
    } finally {
      if (mounted) setState(() => _loggingOut = false);
    }
  }

  @override
  void initState() {
    super.initState();
    _events = _realtime.events.listen((event) {
      if (event.type == 'room-assignments-changed') _load();
    });
    if (_ownsRealtime) {
      _connectionEvents = _realtime.connectionStates.listen(
        _handleConnectionState,
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !_realtime.isConnected) _showConnectionBlocker();
      });
      _realtime.start();
    }
    _load();
  }

  void _handleConnectionState(ClientRealtimeConnectionState state) {
    if (!mounted) return;
    if (state == ClientRealtimeConnectionState.connected) {
      _hideConnectionBlocker();
      if (_error != null && !_loading) _load();
    } else if (state != ClientRealtimeConnectionState.closed) {
      _showConnectionBlocker();
    }
  }

  void _showConnectionBlocker() {
    if (!mounted || _connectionBlocker != null) return;
    final overlay = Navigator.of(context).overlay;
    if (overlay == null) return;
    _connectionBlocker = OverlayEntry(
      builder: (_) => _ConnectionBlocker(spanish: widget.spanish),
    );
    overlay.insert(_connectionBlocker!);
  }

  void _hideConnectionBlocker() {
    _connectionBlocker?.remove();
    _connectionBlocker = null;
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final rooms = await widget.loadRooms(widget.session);
      if (mounted) {
        setState(() {
          _rooms = rooms;
          _loading = false;
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = widget.spanish
              ? 'No se pudieron cargar tus salones.'
              : 'Your rooms could not be loaded.';
        });
      }
    }
  }

  @override
  void dispose() {
    _events?.cancel();
    _connectionEvents?.cancel();
    _hideConnectionBlocker();
    if (_ownsRealtime) _realtime.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      title: Row(
        children: [
          const CircleAvatar(
            radius: 17,
            backgroundColor: Color(0xFFE8EDF2),
            child: Icon(
              Icons.person_outline,
              size: 19,
              color: Color(0xFF71859B),
            ),
          ),
          const SizedBox(width: 10),
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.session.user.fullName,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Text(
                  widget.session.user.role,
                  style: const TextStyle(
                    fontSize: 11,
                    color: Color(0xFF7A7D82),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      actions: [
        IconButton(
          key: const ValueKey('refresh-assigned-rooms'),
          onPressed: _loading ? null : _load,
          tooltip: widget.spanish ? 'Actualizar' : 'Refresh',
          icon: const Icon(Icons.refresh_rounded),
        ),
        if (widget.onLogout != null)
          IconButton(
            key: const ValueKey('client-logout'),
            onPressed: _loggingOut ? null : _logout,
            tooltip: widget.spanish ? 'Cerrar sesión' : 'Sign out',
            icon: _loggingOut
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.logout_rounded),
          ),
        const SizedBox(width: 8),
      ],
    ),
    body: SafeArea(
      child: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _ErrorState(
              message: _error!,
              onRetry: _load,
              spanish: widget.spanish,
            )
          : _rooms.isEmpty
          ? _EmptyState(spanish: widget.spanish)
          : LayoutBuilder(
              builder: (context, constraints) {
                final columns = constraints.maxWidth >= 1000
                    ? 3
                    : constraints.maxWidth >= 620
                    ? 2
                    : 1;
                return GridView.builder(
                  padding: const EdgeInsets.all(24),
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: columns,
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                    mainAxisExtent: 124,
                  ),
                  itemCount: _rooms.length,
                  itemBuilder: (context, index) => _RoomCard(
                    room: _rooms[index],
                    spanish: widget.spanish,
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (context) =>
                            widget.liveRoomBuilder?.call(
                              context,
                              _rooms[index],
                              _realtime,
                            ) ??
                            LiveRoomPage(
                              session: widget.session,
                              room: _rooms[index],
                              spanish: widget.spanish,
                              realtime: _realtime,
                            ),
                      ),
                    ),
                  ),
                );
              },
            ),
    ),
  );
}

class _ConnectionBlocker extends StatelessWidget {
  const _ConnectionBlocker({required this.spanish});

  final bool spanish;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xEFFFFFFF),
    child: PopScope(
      canPop: false,
      child: AbsorbPointer(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 18),
              Text(
                spanish ? 'Reconectando con el servidor…' : 'Reconnecting…',
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF4D5966),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _RoomCard extends StatelessWidget {
  const _RoomCard({
    required this.room,
    required this.spanish,
    required this.onTap,
  });

  final ClientRoomSummary room;
  final bool spanish;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    elevation: 1,
    shadowColor: const Color(0x18000000),
    borderRadius: BorderRadius.circular(16),
    child: InkWell(
      key: ValueKey('assigned-room-${room.id}'),
      borderRadius: BorderRadius.circular(16),
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        child: Row(
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: const Color(0xFFE9EFF5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.meeting_room_outlined,
                color: Color(0xFF71859B),
                size: 23,
              ),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    room.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    spanish
                        ? '${room.tableCount} mesas'
                        : '${room.tableCount} tables',
                    style: const TextStyle(
                      color: Color(0xFF7A7D82),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right_rounded, color: Color(0xFF95A0AA)),
          ],
        ),
      ),
    ),
  );
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.spanish});
  final bool spanish;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            Icons.domain_disabled_outlined,
            size: 58,
            color: Color(0xFF9AA8B7),
          ),
          const SizedBox(height: 16),
          Text(
            spanish ? 'No tienes salones asignados' : 'No rooms assigned',
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 7),
          Text(
            spanish
                ? 'Un administrador debe asignarte al menos un salón.'
                : 'An administrator must assign at least one room to you.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: Color(0xFF7A7D82)),
          ),
        ],
      ),
    ),
  );
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({
    required this.message,
    required this.onRetry,
    required this.spanish,
  });
  final String message;
  final VoidCallback onRetry;
  final bool spanish;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(message, style: const TextStyle(color: Color(0xFFC94E4E))),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: Text(spanish ? 'Reintentar' : 'Retry'),
        ),
      ],
    ),
  );
}
