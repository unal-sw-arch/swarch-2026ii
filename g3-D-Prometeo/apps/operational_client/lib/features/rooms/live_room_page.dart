import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../models/client_room.dart';
import '../../models/client_user.dart';
import '../../models/client_order.dart';
import '../../services/client_realtime.dart';
import '../../services/client_rooms_api.dart';
import '../../utils/money.dart';
import 'bill_order_dialog.dart';
import 'delivery_order_dialog.dart';
import 'daily_orders_dialog.dart';
import 'live_room_controller.dart';
import 'order_editor_dialog.dart';
import 'split_bill_page.dart';

part 'live_room/room_actions.dart';
part 'live_room/layout_canvas.dart';
part 'live_room/order_flows.dart';
part 'live_room/order_editor_loader.dart';
part 'live_room/centered_notice.dart';

enum _LiveToolMode { edit, bill, select }

typedef LoadLiveLayout =
    Future<LiveRoomLayout> Function({
      required ClientSession session,
      required int roomId,
    });
typedef SaveLiveLayout =
    Future<LiveRoomLayout> Function({
      required ClientSession session,
      required int roomId,
      required LiveRoomLayout layout,
    });
typedef LoadRoomOrders =
    Future<List<ClientOrder>> Function({
      required ClientSession session,
      required int roomId,
    });
typedef LoadTodayRoomOrders =
    Future<List<ClientOrder>> Function({
      required ClientSession session,
      required int roomId,
      DateTime? from,
      DateTime? to,
      ClientPaymentMethod? paymentMethod,
    });
typedef LoadRoomMenus =
    Future<List<ClientRoomMenu>> Function({
      required ClientSession session,
      required int roomId,
    });
typedef WriteTableOrder =
    Future<void> Function({
      required ClientSession session,
      required int roomId,
      required int tableId,
      int? orderId,
      required String description,
      required List<OrderItemWrite> items,
    });
typedef WriteExternalOrder =
    Future<void> Function({
      required ClientSession session,
      required int roomId,
      required String externalName,
      required String description,
      required List<OrderItemWrite> items,
    });
typedef SetOrderEating =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
    });
typedef TransferTableOrder =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
      required int tableId,
    });
typedef DeliverOrderItem =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
      required int itemId,
      required int unitIndex,
    });
typedef UndoDeliveredOrderItem = DeliverOrderItem;
typedef DeliverAllOrderItems =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
    });
typedef SetOrderClosed =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
      required ClientPaymentMethod paymentMethod,
    });
typedef SaveOrderSplit =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
      required List<SplitAccountWrite> accounts,
    });
typedef PayOrderSplitAccount =
    Future<ClientOrder> Function({
      required ClientSession session,
      required int roomId,
      required int orderId,
      required int accountId,
      required ClientPaymentMethod paymentMethod,
      required int tipPercent,
      required int customTip,
    });

class LiveRoomPage extends StatefulWidget {
  const LiveRoomPage({
    super.key,
    required this.session,
    required this.room,
    required this.spanish,
    this.realtime,
    this.loadLayout = getLiveRoomLayout,
    this.saveLayout = saveLiveRoomLayout,
    this.loadOrders = getRoomOrders,
    this.loadTodayOrders = getTodayRoomOrders,
    this.loadMenus = getRoomMenus,
    this.writeOrder = saveTableOrder,
    this.writeExternalOrder = saveExternalOrder,
    this.setEating = markOrderEating,
    this.transferOrder = transferTableOrder,
    this.deliverItem = deliverOrderItem,
    this.deliverAllItems = deliverAllOrderItems,
    this.undoDeliveredItem = undoDeliveredOrderItem,
    this.setClosed = markOrderClosed,
    this.saveSplit = saveOrderSplit,
    this.paySplit = payOrderSplitAccount,
    this.onBack,
    this.onEditLayout,
  });

  final ClientSession session;
  final ClientRoomSummary room;
  final bool spanish;
  final ClientRealtimeService? realtime;
  final LoadLiveLayout loadLayout;
  final SaveLiveLayout saveLayout;
  final LoadRoomOrders loadOrders;
  final LoadTodayRoomOrders loadTodayOrders;
  final LoadRoomMenus loadMenus;
  final WriteTableOrder writeOrder;
  final WriteExternalOrder writeExternalOrder;
  final SetOrderEating setEating;
  final TransferTableOrder transferOrder;
  final DeliverOrderItem deliverItem;
  final DeliverAllOrderItems deliverAllItems;
  final UndoDeliveredOrderItem undoDeliveredItem;
  final SetOrderClosed setClosed;
  final SaveOrderSplit saveSplit;
  final PayOrderSplitAccount paySplit;
  final VoidCallback? onBack;
  final VoidCallback? onEditLayout;

  @override
  State<LiveRoomPage> createState() => _LiveRoomPageState();
}

class _CreateExternalOrderIntent {
  const _CreateExternalOrderIntent();
}

class _EditExternalOrderIntent {
  const _EditExternalOrderIntent(this.order);
  final ClientOrder order;
}

class _TransferTarget {
  const _TransferTarget({required this.tableId, required this.label});

  final int tableId;
  final String label;
}

class _LiveRoomPageState extends State<LiveRoomPage> {
  static const _canvasOrigin = Offset(12000, 12000);
  static const _canvasExtent = 30000.0;
  final _layout = LiveRoomController();
  final _camera = TransformationController();
  final _viewportKey = GlobalKey();
  StreamSubscription<ClientRealtimeEvent>? _events;
  bool _loading = true;
  bool _saving = false;
  bool _reloadPending = false;
  bool _openingTableView = false;
  bool _openingOrderEditor = false;
  bool _menusStale = false;
  int _menusGeneration = 0;
  Timer? _menuRefreshTimer;
  String? _error;
  int? _activePointer;
  int? _activeTable;
  bool _pointerMoved = false;
  Offset _dragDelta = Offset.zero;
  Offset _lastGlobalPosition = Offset.zero;
  double _rotation = 0;
  List<ClientOrder> _orders = [];
  List<ClientRoomMenu>? _menusCache;
  Future<List<ClientRoomMenu>>? _menusRequest;
  _LiveToolMode _mode = _LiveToolMode.select;

  void _showCenteredNotice(String message, {bool warning = false}) {
    if (!mounted) return;
    unawaited(
      showGeneralDialog<void>(
        context: context,
        barrierDismissible: true,
        barrierLabel: message,
        barrierColor: Colors.transparent,
        transitionDuration: const Duration(milliseconds: 180),
        transitionBuilder: (_, animation, _, child) => FadeTransition(
          opacity: CurvedAnimation(
            parent: animation,
            curve: Curves.easeOutCubic,
            reverseCurve: Curves.easeInCubic,
          ),
          child: child,
        ),
        pageBuilder: (_, _, _) =>
            _CenteredNotice(message: message, warning: warning),
      ),
    );
  }

  bool get _desktop =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.linux ||
          defaultTargetPlatform == TargetPlatform.macOS ||
          defaultTargetPlatform == TargetPlatform.windows);
  bool get _canInteract => !_loading && !_saving;

  @override
  void initState() {
    super.initState();
    widget.realtime?.subscribe(widget.room.id);
    _events = widget.realtime?.events.listen(_handleRealtimeEvent);
    _load();
    unawaited(_warmMenus());
  }

  void _handleRealtimeEvent(ClientRealtimeEvent event) {
    if (event.roomId != null && event.roomId != widget.room.id) return;
    if (event.resource == 'catalog' ||
        event.type == 'hall.catalog.changed' ||
        event.type.startsWith('catalog.')) {
      _invalidateMenus();
      return;
    }
    if (event.requiresResync) {
      if (event.staleResources.contains('catalog')) _invalidateMenus();
      _reload(realtime: true);
    } else if (event.type == 'hall.orders.changed') {
      final operation = event.payload['operation'];
      if (operation == 'delete') {
        final id = event.payload['orderId'];
        if (id is int) {
          setState(() => _orders.removeWhere((order) => order.id == id));
          _syncTableStatuses();
        }
        return;
      }
      final rawOrder = event.payload['order'];
      if (operation == 'upsert' && rawOrder is Map) {
        try {
          final order = ClientOrder.fromJson(
            Map<String, dynamic>.from(rawOrder),
          );
          setState(() {
            final index = _orders.indexWhere(
              (current) => current.id == order.id,
            );
            if (order.status == 'closed') {
              if (index >= 0) _orders.removeAt(index);
            } else if (index >= 0) {
              _orders[index] = order;
            } else {
              _orders.add(order);
            }
          });
          _syncTableStatuses();
          return;
        } on Object {
          // A malformed or newer patch falls back to the canonical GET below.
        }
      }
      _reload(realtime: true);
    } else if (event.type == 'hall.layout.changed') {
      final rawLayout = event.payload['layout'];
      if (rawLayout is Map &&
          !_layout.dirty &&
          _layout.interaction == LiveLayoutInteraction.normal) {
        try {
          _layout.replaceFromRealtime(
            LiveRoomLayout.fromJson(Map<String, dynamic>.from(rawLayout)),
          );
          _syncTableStatuses();
          return;
        } on Object {
          // Fall through to a version repair GET.
        }
      }
      if (_saving ||
          _layout.dirty ||
          _layout.interaction != LiveLayoutInteraction.normal) {
        _reloadPending = true;
      } else {
        _reload(realtime: true);
      }
    } else if (event.type == 'hall.assignments.changed') {
      _reload(realtime: true, closeWhenUnauthorized: true);
    }
  }

  Future<List<ClientRoomMenu>> _roomMenus({bool force = false}) async {
    final cached = _menusCache;
    if (!force && cached != null) {
      if (_menusStale && _menuRefreshTimer == null && _menusRequest == null) {
        _scheduleMenuRefresh();
      }
      return cached;
    }
    final pending = _menusRequest;
    if (pending != null) return pending;

    final generation = _menusGeneration;
    final request = widget.loadMenus(
      session: widget.session,
      roomId: widget.room.id,
    );
    _menusRequest = request;
    try {
      final menus = await request;
      if (generation == _menusGeneration) {
        _menusCache = menus;
        _menusStale = false;
      }
      return menus;
    } finally {
      if (identical(_menusRequest, request)) _menusRequest = null;
      if (mounted && generation != _menusGeneration && _menusStale) {
        _scheduleMenuRefresh();
      }
    }
  }

  Future<void> _warmMenus({bool force = false}) async {
    try {
      await _roomMenus(force: force);
    } on Object {
      // The room stays usable. A tap retries the catalog request if no cached
      // snapshot exists, while a stale snapshot can still open immediately.
    }
  }

  void _invalidateMenus() {
    _menusGeneration++;
    _menusStale = true;
    _scheduleMenuRefresh();
  }

  void _scheduleMenuRefresh() {
    _menuRefreshTimer?.cancel();
    _menuRefreshTimer = Timer(const Duration(milliseconds: 200), () {
      _menuRefreshTimer = null;
      unawaited(_warmMenus(force: true));
    });
  }

  void _syncTableStatuses() {
    final statuses = <int, String>{};
    for (final order in _orders) {
      if (order.tableGroupId != null) {
        final group = _layout.groups
            .where((candidate) => candidate.id == order.tableGroupId)
            .firstOrNull;
        if (group != null) {
          for (final tableId in group.tableIds) {
            statuses[tableId] = order.status;
          }
        }
      } else if (order.tableId > 0) {
        statuses[order.tableId] = order.status;
      }
    }
    _layout.applyOrderStatuses(statuses);
  }

  Future<void> _load() async {
    try {
      final values = await Future.wait([
        widget.loadLayout(session: widget.session, roomId: widget.room.id),
        widget.loadOrders(session: widget.session, roomId: widget.room.id),
      ]);
      final layout = values[0] as LiveRoomLayout;
      if (mounted) {
        _layout.load(layout);
        _orders = values[1] as List<ClientOrder>;
        _syncTableStatuses();
        setState(() {
          _loading = false;
          _error = null;
        });
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) _focusLayout();
        });
      }
    } on Object {
      if (mounted) {
        setState(() {
          _loading = false;
          _error = widget.spanish
              ? 'No se pudo cargar el salón.'
              : 'The room could not be loaded.';
        });
      }
    }
  }

  Future<void> _reload({
    bool realtime = false,
    bool closeWhenUnauthorized = false,
  }) async {
    try {
      final values = await Future.wait([
        widget.loadLayout(session: widget.session, roomId: widget.room.id),
        widget.loadOrders(session: widget.session, roomId: widget.room.id),
      ]);
      final layout = values[0] as LiveRoomLayout;
      if (!mounted) return;
      _layout.replaceFromRealtime(layout);
      _orders = values[1] as List<ClientOrder>;
      _syncTableStatuses();
      if (!realtime) setState(() => _error = null);
    } on Object {
      if (!mounted) return;
      if (closeWhenUnauthorized && Navigator.of(context).canPop()) {
        Navigator.of(context).pop();
      } else if (!realtime) {
        setState(() {
          _error = widget.spanish
              ? 'No se pudo actualizar el salón.'
              : 'The room could not be refreshed.';
        });
      }
    }
  }

  Future<void> _reloadOrders() async {
    try {
      final orders = await widget.loadOrders(
        session: widget.session,
        roomId: widget.room.id,
      );
      if (!mounted) return;
      _orders = orders;
      _syncTableStatuses();
    } on Object {
      if (mounted) {
        _showCenteredNotice(
          widget.spanish
              ? 'No se pudieron actualizar los pedidos.'
              : 'Orders could not be refreshed.',
        );
      }
    }
  }

  Future<void> _persist() async {
    if (_saving || !_layout.dirty) return;
    setState(() => _saving = true);
    try {
      final saved = await widget.saveLayout(
        session: widget.session,
        roomId: widget.room.id,
        layout: _layout.snapshot(),
      );
      if (!mounted) return;
      _layout.acceptSaved(saved);
    } on Object {
      if (mounted) {
        _showCenteredNotice(
          widget.spanish
              ? 'No se pudo guardar el cambio.'
              : 'The change could not be saved.',
        );
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
        if (_reloadPending) {
          _reloadPending = false;
          _reload(realtime: true);
        }
      }
    }
  }

  @override
  void dispose() {
    _menuRefreshTimer?.cancel();
    widget.realtime?.unsubscribe(widget.room.id);
    _events?.cancel();
    _camera.dispose();
    _layout.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFFAF9F6),
    body: SafeArea(
      child: AnimatedBuilder(
        animation: _layout,
        builder: (context, _) => Column(
          children: [
            _header(),
            const Divider(height: 1, color: Color(0xFFE7E5E1)),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                  ? _errorView()
                  : Stack(
                      children: [
                        Positioned.fill(child: _canvas()),
                        Positioned(
                          left: 16,
                          top: 16,
                          child: Row(
                            children: [
                              _historyButton(),
                              const SizedBox(width: 9),
                              _externalOrdersButton(),
                            ],
                          ),
                        ),
                        Positioned(
                          right: 16,
                          top: 16,
                          child: _roomActiveTotal(),
                        ),
                        Positioned(
                          left: 16,
                          right: 16,
                          bottom: 18,
                          child: Center(child: _tools()),
                        ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    ),
  );
}
