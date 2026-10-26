// ignore_for_file: invalid_use_of_protected_member

part of '../split_bill_page.dart';

extension _SplitSummary on _SplitBillPageState {
  Widget _summaryPane() => ColoredBox(
    color: Colors.white,
    child: ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Text(
          '${_es ? 'Subcuenta' : 'Check'} · ${_active.name}',
          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 16),
        ..._active.allocations.entries.map((entry) {
          final item = _order.items
              .where((candidate) => candidate.id == entry.key)
              .first;
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(
              children: [
                Expanded(
                  child: Text('${_halfLabel(entry.value)}× ${item.name}'),
                ),
                Text(formatPesos((item.unitValue * entry.value / 2).round())),
              ],
            ),
          );
        }),
        const Divider(height: 28),
        _tipSelector(),
        const SizedBox(height: 18),
        PaymentMethodSwitch(
          spanish: _es,
          value: _active.method,
          onChanged: _active.paid
              ? (_) {}
              : (value) => setState(() => _active.method = value),
        ),
        const SizedBox(height: 22),
        _totals(),
        const SizedBox(height: 18),
        FilledButton.icon(
          key: const ValueKey('pay-split-account'),
          onPressed: _saving || _active.paid || _active.allocations.isEmpty
              ? null
              : _pay,
          icon: const Icon(Icons.point_of_sale_outlined),
          label: Text(
            _active.paid
                ? (_es ? 'Pagada' : 'Paid')
                : '${_es ? 'Cobrar' : 'Charge'} ${_active.name}',
          ),
        ),
        const SizedBox(height: 9),
        OutlinedButton(
          key: const ValueKey('save-split-bill'),
          onPressed: _saving ? null : _save,
          child: Text(_es ? 'Guardar división' : 'Save split'),
        ),
        if (_error != null) ...[
          const SizedBox(height: 12),
          Text(_error!, style: const TextStyle(color: Colors.redAccent)),
        ],
      ],
    ),
  );

  Widget _compactFooter() => Container(
    padding: const EdgeInsets.fromLTRB(18, 12, 18, 16),
    decoration: const BoxDecoration(
      color: Colors.white,
      border: Border(top: BorderSide(color: Color(0xFFDDE4EA))),
    ),
    child: Row(
      children: [
        Expanded(child: _totals()),
        const SizedBox(width: 12),
        OutlinedButton(
          onPressed: _saving ? null : () => _showMobilePaymentSheet(),
          child: Text(_es ? 'Opciones' : 'Options'),
        ),
        const SizedBox(width: 8),
        FilledButton(
          key: const ValueKey('pay-split-account-mobile'),
          onPressed: _saving || _active.paid || _active.allocations.isEmpty
              ? null
              : _pay,
          child: Text(_es ? 'Cobrar' : 'Charge'),
        ),
      ],
    ),
  );

  Widget _totals() {
    final subtotal = _subtotal(_active);
    final tip = _tip(_active);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${_es ? 'Subtotal' : 'Subtotal'} ${formatPesos(subtotal)}',
          style: const TextStyle(color: Color(0xFF718096)),
        ),
        if (tip > 0)
          Text(
            '${_es ? 'Propina' : 'Tip'} ${formatPesos(tip)}',
            style: const TextStyle(color: Color(0xFF718096)),
          ),
        Text(
          formatPesos(subtotal + tip),
          style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900),
        ),
      ],
    );
  }

  Widget _tipSelector({VoidCallback? refresh}) => Wrap(
    spacing: 7,
    runSpacing: 7,
    children: [
      for (final percent in [0, 10, 15])
        ChoiceChip(
          label: Text('$percent%'),
          selected: _active.customTip == 0 && _active.tipPercent == percent,
          onSelected: _active.paid
              ? null
              : (_) {
                  setState(() {
                    _active.tipPercent = percent;
                    _active.customTip = 0;
                  });
                  refresh?.call();
                },
        ),
      ActionChip(
        label: Text(_es ? 'Otro' : 'Other'),
        onPressed: _active.paid ? null : () => _customTip(refresh: refresh),
      ),
    ],
  );
}
