import 'package:flutter/material.dart';

import '../../../services/admin_api.dart';
import '../../../utils/money.dart';
import '../halls/room_layout_models.dart';

typedef LoadHistoryRooms = Future<List<RoomSummary>> Function(String token);
typedef LoadHistorySummary =
    Future<AdminOrderHistorySummary> Function({
      required String token,
      required int roomId,
    });
typedef DeleteHistoryOrders =
    Future<int> Function({
      required String token,
      required int roomId,
      required Set<String> statuses,
    });
typedef LoadHistoryEntries =
    Future<List<AdminOrderHistoryEntry>> Function({
      required String token,
      required int roomId,
    });
typedef DeleteHistoryOrder =
    Future<void> Function({
      required String token,
      required int roomId,
      required int orderId,
    });

class OrderHistoryPage extends StatefulWidget {
  const OrderHistoryPage({
    super.key,
    required this.spanish,
    required this.token,
    this.loadRooms = getRooms,
    this.loadSummary = getOrderHistorySummary,
    this.loadEntries = getOrderHistoryEntries,
    this.deleteOrders = deleteRoomOrders,
    this.deleteOrder = deleteRoomOrder,
  });

  final bool spanish;
  final String token;
  final LoadHistoryRooms loadRooms;
  final LoadHistorySummary loadSummary;
  final LoadHistoryEntries loadEntries;
  final DeleteHistoryOrders deleteOrders;
  final DeleteHistoryOrder deleteOrder;

  @override
  State<OrderHistoryPage> createState() => _OrderHistoryPageState();
}

class _OrderHistoryPageState extends State<OrderHistoryPage> {
  List<RoomSummary> _rooms = const [];
  RoomSummary? _room;
  AdminOrderHistorySummary? _summary;
  List<AdminOrderHistoryEntry> _entries = const [];
  final Set<String> _selected = {};
  bool _loadingRooms = true;
  bool _loadingSummary = false;
  bool _deleting = false;
  final Set<int> _deletingEntries = {};
  String? _error;

  bool get _es => widget.spanish;

  @override
  void initState() {
    super.initState();
    _loadRooms();
  }

  Future<void> _loadRooms() async {
    setState(() {
      _loadingRooms = true;
      _error = null;
    });
    try {
      final rooms = await widget.loadRooms(widget.token);
      if (!mounted) return;
      setState(() {
        _rooms = rooms;
        _room = rooms.isEmpty ? null : rooms.first;
        _loadingRooms = false;
      });
      if (_room != null) await _loadSummary();
    } on Object {
      if (!mounted) return;
      setState(() {
        _loadingRooms = false;
        _error = _es
            ? 'No se pudieron cargar los salones.'
            : 'Rooms could not be loaded.';
      });
    }
  }

  Future<void> _loadSummary() async {
    final room = _room;
    if (room == null) return;
    setState(() {
      _loadingSummary = true;
      _error = null;
    });
    try {
      final values = await Future.wait<Object>([
        widget.loadSummary(token: widget.token, roomId: room.id),
        widget.loadEntries(token: widget.token, roomId: room.id),
      ]);
      final summary = values[0] as AdminOrderHistorySummary;
      final entries = values[1] as List<AdminOrderHistoryEntry>;
      if (!mounted || _room?.id != room.id) return;
      setState(() {
        _summary = summary;
        _entries = entries;
        _selected.removeWhere((status) => summary.countFor(status) == 0);
        _loadingSummary = false;
      });
    } on Object {
      if (!mounted || _room?.id != room.id) return;
      setState(() {
        _summary = null;
        _entries = const [];
        _loadingSummary = false;
        _error = _es
            ? 'No se pudo cargar el historial del salón.'
            : 'The room history could not be loaded.';
      });
    }
  }

  Future<void> _deleteEntry(AdminOrderHistoryEntry entry) async {
    final room = _room;
    if (room == null || _deletingEntries.contains(entry.id)) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Eliminar pedido' : 'Delete order'),
        content: Text(
          _es
              ? 'Se eliminará permanentemente el pedido de ${entry.label}.'
              : 'The order for ${entry.label} will be permanently deleted.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: ValueKey('confirm-delete-order-${entry.id}'),
            style: FilledButton.styleFrom(backgroundColor: Colors.red.shade700),
            onPressed: () => Navigator.pop(context, true),
            child: Text(_es ? 'Eliminar' : 'Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _deletingEntries.add(entry.id));
    try {
      await widget.deleteOrder(
        token: widget.token,
        roomId: room.id,
        orderId: entry.id,
      );
      if (!mounted) return;
      await _loadSummary();
    } on Object {
      if (mounted) {
        setState(() {
          _error = _es
              ? 'No se pudo eliminar el pedido.'
              : 'The order could not be deleted.';
        });
      }
    } finally {
      if (mounted) setState(() => _deletingEntries.remove(entry.id));
    }
  }

  Future<void> _deleteSelected() async {
    final room = _room;
    if (room == null || _selected.isEmpty || _deleting) return;
    final count = _selected.fold<int>(
      0,
      (total, status) => total + (_summary?.countFor(status) ?? 0),
    );
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Eliminar órdenes' : 'Delete orders'),
        content: Text(
          _es
              ? 'Se eliminarán permanentemente $count órdenes de ${room.name}. Esta acción no se puede deshacer.'
              : '$count orders from ${room.name} will be permanently deleted. This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            key: const ValueKey('confirm-delete-order-history'),
            style: FilledButton.styleFrom(backgroundColor: Colors.red.shade700),
            onPressed: () => Navigator.pop(context, true),
            child: Text(_es ? 'Eliminar' : 'Delete'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _deleting = true);
    try {
      final deleted = await widget.deleteOrders(
        token: widget.token,
        roomId: room.id,
        statuses: Set.unmodifiable(_selected),
      );
      if (!mounted) return;
      _selected.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _es
                ? 'Se eliminaron $deleted órdenes.'
                : '$deleted orders were deleted.',
          ),
        ),
      );
      await _loadSummary();
    } on Object {
      if (mounted) {
        setState(() {
          _error = _es
              ? 'No se pudieron eliminar las órdenes.'
              : 'The orders could not be deleted.';
        });
      }
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loadingRooms) return const Center(child: CircularProgressIndicator());
    return SingleChildScrollView(
      padding: const EdgeInsets.all(28),
      child: Align(
        alignment: Alignment.topLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 820),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                _es ? 'Eliminar historial' : 'Delete history',
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                _es
                    ? 'Escoge un salón y los tipos de órdenes que quieres eliminar.'
                    : 'Choose a room and the order types you want to delete.',
                style: const TextStyle(color: Color(0xFF6F747A), fontSize: 15),
              ),
              const SizedBox(height: 28),
              if (_rooms.isEmpty)
                _message(
                  _es ? 'No hay salones disponibles.' : 'There are no rooms.',
                )
              else ...[
                DropdownButtonFormField<int>(
                  key: const ValueKey('history-room-selector'),
                  initialValue: _room?.id,
                  decoration: InputDecoration(
                    labelText: _es ? 'Salón' : 'Room',
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                  items: [
                    for (final room in _rooms)
                      DropdownMenuItem(value: room.id, child: Text(room.name)),
                  ],
                  onChanged: _deleting
                      ? null
                      : (id) {
                          setState(() {
                            _room = _rooms.firstWhere((room) => room.id == id);
                            _summary = null;
                            _entries = const [];
                            _selected.clear();
                          });
                          _loadSummary();
                        },
                ),
                const SizedBox(height: 22),
                if (_loadingSummary)
                  const Padding(
                    padding: EdgeInsets.all(44),
                    child: Center(child: CircularProgressIndicator()),
                  )
                else if (_summary != null) ...[
                  _statusTile(
                    status: 'waiting',
                    title: _es ? 'Pendientes' : 'Pending',
                    subtitle: _es
                        ? 'Órdenes con productos por entregar.'
                        : 'Orders with items awaiting delivery.',
                    icon: Icons.schedule_rounded,
                    color: const Color(0xFFD39B24),
                  ),
                  const SizedBox(height: 12),
                  _statusTile(
                    status: 'eating',
                    title: _es ? 'Entregadas' : 'Delivered',
                    subtitle: _es
                        ? 'Órdenes entregadas que todavía no se han facturado.'
                        : 'Delivered orders that have not been billed.',
                    icon: Icons.restaurant_rounded,
                    color: const Color(0xFF4E8B61),
                  ),
                  const SizedBox(height: 12),
                  _statusTile(
                    status: 'closed',
                    title: _es ? 'Facturadas' : 'Billed',
                    subtitle: _es
                        ? 'Ventas cerradas que alimentan las métricas.'
                        : 'Closed sales included in dashboard metrics.',
                    icon: Icons.receipt_long_rounded,
                    color: const Color(0xFF71859B),
                  ),
                  const SizedBox(height: 24),
                  Align(
                    alignment: Alignment.centerRight,
                    child: FilledButton.icon(
                      key: const ValueKey('delete-order-history'),
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.red.shade700,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 22,
                          vertical: 16,
                        ),
                      ),
                      onPressed: _selected.isEmpty || _deleting
                          ? null
                          : _deleteSelected,
                      icon: _deleting
                          ? const SizedBox.square(
                              dimension: 17,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.delete_sweep_outlined),
                      label: Text(
                        _es ? 'Eliminar selección' : 'Delete selection',
                      ),
                    ),
                  ),
                  const SizedBox(height: 30),
                  Text(
                    _es ? 'Pedidos individuales' : 'Individual orders',
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    _es
                        ? 'Se muestran los 50 pedidos más recientes. La selección de arriba también filtra esta lista.'
                        : 'The 50 most recent orders are shown. The selection above also filters this list.',
                    style: const TextStyle(color: Color(0xFF73777C)),
                  ),
                  const SizedBox(height: 14),
                  if (_visibleEntries.isEmpty)
                    _message(
                      _es
                          ? 'No hay pedidos para mostrar.'
                          : 'There are no orders to display.',
                    )
                  else
                    for (final entry in _visibleEntries) ...[
                      _entryTile(entry),
                      const SizedBox(height: 9),
                    ],
                ],
              ],
              if (_error != null) ...[
                const SizedBox(height: 18),
                _message(_error!, error: true),
              ],
            ],
          ),
        ),
      ),
    );
  }

  List<AdminOrderHistoryEntry> get _visibleEntries => _selected.isEmpty
      ? _entries
      : _entries.where((entry) => _selected.contains(entry.status)).toList();

  Widget _entryTile(AdminOrderHistoryEntry entry) {
    final deleting = _deletingEntries.contains(entry.id);
    final color = switch (entry.status) {
      'waiting' => const Color(0xFFD39B24),
      'eating' => const Color(0xFF4E8B61),
      _ => const Color(0xFF71859B),
    };
    final status = switch (entry.status) {
      'waiting' => _es ? 'Pendiente' : 'Pending',
      'eating' => _es ? 'Entregada' : 'Delivered',
      _ => _es ? 'Facturada' : 'Billed',
    };
    final local = entry.updatedAt.toLocal();
    final timestamp =
        '${local.day.toString().padLeft(2, '0')}/'
        '${local.month.toString().padLeft(2, '0')}/${local.year} · '
        '${local.hour.toString().padLeft(2, '0')}:'
        '${local.minute.toString().padLeft(2, '0')}';
    return Container(
      key: ValueKey('history-order-${entry.id}'),
      padding: const EdgeInsets.fromLTRB(16, 13, 8, 13),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(13),
        border: Border.all(color: const Color(0xFFE0E3E6)),
      ),
      child: Row(
        children: [
          Container(
            width: 9,
            height: 42,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.label,
                  style: const TextStyle(fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 3),
                Text(
                  '$status · $timestamp',
                  style: const TextStyle(
                    color: Color(0xFF73777C),
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          if (entry.totalValue > 0)
            Text(
              formatPesos(entry.totalValue),
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          IconButton(
            tooltip: _es ? 'Eliminar pedido' : 'Delete order',
            onPressed: deleting || _deleting ? null : () => _deleteEntry(entry),
            icon: deleting
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.delete_outline_rounded),
            color: Colors.red.shade700,
          ),
        ],
      ),
    );
  }

  Widget _statusTile({
    required String status,
    required String title,
    required String subtitle,
    required IconData icon,
    required Color color,
  }) {
    final count = _summary!.countFor(status);
    final enabled = count > 0 && !_deleting;
    final selected = _selected.contains(status);
    return Material(
      color: selected ? color.withValues(alpha: 0.09) : Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: selected ? color : const Color(0xFFE0E3E6),
          width: selected ? 1.5 : 1,
        ),
      ),
      child: CheckboxListTile(
        key: ValueKey('history-status-$status'),
        value: selected,
        enabled: enabled,
        activeColor: color,
        secondary: CircleAvatar(
          backgroundColor: color.withValues(alpha: 0.13),
          foregroundColor: color,
          child: Icon(icon, size: 20),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text(subtitle),
        controlAffinity: ListTileControlAffinity.trailing,
        onChanged: enabled
            ? (value) => setState(() {
                value == true
                    ? _selected.add(status)
                    : _selected.remove(status);
              })
            : null,
      ),
    );
  }

  Widget _message(String value, {bool error = false}) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: error ? const Color(0xFFFFEEEE) : const Color(0xFFF1F4F6),
      borderRadius: BorderRadius.circular(12),
    ),
    child: Text(
      value,
      style: TextStyle(
        color: error ? Colors.red.shade800 : const Color(0xFF5D6872),
      ),
    ),
  );
}
