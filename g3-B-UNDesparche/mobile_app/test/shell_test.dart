import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/theme/theme_controller.dart';
import 'package:mobile_app/core/widgets/floating_nav_bar.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/main.dart';

void main() {
  setUp(() async {
    await initializeDateFormatting('es', null);
    await di.sl.reset();
    await di.initDependencies(enableFirebaseAuth: false);
  });

  testWidgets('El shell muestra la barra flotante con 4 pestañas', (tester) async {
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump();

    expect(find.byType(FloatingNavBar), findsOneWidget);
    for (final label in ['Eventos', 'Implementos', 'Mapa', 'Perfil']) {
      expect(find.text(label), findsWidgets);
    }
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('ThemeController cambia el tema de la app', (tester) async {
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 800));

    di.sl<ThemeController>().value = ThemeMode.dark;
    await tester.pump();
    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.themeMode, ThemeMode.dark);

    unawaited(di.sl<ThemeController>().toggle(Brightness.dark));
    expect(di.sl<ThemeController>().value, ThemeMode.light);
  });
}
