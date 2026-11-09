import 'package:flutter/material.dart';

import '../api/gateway_client.dart';
import '../kitchen/queue_controller.dart' show ActionResult;

String formatClock(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}:${t.second.toString().padLeft(2, '0')}';

class StatusBanner extends StatelessWidget {
  const StatusBanner({super.key, required this.error, required this.lastUpdated, required this.onRetry});

  final ApiException error;
  final DateTime? lastUpdated;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final stale = lastUpdated == null ? '' : ' Mostrando datos de las ${formatClock(lastUpdated!)}.';
    final text = switch (error) {
      NetworkException() => '${error.message}$stale',
      _ => error.message,
    };
    final scheme = Theme.of(context).colorScheme;
    return Material(
      key: const Key('error-banner'),
      color: scheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Row(
          children: [
            Icon(error is NetworkException ? Icons.wifi_off : Icons.error_outline, color: scheme.onErrorContainer),
            const SizedBox(width: 12),
            Expanded(child: Text(text, style: TextStyle(color: scheme.onErrorContainer))),
            TextButton.icon(
              key: const Key('retry'),
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ],
        ),
      ),
    );
  }
}

void showResult(BuildContext context, ActionResult result) {
  final messenger = ScaffoldMessenger.of(context);
  messenger
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(
      content: Text(result.message),
      backgroundColor: result.ok ? Colors.green.shade800 : Theme.of(context).colorScheme.error,
    ));
}

