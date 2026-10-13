// ignore_for_file: invalid_use_of_protected_member

part of '../split_bill_page.dart';

extension _SplitPaymentSheet on _SplitBillPageState {
  Future<void> _customTip({VoidCallback? refresh}) async {
    final controller = TextEditingController(text: '${_active.customTip}');
    final value = await showDialog<int>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_es ? 'Propina personalizada' : 'Custom tip'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(prefixText: r'$ '),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(_es ? 'Cancelar' : 'Cancel'),
          ),
          FilledButton(
            onPressed: () =>
                Navigator.pop(context, int.tryParse(controller.text)),
            child: Text(_es ? 'Aplicar' : 'Apply'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (value == null || value < 0 || !mounted) return;
    setState(() {
      _active.customTip = value;
      _active.tipPercent = 0;
    });
    refresh?.call();
  }

  Future<void> _showMobilePaymentSheet() => showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    builder: (_) => Padding(
      padding: const EdgeInsets.fromLTRB(18, 4, 18, 28),
      child: StatefulBuilder(
        builder: (context, setSheetState) => Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _tipSelector(refresh: () => setSheetState(() {})),
            const SizedBox(height: 16),
            PaymentMethodSwitch(
              spanish: _es,
              value: _active.method,
              onChanged: (value) {
                setState(() => _active.method = value);
                setSheetState(() {});
              },
            ),
            const SizedBox(height: 14),
            OutlinedButton(
              onPressed: _saving
                  ? null
                  : () {
                      Navigator.pop(context);
                      _save();
                    },
              child: Text(_es ? 'Guardar división' : 'Save split'),
            ),
          ],
        ),
      ),
    ),
  );

  String _halfLabel(int halves) {
    final whole = halves ~/ 2;
    if (halves.isEven) return '$whole';
    return whole == 0 ? '½' : '$whole½';
  }
}
