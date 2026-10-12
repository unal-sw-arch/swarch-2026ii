import 'dart:async';
import 'dart:math' as math;
import 'dart:io';

import 'package:flutter/gestures.dart' show kPrimaryMouseButton;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:restaurante_operational/models/client_room.dart';
import 'package:restaurante_operational/models/client_user.dart';
import 'package:restaurante_operational/services/client_realtime.dart';
import 'package:restaurante_operational/features/rooms/live_room_page.dart';

import '../../../services/admin_api.dart';
import 'room_layout_controller.dart';
import 'room_layout_models.dart';

part 'parts/page_chrome.dart';
part 'parts/layout_canvas.dart';
part 'parts/pointer_gestures.dart';
part 'parts/editor_tools.dart';
part 'parts/editor_widgets.dart';

typedef LoadRoomLayout =
    Future<RoomLayoutModel> Function({
      required String token,
      required int roomId,
    });
typedef SaveRoomLayout =
    Future<RoomLayoutModel> Function({
      required String token,
      required int roomId,
      required RoomLayoutModel layout,
    });

enum HallViewMode { live, edit }

class HallLayoutPage extends StatefulWidget {
  const HallLayoutPage({
    super.key,
    required this.spanish,
    required this.token,
    this.roomId = 1,
    this.isAdmin = true,
    this.loadLayout = getRoomLayout,
    this.saveLayout = saveRoomLayout,
    this.onBack,
  });

  final bool spanish;
  final String token;
  final int roomId;
  final bool isAdmin;
  final LoadRoomLayout loadLayout;
  final SaveRoomLayout saveLayout;
  final VoidCallback? onBack;

  @override
  State<HallLayoutPage> createState() => _HallLayoutPageState();
}

class _HallLayoutPageState extends State<HallLayoutPage> {
  static const _canvasOrigin = Offset(12000, 12000);
  static const _canvasExtent = 30000.0;
  final _layout = RoomLayoutController();
  final _camera = TransformationController();
  final _layoutSpaceKey = GlobalKey();
  final _viewportKey = GlobalKey();
  HallViewMode _mode = HallViewMode.edit;
  bool _loading = true;
  bool _saving = false;
  String? _error;
  double _rotation = 0;
  Offset _dragDelta = Offset.zero;
  Offset _resizeDelta = Offset.zero;
  Offset _lastGlobalPosition = Offset.zero;
  int? _activeDesktopPointer;
  int? _activeDesktopTable;
  bool _desktopTableMoved = false;
  _CopiedLayoutObject? _copiedObject;
  Offset? _lastPointerPosition;
  ClientRealtimeService? _liveRealtime;

  bool get _es => widget.spanish;
  bool get _editing => widget.isAdmin && _mode == HallViewMode.edit;
  bool get _editingTables =>
      _editing && _layout.interaction != LayoutInteraction.editingWalls;
  bool get _desktop =>
      Platform.isLinux || Platform.isMacOS || Platform.isWindows;

  @override
  void initState() {
    super.initState();
    if (!widget.isAdmin) _mode = HallViewMode.live;
    if (widget.loadLayout == getRoomLayout && widget.token.isNotEmpty) {
      _liveRealtime = ClientRealtimeService(_liveSession());
      unawaited(_liveRealtime!.start());
    }
    _load();
  }

  @override
  void didUpdateWidget(covariant HallLayoutPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.isAdmin && _mode != HallViewMode.live) {
      _mode = HallViewMode.live;
    }
    if (oldWidget.roomId != widget.roomId ||
        oldWidget.token != widget.token ||
        oldWidget.loadLayout != widget.loadLayout) {
      _load();
    }
  }

  @override
  void dispose() {
    unawaited(_liveRealtime?.dispose());
    _camera.dispose();
    _layout.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    var loadedSuccessfully = false;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final loaded = await widget.loadLayout(
        token: widget.token,
        roomId: widget.roomId,
      );
      _layout.load(loaded);
      loadedSuccessfully = true;
    } on Object {
      _error = _es
          ? 'No se pudo cargar el salón.'
          : 'The room could not be loaded.';
    } finally {
      if (mounted) {
        setState(() => _loading = false);
        if (loadedSuccessfully) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) _focusLayout();
          });
        }
      }
    }
  }

  Future<void> _save() async {
    if (_saving || !_layout.dirty) return;
    setState(() => _saving = true);
    try {
      final saved = await widget.saveLayout(
        token: widget.token,
        roomId: widget.roomId,
        layout: _layout.snapshot(),
      );
      _layout.acceptSaved(saved);
      if (mounted) {
        _message(_es ? 'Cambios guardados.' : 'Changes saved.');
      }
    } on Object {
      if (mounted) {
        _message(
          _es
              ? 'No se pudo guardar el layout.'
              : 'The layout could not be saved.',
          error: true,
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  void _message(String message, {bool error = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: error
            ? const Color(0xFFB64A4A)
            : const Color(0xFF52677D),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_error!),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _load,
              icon: const Icon(Icons.refresh),
              label: Text(_es ? 'Reintentar' : 'Retry'),
            ),
          ],
        ),
      );
    }
    if (_mode == HallViewMode.live && _liveRealtime != null) {
      final current = _layout.snapshot();
      return LiveRoomPage(
        key: ValueKey('admin-live-room-${widget.roomId}'),
        session: _liveSession(),
        room: ClientRoomSummary(
          id: widget.roomId,
          name: current.roomName,
          tableCount: current.tables.length,
        ),
        spanish: _es,
        realtime: _liveRealtime,
        onBack: widget.onBack,
        onEditLayout: widget.isAdmin
            ? () {
                setState(() => _mode = HallViewMode.edit);
                unawaited(_load());
              }
            : null,
      );
    }
    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.keyC, control: true):
            _copySelection,
        const SingleActivator(LogicalKeyboardKey.keyV, control: true): () =>
            _pasteCopied(_lastPointerPosition ?? _visibleLayoutCenter()),
      },
      child: Focus(
        autofocus: true,
        child: AnimatedBuilder(
          animation: _layout,
          builder: (context, _) => ColoredBox(
            color: const Color(0xFFFAF9F6),
            child: Column(
              children: [
                _header(),
                const Divider(height: 1, color: Color(0xFFE7E5E1)),
                Expanded(
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final compact = constraints.maxWidth < 720;
                      return Stack(
                        children: [
                          Positioned.fill(child: _canvas()),
                          if (_editing)
                            Positioned(
                              right: compact ? 14 : 22,
                              bottom: compact ? 76 : null,
                              top: compact ? null : 24,
                              child: _editTools(compact),
                            ),
                          Positioned(
                            bottom: 14,
                            left: 0,
                            right: 0,
                            child: Center(child: _cameraTools()),
                          ),
                          if (_mode == HallViewMode.live)
                            Positioned(
                              left: 18,
                              bottom: 18,
                              child: _LiveBadge(spanish: _es),
                            ),
                        ],
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
