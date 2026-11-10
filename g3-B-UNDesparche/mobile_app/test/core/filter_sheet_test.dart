import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/core/widgets/filter_sheet.dart';
import 'package:mobile_app/features/inventory/presentation/widgets/implement_card.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/main.dart';

const _groups = [
  FilterGroup(
    id: 'faculty',
    title: 'Facultad',
    defaultKey: 'all',
    selectedKey: 'all',
    options: [
      FilterOption('all', 'Todas'),
      FilterOption('ing', 'Ingeniería'),
      FilterOption('hum', 'Ciencias Humanas'),
      FilterOption('bie', 'Bienestar Central'),
    ],
  ),
  FilterGroup(
    id: 'category',
    title: 'Categoría',
    defaultKey: 'all',
    selectedKey: 'all',
    options: [
      FilterOption('all', 'Todos'),
      FilterOption('balls', 'Balones'),
      FilterOption('rackets', 'Raquetas'),
      FilterOption('boardGames', 'Juegos de Mesa'),
    ],
  ),
];

/// Abre la hoja desde un boton y guarda lo que devuelve.
Widget _host({required ValueSetter<Map<String, String>?> onResult, List<FilterGroup> groups = _groups, Brightness brightness = Brightness.light}) {
  return MaterialApp(
    theme: brightness == Brightness.dark ? AppTheme.darkTheme : AppTheme.lightTheme,
    home: Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: FilterButton(
            activeCount: activeFilterCount(groups),
            onTap: () async => onResult(await showFilterSheet(context, groups: groups)),
          ),
        ),
      ),
    ),
  );
}

void main() {
  setUp(() async {
    GoogleFonts.config.allowRuntimeFetching = false;
    await initializeDateFormatting('es', null);
  });

  group('Hoja de filtros', () {
    testWidgets('muestra todas las opciones sin scroll horizontal', (tester) async {
      tester.view.physicalSize = const Size(320, 568) * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_host(onResult: (_) {}));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.byType(FilterOptionTile), findsNWidgets(8));
      // Ninguna zona desplazable en horizontal: las fichas saltan de linea.
      final horizontal = tester
          .widgetList<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .where((s) => s.scrollDirection == Axis.horizontal);
      expect(horizontal, isEmpty);
      // Cada ficha cabe en el ancho de la pantalla.
      for (final pill in find.byType(FilterOptionTile).evaluate()) {
        final box = pill.renderObject! as RenderBox;
        expect(box.localToGlobal(Offset.zero).dx + box.size.width, lessThanOrEqualTo(320));
      }
    });

    testWidgets('elegir y aplicar devuelve la seleccion', (tester) async {
      Map<String, String>? result;
      await tester.pumpWidget(_host(onResult: (r) => result = r));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      // Sin filtros puestos, "Limpiar" esta apagado.
      expect(tester.widget<TextButton>(find.widgetWithText(TextButton, 'Limpiar')).onPressed, isNull);

      await tester.tap(find.widgetWithText(FilterOptionTile, 'Ingeniería'));
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Balones'));
      await tester.pump();
      await tester.tap(find.text('Aplicar filtros'));
      await tester.pumpAndSettle();

      expect(result, {'faculty': 'ing', 'category': 'balls'});
    });

    testWidgets('el boton cuenta los resultados y se apaga si no hay ninguno', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: FilterButton(
                activeCount: 0,
                onTap: () => showFilterSheet(
                  context,
                  groups: _groups,
                  resultNoun: ('implemento', 'implementos'),
                  resultCount: (sel) => sel['category'] == 'rackets' ? 0 : (sel['category'] == 'balls' ? 1 : 5),
                ),
              ),
            ),
          ),
        ),
      ));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      expect(find.text('Ver 5 implementos'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Balones'));
      await tester.pump();
      expect(find.text('Ver 1 implemento'), findsOneWidget);
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Raquetas'));
      await tester.pump();
      expect(find.text('Sin resultados'), findsOneWidget);
      // Apagado: tocarlo no cierra la hoja.
      await tester.tap(find.text('Sin resultados'));
      await tester.pumpAndSettle();
      expect(find.text('Filtros'), findsOneWidget);
    });

    testWidgets('las opciones van en 2 columnas del mismo ancho', (tester) async {
      tester.view.physicalSize = const Size(360, 740) * 3;
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(_host(onResult: (_) {}));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      final tiles = find.byType(FilterOptionTile).evaluate().map((e) => e.renderObject! as RenderBox).toList();
      final widths = tiles.map((b) => b.size.width.round()).toSet();
      expect(widths.length, 1, reason: 'todas las casillas deben medir igual');
      final columns = tiles.map((b) => b.localToGlobal(Offset.zero).dx.round()).toSet();
      expect(columns.length, 2);
      for (final b in tiles) {
        expect(b.size.height, greaterThanOrEqualTo(48));
      }
    });
    testWidgets('"Limpiar" vuelve todo a "sin filtro" y se puede aplicar', (tester) async {
      Map<String, String>? result;
      final active = [
        _groups[0],
        const FilterGroup(
          id: 'category',
          title: 'Categoría',
          defaultKey: 'all',
          selectedKey: 'rackets',
          options: [FilterOption('all', 'Todos'), FilterOption('rackets', 'Raquetas')],
        ),
      ];
      await tester.pumpWidget(_host(onResult: (r) => result = r, groups: active));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Limpiar'));
      await tester.pump();
      await tester.tap(find.text('Aplicar filtros'));
      await tester.pumpAndSettle();

      expect(result, {'faculty': 'all', 'category': 'all'});
    });

    testWidgets('cerrar sin aplicar no cambia nada', (tester) async {
      var called = false;
      Map<String, String>? result = {'x': 'y'};
      await tester.pumpWidget(_host(onResult: (r) {
        called = true;
        result = r;
      }));
      await tester.tap(find.byType(FilterButton));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilterOptionTile, 'Balones'));
      await tester.tapAt(const Offset(10, 10)); // toca fuera de la hoja
      await tester.pumpAndSettle();

      expect(called, isTrue);
      expect(result, isNull);
    });

    for (final brightness in Brightness.values) {
      testWidgets('zonas de toque y etiquetas en ${brightness.name}', (tester) async {
        final handle = tester.ensureSemantics();
        await tester.pumpWidget(_host(onResult: (_) {}, brightness: brightness));
        await tester.tap(find.byType(FilterButton));
        await tester.pumpAndSettle();
        await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
        await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
        handle.dispose();
      });
    }
  });

  group('Boton y fichas de filtros activos', () {
    testWidgets('el boton muestra cuantos filtros hay y lo dice en voz alta', (tester) async {
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(body: FilterButton(activeCount: 2, onTap: () {})),
      ));
      expect(find.text('2'), findsOneWidget);
      expect(find.bySemanticsLabel('Filtros, 2 activos'), findsOneWidget);
      handle.dispose();
    });

    testWidgets('quitar una ficha avisa de cual grupo', (tester) async {
      String? removed;
      final groups = [
        const FilterGroup(
          id: 'category',
          title: 'Categoría',
          defaultKey: 'all',
          selectedKey: 'balls',
          options: [FilterOption('all', 'Todos'), FilterOption('balls', 'Balones')],
        ),
      ];
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(body: ActiveFilterChips(groups: groups, onRemove: (id) => removed = id)),
      ));
      await tester.tap(find.text('Balones'));
      expect(removed, 'category');
    });

    testWidgets('sin filtros activos no ocupa espacio', (tester) async {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.lightTheme,
        home: Scaffold(body: ActiveFilterChips(groups: _groups, onRemove: (_) {})),
      ));
      expect(tester.getSize(find.byType(ActiveFilterChips)).height, 0);
    });
  });

  testWidgets('Implementos: filtrar por categoria desde la hoja deja solo esos implementos', (tester) async {
    await di.sl.reset();
    await di.initDependencies(enableFirebaseAuth: false);
    tester.view.physicalSize = const Size(390, 844) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump(const Duration(milliseconds: 900));

    await tester.tap(find.text('Implementos').last);
    await tester.pumpAndSettle();
    final before = find.byType(ImplementCard, skipOffstage: false).evaluate().length;
    expect(before, greaterThan(1));

    await tester.tap(find.byType(FilterButton).last);
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilterOptionTile, 'Juegos de Mesa'));
    await tester.pump();
    // El boton dice cuantos implementos quedarian.
    final apply = find.textContaining(RegExp(r'^Ver \d+ implemento'));
    expect(apply, findsOneWidget);
    await tester.tap(apply);
    await tester.pumpAndSettle();

    final after = find.byType(ImplementCard, skipOffstage: false).evaluate().length;
    expect(after, lessThan(before));
    expect(find.text('1'), findsWidgets);

    // Quitar la ficha devuelve la lista completa.
    await tester.tap(find.text('Juegos de Mesa').last);
    await tester.pumpAndSettle();
    expect(find.byType(ImplementCard, skipOffstage: false).evaluate().length, before);
  });
}
