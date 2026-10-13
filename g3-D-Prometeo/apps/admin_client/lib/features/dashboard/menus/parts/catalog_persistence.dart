// ignore_for_file: invalid_use_of_protected_member

part of '../menus_page.dart';

extension _MenusCatalogPersistence on _MenusPageState {
  Future<void> _write(Future<dynamic> Function() action) async {
    try {
      await action();
      if (mounted) await _load();
    } on Object {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            backgroundColor: const Color(0xFFB64A4A),
            content: Text(_es ? 'No se pudo guardar.' : 'Could not save.'),
          ),
        );
      }
    }
  }
}
