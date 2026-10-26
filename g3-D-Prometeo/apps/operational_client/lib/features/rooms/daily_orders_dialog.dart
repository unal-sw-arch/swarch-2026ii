import 'package:flutter/material.dart';

import '../../models/client_order.dart';
import '../../utils/money.dart';
import 'bill_order_dialog.dart';

typedef LoadFilteredOrders =
    Future<List<ClientOrder>> Function({
      required DateTime from,
      required DateTime to,
      ClientPaymentMethod? paymentMethod,
    });

class DailyOrdersDialog extends StatefulWidget {
  const DailyOrdersDialog({
    super.key,
    required this.spanish,
    required this.orders,
    this.loadOrders,
  });

  final bool spanish;
  final List<ClientOrder> orders;
  final LoadFilteredOrders? loadOrders;

  @override
  State<DailyOrdersDialog> createState() => _DailyOrdersDialogState();
}

class _DailyOrdersDialogState extends State<DailyOrdersDialog> {
  late List<ClientOrder> _orders;
  int _fromHour = 1;
  int _toHour = 24;
  ClientPaymentMethod? _paymentMethod;
  bool _loading = false;
  String? _error;

  bool get spanish => widget.spanish;

  @override
  void initState() {
    super.initState();
    _orders = widget.orders;
  }

  @override
  Widget build(BuildContext context) {
    final paidEntries = <_PaidHistoryEntry>[];
    for (final order in _orders) {
      if (order.splitAccounts.isNotEmpty) {
        for (final account in order.splitAccounts) {
          if (account.isPaid &&
              (_paymentMethod == null ||
                  account.paymentMethod == _paymentMethod) &&
              _withinSelectedHours(account.paidAt)) {
            paidEntries.add(_PaidHistoryEntry(order, account));
          }
        }
      } else if (order.status == 'closed' &&
          (_paymentMethod == null || order.paymentMethod == _paymentMethod)) {
        paidEntries.add(_PaidHistoryEntry(order, null));
      }
    }
    paidEntries.sort((left, right) => right.paidAt.compareTo(left.paidAt));
    final total = paidEntries.fold<int>(0, (sum, entry) => sum + entry.total);
    return Material(
      color: Colors.transparent,
      child: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760, maxHeight: 720),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: const Color(0xFFFAF9F6),
                borderRadius: BorderRadius.circular(22),
                boxShadow: const [
                  BoxShadow(color: Color(0x44000000), blurRadius: 30),
                ],
              ),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(22, 16, 12, 14),
                    child: Row(
                      children: [
                        const Icon(Icons.history_rounded),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                spanish
                                    ? 'Historial de ventas'
                                    : 'Sales history',
                                style: const TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              Text(
                                _periodLabel(),
                                style: const TextStyle(
                                  color: Color(0xFF73777C),
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 12, 18, 10),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Row(
                          children: [
                            const Icon(
                              Icons.schedule_rounded,
                              size: 19,
                              color: Color(0xFF71859B),
                            ),
                            const SizedBox(width: 8),
                            Text(
                              spanish ? 'Intervalo horario' : 'Hourly range',
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const Spacer(),
                            Text(
                              '${_hourLabel(_fromHour)} – ${_hourLabel(_toHour, end: true)}',
                              style: const TextStyle(
                                color: Color(0xFF52677D),
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ],
                        ),
                        RangeSlider(
                          key: const ValueKey('sales-history-hour-range'),
                          min: 1,
                          max: 24,
                          divisions: 23,
                          values: RangeValues(
                            _fromHour.toDouble(),
                            _toHour.toDouble(),
                          ),
                          labels: RangeLabels(
                            _hourLabel(_fromHour),
                            _hourLabel(_toHour, end: true),
                          ),
                          onChanged: _loading
                              ? null
                              : (range) {
                                  final from = range.start.round();
                                  final to = range.end.round();
                                  if (to - from < 1) return;
                                  setState(() {
                                    _fromHour = from;
                                    _toHour = to;
                                  });
                                },
                          onChangeEnd: _loading ? null : (_) => _reload(),
                        ),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            _paymentChip(null),
                            for (final method in ClientPaymentMethod.values)
                              _paymentChip(method),
                          ],
                        ),
                        if (_error != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            _error!,
                            style: const TextStyle(color: Color(0xFFB64A4A)),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: _loading
                        ? const Center(child: CircularProgressIndicator())
                        : paidEntries.isEmpty
                        ? Center(
                            child: Text(
                              spanish
                                  ? 'Aún no hay cuentas facturadas hoy.'
                                  : 'There are no billed orders today yet.',
                            ),
                          )
                        : ListView.separated(
                            padding: const EdgeInsets.all(18),
                            itemCount: paidEntries.length,
                            separatorBuilder: (_, _) =>
                                const SizedBox(height: 9),
                            itemBuilder: (context, index) {
                              final entry = paidEntries[index];
                              final order = entry.order;
                              return Card(
                                margin: EdgeInsets.zero,
                                child: ListTile(
                                  leading: const CircleAvatar(
                                    child: Icon(Icons.table_restaurant),
                                  ),
                                  title: Text(
                                    '${spanish ? 'Mesa' : 'Table'} ${order.tableLabel}',
                                  ),
                                  subtitle: Wrap(
                                    spacing: 7,
                                    runSpacing: 4,
                                    crossAxisAlignment:
                                        WrapCrossAlignment.center,
                                    children: [
                                      Text(
                                        '${entry.quantityLabel} ${spanish ? 'productos' : 'items'}',
                                      ),
                                      if (entry.account != null &&
                                          order.splitAccounts.length > 1)
                                        Text(entry.account!.name),
                                      _PaymentBadge(
                                        spanish: spanish,
                                        method: entry.method,
                                      ),
                                    ],
                                  ),
                                  trailing: Text(
                                    formatPesos(entry.total),
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w800,
                                    ),
                                  ),
                                  onTap: () => _openBill(context, entry),
                                ),
                              );
                            },
                          ),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.all(18),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          spanish ? 'Total del periodo' : 'Period total',
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        Text(
                          formatPesos(total),
                          style: const TextStyle(
                            fontSize: 25,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _paymentChip(ClientPaymentMethod? method) => ChoiceChip(
    key: ValueKey('history-payment-${method?.apiValue ?? 'all'}'),
    label: Text(
      method == null ? (spanish ? 'Todos' : 'All') : _paymentLabel(method),
    ),
    selected: _paymentMethod == method,
    onSelected: _loading
        ? null
        : (_) {
            setState(() => _paymentMethod = method);
            _reload();
          },
  );

  Future<void> _reload() async {
    final loader = widget.loadOrders;
    if (loader == null) {
      setState(() {});
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final today = DateTime.now();
      final from = DateTime(today.year, today.month, today.day, _fromHour);
      final to = _toHour == 24
          ? DateTime(today.year, today.month, today.day + 1)
          : DateTime(today.year, today.month, today.day, _toHour);
      final orders = await loader(
        from: from,
        to: to,
        paymentMethod: _paymentMethod,
      );
      if (mounted) setState(() => _orders = orders);
    } on Object {
      if (mounted) {
        setState(() {
          _error = spanish
              ? 'No se pudo actualizar el historial.'
              : 'Could not refresh sales history.';
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _periodLabel() =>
      '${spanish ? 'Hoy' : 'Today'} · ${_hourLabel(_fromHour)} – '
      '${_hourLabel(_toHour, end: true)}';

  String _hourLabel(int hour, {bool end = false}) {
    if (end && hour == 24) return '23:59';
    return '${hour.toString().padLeft(2, '0')}:00';
  }

  bool _withinSelectedHours(String? paidAt) {
    if (paidAt == null) return true;
    final date = DateTime.tryParse(paidAt)?.toLocal();
    if (date == null) return false;
    return date.hour >= _fromHour && date.hour < _toHour;
  }

  String _paymentLabel(ClientPaymentMethod method) => switch (method) {
    ClientPaymentMethod.cash => spanish ? 'Efectivo' : 'Cash',
    ClientPaymentMethod.transfer => spanish ? 'Transferencia' : 'Transfer',
    ClientPaymentMethod.card => spanish ? 'Tarjeta' : 'Card',
  };

  Future<void> _openBill(BuildContext context, _PaidHistoryEntry entry) =>
      showGeneralDialog<void>(
        context: context,
        barrierDismissible: true,
        barrierLabel: 'Order detail',
        barrierColor: Colors.black45,
        transitionDuration: const Duration(milliseconds: 180),
        transitionBuilder: (_, animation, _, child) =>
            FadeTransition(opacity: animation, child: child),
        pageBuilder: (_, _, _) => Padding(
          padding: const EdgeInsets.all(18),
          child: entry.account == null
              ? BillOrderDialog(
                  spanish: spanish,
                  tableLabel: entry.order.tableLabel,
                  order: entry.order,
                  productsById: const {},
                  readOnly: true,
                )
              : _PaidAccountDetail(spanish: spanish, entry: entry),
        ),
      );
}

class _PaidHistoryEntry {
  const _PaidHistoryEntry(this.order, this.account);
  final ClientOrder order;
  final ClientSplitAccount? account;

  int get total => account?.totalValue ?? order.total;
  ClientPaymentMethod? get method =>
      account?.paymentMethod ?? order.paymentMethod;
  String get paidAt => account?.paidAt ?? order.createdAt ?? '';
  String get quantityLabel {
    if (account == null) {
      return '${order.items.fold<int>(0, (sum, item) => sum + item.quantity)}';
    }
    final halves = account!.allocations.fold<int>(
      0,
      (sum, allocation) => sum + allocation.halfUnits,
    );
    return halves.isEven ? '${halves ~/ 2}' : '${halves / 2}';
  }
}

class _PaidAccountDetail extends StatelessWidget {
  const _PaidAccountDetail({required this.spanish, required this.entry});

  final bool spanish;
  final _PaidHistoryEntry entry;

  @override
  Widget build(BuildContext context) {
    final account = entry.account!;
    final itemsById = {for (final item in entry.order.items) item.id: item};
    return Material(
      color: Colors.transparent,
      child: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 760, maxHeight: 700),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: const Color(0xFFFAF9F6),
                borderRadius: BorderRadius.circular(22),
                boxShadow: const [
                  BoxShadow(color: Color(0x44000000), blurRadius: 30),
                ],
              ),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(22, 16, 12, 14),
                    child: Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${spanish ? 'Mesa' : 'Table'} ${entry.order.tableLabel} · ${account.name}',
                                style: const TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              Text(
                                '${spanish ? 'Pagada' : 'Paid'} · ${_methodLabel(account.paymentMethod)}',
                                style: const TextStyle(
                                  color: Color(0xFF71859B),
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                  ),
                  const Divider(height: 1),
                  Expanded(
                    child: ListView.separated(
                      padding: const EdgeInsets.all(18),
                      itemCount: account.allocations.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 9),
                      itemBuilder: (_, index) {
                        final allocation = account.allocations[index];
                        final item = itemsById[allocation.orderItemId];
                        if (item == null) return const SizedBox.shrink();
                        final halves = allocation.halfUnits;
                        final quantity = halves.isEven
                            ? '${halves ~/ 2}'
                            : '${halves / 2}';
                        return Card(
                          color: item.parentOrderItemId == null
                              ? Colors.white
                              : const Color(0xFFF2F5F8),
                          child: Padding(
                            padding: const EdgeInsets.all(14),
                            child: Row(
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        '$quantity× ${item.name}',
                                        style: const TextStyle(
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                      if (item.categoryName.isNotEmpty)
                                        Text(
                                          item.categoryName,
                                          style: const TextStyle(
                                            color: Color(0xFF71859B),
                                          ),
                                        ),
                                      if ((item.specifications ?? '')
                                          .trim()
                                          .isNotEmpty)
                                        Text(item.specifications!.trim()),
                                    ],
                                  ),
                                ),
                                Text(
                                  formatPesos(
                                    (item.unitValue * halves / 2).round(),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.all(18),
                    child: Column(
                      children: [
                        _moneyRow(
                          spanish ? 'Subtotal' : 'Subtotal',
                          account.subtotal,
                        ),
                        if (account.tipValue > 0)
                          _moneyRow(
                            spanish ? 'Propina' : 'Tip',
                            account.tipValue,
                          ),
                        _moneyRow(
                          spanish ? 'Total pagado' : 'Total paid',
                          account.totalValue,
                          bold: true,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _moneyRow(String label, int value, {bool bold = false}) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 4),
    child: Row(
      children: [
        Text(
          label,
          style: TextStyle(
            fontWeight: bold ? FontWeight.w800 : FontWeight.w400,
          ),
        ),
        const Spacer(),
        Text(
          formatPesos(value),
          style: TextStyle(
            fontWeight: bold ? FontWeight.w900 : FontWeight.w600,
          ),
        ),
      ],
    ),
  );

  String _methodLabel(ClientPaymentMethod? method) => switch (method) {
    ClientPaymentMethod.cash => spanish ? 'Efectivo' : 'Cash',
    ClientPaymentMethod.transfer => spanish ? 'Transferencia' : 'Transfer',
    ClientPaymentMethod.card => spanish ? 'Tarjeta' : 'Card',
    null => spanish ? 'Sin registrar' : 'Unknown',
  };
}

class _PaymentBadge extends StatelessWidget {
  const _PaymentBadge({required this.spanish, required this.method});

  final bool spanish;
  final ClientPaymentMethod? method;

  @override
  Widget build(BuildContext context) {
    final label = switch (method) {
      ClientPaymentMethod.cash => spanish ? 'Efectivo' : 'Cash',
      ClientPaymentMethod.transfer => spanish ? 'Transferencia' : 'Transfer',
      ClientPaymentMethod.card => spanish ? 'Tarjeta' : 'Card',
      null => spanish ? 'Sin registrar' : 'Unknown',
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: const Color(0xFFE9EFF5),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: Color(0xFF5C7187),
          fontSize: 10,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}
