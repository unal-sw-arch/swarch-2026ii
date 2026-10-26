import 'package:flutter/material.dart';

import '../../models/client_order.dart';
import '../../utils/money.dart';
import 'payment_method_switch.dart';

part 'split_bill/account_items.dart';
part 'split_bill/summary.dart';
part 'split_bill/payment_sheet.dart';
part 'split_bill/draft.dart';

typedef SaveSplitAccounts =
    Future<ClientOrder> Function(List<SplitAccountWrite> accounts);
typedef PaySplitAccount =
    Future<ClientOrder> Function(
      int accountId,
      ClientPaymentMethod paymentMethod,
      int tipPercent,
      int customTip,
    );

class SplitBillPage extends StatefulWidget {
  const SplitBillPage({
    super.key,
    required this.spanish,
    required this.tableLabel,
    required this.order,
    required this.onSave,
    required this.onPay,
  });

  final bool spanish;
  final String tableLabel;
  final ClientOrder order;
  final SaveSplitAccounts onSave;
  final PaySplitAccount onPay;

  @override
  State<SplitBillPage> createState() => _SplitBillPageState();
}

class _SplitBillPageState extends State<SplitBillPage> {
  static const _background = Color(0xFFFAF9F6);
  static const _accent = Color(0xFF52677D);
  static const _accentSoft = Color(0xFFE9EFF5);
  static const _border = Color(0xFFDCE4EC);
  late ClientOrder _order;
  final List<_SplitDraft> _accounts = [];
  int _selected = 0;
  bool _saving = false;
  String? _error;

  bool get _es => widget.spanish;
  _SplitDraft get _active => _accounts[_selected];

  @override
  void initState() {
    super.initState();
    _order = widget.order;
    _syncAccounts(_order);
  }

  void _syncAccounts(ClientOrder order, {int? preferredPosition}) {
    _accounts
      ..clear()
      ..addAll(
        order.splitAccounts.map(
          (account) => _SplitDraft(
            id: account.id,
            name: account.name,
            position: account.position,
            paid: account.isPaid,
            method: account.paymentMethod ?? ClientPaymentMethod.cash,
            tipPercent: account.tipPercent,
            customTip: account.tipPercent == 0 ? account.tipValue : 0,
            paidSubtotal: account.subtotal,
            paidTip: account.tipValue,
            allocations: {
              for (final allocation in account.allocations)
                allocation.orderItemId: allocation.halfUnits,
            },
          ),
        ),
      );
    if (_accounts.every((account) => account.paid)) _addAccount(notify: false);
    _accounts.sort((left, right) => left.position.compareTo(right.position));
    final target =
        preferredPosition ??
        (_accounts.where((account) => !account.paid).firstOrNull?.position ??
            _accounts.first.position);
    _selected = _accounts.indexWhere((account) => account.position == target);
    if (_selected < 0) _selected = 0;
  }

  void _addAccount({bool notify = true}) {
    final position = _accounts.isEmpty
        ? 0
        : _accounts
                  .map((account) => account.position)
                  .reduce((left, right) => left > right ? left : right) +
              1;
    _accounts.add(
      _SplitDraft(
        name: '${_es ? 'Cliente' : 'Guest'} ${position + 1}',
        position: position,
      ),
    );
    _selected = _accounts.length - 1;
    if (notify) setState(() {});
  }

  int _itemCapacity(ClientOrderItem item) => item.quantity * 2;

  int _usedByOthers(int itemId) => _accounts
      .where((account) => !identical(account, _active))
      .fold(0, (sum, account) => sum + (account.allocations[itemId] ?? 0));

  int _remaining(ClientOrderItem item) =>
      (_itemCapacity(item) - _usedByOthers(item.id)).clamp(
        0,
        item.quantity * 2,
      );

  void _changeAllocation(ClientOrderItem item, int change) {
    if (_active.paid) return;
    final current = _active.allocations[item.id] ?? 0;
    final next = (current + change).clamp(0, _remaining(item));
    setState(() {
      if (next == 0) {
        _active.allocations.remove(item.id);
      } else {
        _active.allocations[item.id] = next;
      }
    });
  }

  void _toggleItem(ClientOrderItem item) {
    if (_active.paid) return;
    final current = _active.allocations[item.id] ?? 0;
    setState(() {
      if (current > 0) {
        _active.allocations.remove(item.id);
      } else {
        final remaining = _remaining(item);
        if (remaining > 0) _active.allocations[item.id] = remaining;
      }
    });
  }

  void _selectAll() {
    if (_active.paid) return;
    setState(() {
      for (final item in _order.items) {
        final remaining = _remaining(item);
        if (remaining > 0) _active.allocations[item.id] = remaining;
      }
    });
  }

  void _clearActive() {
    if (_active.paid) return;
    setState(_active.allocations.clear);
  }

  void _divideEqually() {
    final drafts = _accounts.where((account) => !account.paid).toList();
    if (drafts.isEmpty) return;
    setState(() {
      for (final draft in drafts) {
        draft.allocations.clear();
      }
      for (final item in _order.items) {
        final paid = _accounts
            .where((account) => account.paid)
            .fold(
              0,
              (sum, account) => sum + (account.allocations[item.id] ?? 0),
            );
        final available = item.quantity * 2 - paid;
        for (var half = 0; half < available; half++) {
          final draft = drafts[half % drafts.length];
          draft.allocations[item.id] = (draft.allocations[item.id] ?? 0) + 1;
        }
      }
    });
  }

  int _subtotal(_SplitDraft account) {
    if (account.paid) return account.paidSubtotal;
    var total = 0;
    for (final item in _order.items) {
      final halves = account.allocations[item.id] ?? 0;
      final paidHalves = _accounts
          .where((candidate) => candidate.paid)
          .fold<int>(
            0,
            (sum, candidate) => sum + (candidate.allocations[item.id] ?? 0),
          );
      total +=
          (item.unitValue * (paidHalves + halves) ~/ 2) -
          (item.unitValue * paidHalves ~/ 2);
    }
    return total;
  }

  int _tip(_SplitDraft account) => account.paid
      ? account.paidTip
      : account.customTip > 0
      ? account.customTip
      : (_subtotal(account) * account.tipPercent / 100).round();

  List<SplitAccountWrite> _draftWrites() => [
    for (final account in _accounts.where((account) => !account.paid))
      SplitAccountWrite(
        name: account.name,
        position: account.position,
        tipPercent: account.tipPercent,
        customTip: account.customTip,
        allocations: account.allocations,
      ),
  ];

  Future<ClientOrder?> _save({bool quiet = false}) async {
    if (_saving) return null;
    final preferred = _active.position;
    final method = _active.method;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final order = await widget.onSave(_draftWrites());
      if (!mounted) return order;
      setState(() {
        _order = order;
        _syncAccounts(order, preferredPosition: preferred);
        if (!_active.paid) _active.method = method;
      });
      return order;
    } on Object {
      if (mounted && !quiet) {
        setState(() {
          _error = _es
              ? 'No se pudo guardar la división.'
              : 'The split could not be saved.';
        });
      }
      return null;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _pay() async {
    if (_active.paid || _active.allocations.isEmpty) return;
    final position = _active.position;
    final method = _active.method;
    final tipPercent = _active.tipPercent;
    final customTip = _active.customTip;
    final saved = await _save();
    if (saved == null || !mounted) return;
    final account = saved.splitAccounts
        .where((candidate) => candidate.position == position)
        .firstOrNull;
    if (account == null) return;
    setState(() => _saving = true);
    try {
      final order = await widget.onPay(
        account.id,
        method,
        tipPercent,
        customTip,
      );
      if (!mounted) return;
      if (order.status == 'closed') {
        Navigator.pop(context, true);
        return;
      }
      setState(() {
        _order = order;
        _syncAccounts(order);
      });
    } on Object {
      if (mounted) {
        setState(() {
          _error = _es ? 'No se pudo registrar el pago.' : 'Payment failed.';
        });
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: _background,
    appBar: AppBar(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      leading: IconButton(
        key: const ValueKey('close-split-bill'),
        onPressed: _saving ? null : () => Navigator.pop(context),
        icon: const Icon(Icons.close_rounded),
      ),
      title: Column(
        children: [
          Text(
            '${_es ? 'Separar cuenta' : 'Split bill'} · ${widget.tableLabel}',
          ),
          Text(
            '${_accounts.length} ${_es ? 'subcuentas' : 'checks'}',
            style: const TextStyle(fontSize: 13, color: Color(0xFF718096)),
          ),
        ],
      ),
      centerTitle: true,
    ),
    body: LayoutBuilder(
      builder: (context, constraints) {
        final desktop = constraints.maxWidth >= 850;
        return Column(
          children: [
            _accountTabs(),
            _billProgress(),
            const Divider(height: 1),
            Expanded(
              child: desktop
                  ? Row(
                      children: [
                        Expanded(flex: 3, child: _itemPane()),
                        const VerticalDivider(width: 1),
                        Expanded(flex: 2, child: _summaryPane()),
                      ],
                    )
                  : _itemPane(),
            ),
            if (!desktop) _compactFooter(),
          ],
        );
      },
    ),
  );
}
