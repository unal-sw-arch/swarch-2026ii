import 'package:flutter/material.dart';

import '../../models/client_order.dart';

class PaymentMethodSwitch extends StatelessWidget {
  const PaymentMethodSwitch({
    super.key,
    required this.spanish,
    required this.value,
    required this.onChanged,
  });

  final bool spanish;
  final ClientPaymentMethod value;
  final ValueChanged<ClientPaymentMethod> onChanged;

  @override
  Widget build(BuildContext context) => Container(
    key: const ValueKey('payment-method-switch'),
    height: 46,
    padding: const EdgeInsets.all(4),
    decoration: BoxDecoration(
      color: const Color(0xFFE9EDF1),
      borderRadius: BorderRadius.circular(13),
    ),
    child: LayoutBuilder(
      builder: (context, constraints) {
        final segmentWidth =
            constraints.maxWidth / ClientPaymentMethod.values.length;
        return Stack(
          children: [
            AnimatedPositioned(
              duration: const Duration(milliseconds: 230),
              curve: Curves.easeOutCubic,
              left: segmentWidth * value.index,
              top: 0,
              bottom: 0,
              width: segmentWidth,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: const [
                    BoxShadow(color: Color(0x18000000), blurRadius: 7),
                  ],
                ),
              ),
            ),
            Row(
              children: [
                for (final method in ClientPaymentMethod.values)
                  Expanded(
                    child: InkWell(
                      key: ValueKey('payment-method-${method.apiValue}'),
                      onTap: () => onChanged(method),
                      borderRadius: BorderRadius.circular(10),
                      child: Center(
                        child: AnimatedDefaultTextStyle(
                          duration: const Duration(milliseconds: 180),
                          style: TextStyle(
                            fontWeight: FontWeight.w700,
                            color: value == method
                                ? const Color(0xFF52677D)
                                : const Color(0xFF7A8087),
                          ),
                          child: Text(switch (method) {
                            ClientPaymentMethod.cash =>
                              spanish ? 'Efectivo' : 'Cash',
                            ClientPaymentMethod.transfer =>
                              spanish ? 'Transferencia' : 'Transfer',
                            ClientPaymentMethod.card =>
                              spanish ? 'Tarjeta' : 'Card',
                          }, textAlign: TextAlign.center),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ],
        );
      },
    ),
  );
}
