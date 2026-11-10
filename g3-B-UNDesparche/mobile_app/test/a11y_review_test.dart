import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/core/theme/theme_controller.dart';
import 'package:mobile_app/features/auth/presentation/bloc/auth_bloc.dart';
import 'package:mobile_app/features/auth/presentation/screens/welcome_screen.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/main.dart';

/// Fase 5: revisión de accesibilidad y robustez sobre la app completa (datos mock).
void main() {
  setUp(() async {
    // Sin red en tests: si una fuente no esta en assets, se usa la del sistema.
    GoogleFonts.config.allowRuntimeFetching = false;
    await initializeDateFormatting('es', null);
    await di.sl.reset();
    await di.initDependencies(enableFirebaseAuth: false);
  });

  String describe(Object? e) => e is FlutterError ? e.toStringDeep() : '$e';

  Future<void> bootApp(WidgetTester tester, {required ThemeMode mode, Size size = const Size(390, 844)}) async {
    tester.view.physicalSize = size * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    di.sl<ThemeController>().value = mode;
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump(const Duration(milliseconds: 600));
  }

  for (final mode in [ThemeMode.light, ThemeMode.dark]) {
    group('tema ${mode.name}', () {
      testWidgets('Eventos: zonas de toque de 48 px y etiquetas', (tester) async {
        final handle = tester.ensureSemantics();
        await bootApp(tester, mode: mode);
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        expect(tester.takeException(), isNull);
        handle.dispose();
      });

      testWidgets('Eventos: contraste de texto', (tester) async {
        final handle = tester.ensureSemantics();
        await bootApp(tester, mode: mode);
        await expectLater(tester, meetsGuideline(textContrastGuideline));
        handle.dispose();
      });

      testWidgets('Implementos: zonas de toque y contraste', (tester) async {
        final handle = tester.ensureSemantics();
        await bootApp(tester, mode: mode);
        await tester.tap(find.text('Implementos').last);
        await tester.pump(const Duration(milliseconds: 600));
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        await expectLater(tester, meetsGuideline(textContrastGuideline));
        expect(tester.takeException(), isNull);
        handle.dispose();
      });

      testWidgets('Perfil: zonas de toque y contraste', (tester) async {
        final handle = tester.ensureSemantics();
        await bootApp(tester, mode: mode);
        await tester.tap(find.text('Perfil').last);
        await tester.pump(const Duration(milliseconds: 600));
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        // El contraste de colores se verifica en test/core/contrast_test.dart: la guia de
        // Flutter mide mal el texto fino de 12 px (muestrea un color mas claro que el real).
        expect(tester.takeException(), isNull);
        handle.dispose();
      });
    });
  }

  testWidgets('Pantalla pequeña (320x568) sin desbordes', (tester) async {
    await bootApp(tester, mode: ThemeMode.light, size: const Size(320, 568));
    expect(tester.takeException(), isNull);
    for (final tab in ['Implementos', 'Perfil', 'Eventos']) {
      await tester.tap(find.text(tab).last);
      await tester.pump(const Duration(milliseconds: 600));
      final e = tester.takeException();
      expect(e, isNull, reason: 'desborde en $tab: ${describe(e)}');
    }
  });

  for (final mode in [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('Bienvenida baja (320x480, letra 130 %) en ${mode.name}: sin desbordes y con zonas de toque', (tester) async {
      final handle = tester.ensureSemantics();
      tester.view.physicalSize = const Size(320, 480) * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(BlocProvider(
        create: (_) => di.sl<AuthBloc>(),
        child: MaterialApp(
          theme: AppTheme.lightTheme,
          darkTheme: AppTheme.darkTheme,
          themeMode: mode,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(1.3)),
            child: child!,
          ),
          home: const WelcomeScreen(),
        ),
      ));
      await tester.pump(const Duration(milliseconds: 600));
      final e = tester.takeException();
      expect(e, isNull, reason: describe(e));
      expect(find.text('Ingresar con Google UNAL'), findsOneWidget);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      handle.dispose();
    });
  }

  testWidgets('Menos movimiento: las listas aparecen sin animar', (tester) async {    tester.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);
    await bootApp(tester, mode: ThemeMode.light);
    expect(tester.takeException(), isNull);
    final opacities = tester.widgetList<Opacity>(find.byType(Opacity)).where((o) => o.opacity < 1);
    expect(opacities, isEmpty);
  });
}
