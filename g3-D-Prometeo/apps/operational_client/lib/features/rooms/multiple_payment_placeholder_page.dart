import 'package:flutter/material.dart';

class MultiplePaymentPlaceholderPage extends StatelessWidget {
  const MultiplePaymentPlaceholderPage({super.key, required this.spanish});

  final bool spanish;

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFFAF9F6),
    appBar: AppBar(
      backgroundColor: const Color(0xFFFAF9F6),
      surfaceTintColor: Colors.transparent,
      title: Text(
        spanish ? 'Varios medios de pago' : 'Multiple payment methods',
      ),
    ),
    body: const SizedBox.expand(),
  );
}
