import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/core/widgets/campus_tag.dart';
import 'package:mobile_app/core/widgets/empty_state.dart';
import 'package:mobile_app/core/widgets/fade_up_in.dart';
import 'package:mobile_app/core/widgets/floating_nav_bar.dart';
import 'package:mobile_app/core/widgets/hero_filter_pill.dart';
import 'package:mobile_app/core/widgets/skeleton_card.dart';

Widget _wrap(Widget child, {bool reduce = false}) => MaterialApp(
      theme: AppTheme.lightTheme,
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: reduce),
        child: Scaffold(body: child),
      ),
    );

void main() {
  test('CampusTag sigue la regla de frontend_web', () {
    expect(CampusTag.campusLabel('Edificio 453'), 'ED. 453 · CYT');
    expect(CampusTag.campusLabel('Canchas de Minas'), 'ED. 411 · MINAS');
    expect(CampusTag.campusLabel('Ingeniería'), 'ED. 401 · ING');
    expect(CampusTag.campusLabel('Plaza Ché'), 'ED. 102 · PLAZA CHÉ');
    expect(CampusTag.campusLabel(''), isNull);
  });

  testWidgets('HeroFilterPill avisa al tocar', (tester) async {
    var taps = 0;
    await tester.pumpWidget(_wrap(
      HeroFilterPill(label: 'Deportes', selected: true, onTap: () => taps++),
    ));
    await tester.tap(find.text('Deportes'));
    expect(taps, 1);
    expect(tester.getSize(find.byType(HeroFilterPill)).height, greaterThanOrEqualTo(48));
  });

  testWidgets('FloatingNavBar cambia de pestana', (tester) async {
    int? picked;
    await tester.pumpWidget(_wrap(
      FloatingNavBar(
        currentIndex: 0,
        onSelected: (i) => picked = i,
        destinations: const [
          NavDestination(label: 'Eventos', icon: Icons.event_outlined, selectedIcon: Icons.event),
          NavDestination(label: 'Mapa', icon: Icons.map_outlined, selectedIcon: Icons.map),
        ],
      ),
    ));
    await tester.tap(find.text('Mapa'));
    expect(picked, 1);
  });

  testWidgets('FloatingNavBar: al cambiar de pestaña, la anterior pierde el tinte', (tester) async {
    Widget bar(int i) => _wrap(FloatingNavBar(
          currentIndex: i,
          onSelected: (_) {},
          destinations: const [
            NavDestination(label: 'Eventos', icon: Icons.event_outlined, selectedIcon: Icons.event),
            NavDestination(label: 'Mapa', icon: Icons.map_outlined, selectedIcon: Icons.map),
          ],
        ));
    await tester.pumpWidget(bar(0));
    await tester.pumpWidget(bar(1));
    await tester.pump(const Duration(milliseconds: 400));
    Color? tintOf(String label) {
      final box = tester.widget<AnimatedContainer>(
        find.ancestor(of: find.text(label), matching: find.byType(AnimatedContainer)),
      );
      return (box.decoration as BoxDecoration).color;
    }

    expect(tintOf('Mapa'), isNot(Colors.transparent));
    expect(tintOf('Eventos'), Colors.transparent);
  });

  testWidgets('FloatingNavBar: el indicador mide y se redondea igual en todas las pestañas', (tester) async {
    tester.view.physicalSize = const Size(390, 844) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    const labels = ['Eventos', 'Implementos', 'Mapa', 'Perfil'];
    Widget bar(int i) => _wrap(Align(
          alignment: Alignment.bottomCenter,
          child: FloatingNavBar(
            currentIndex: i,
            onSelected: (_) {},
            destinations: [
              for (final l in labels) NavDestination(label: l, icon: Icons.circle_outlined, selectedIcon: Icons.circle),
            ],
          ),
        ));

    Size? first;
    for (var i = 0; i < labels.length; i++) {
      await tester.pumpWidget(bar(i));
      await tester.pump(const Duration(milliseconds: 400));
      final finder = find.ancestor(of: find.text(labels[i]), matching: find.byType(AnimatedContainer));
      final box = tester.widget<AnimatedContainer>(finder).decoration as BoxDecoration;
      final size = tester.getSize(finder);
      first ??= size;
      expect(size, first, reason: 'el indicador de ${labels[i]} cambia de tamaño');
      expect(box.borderRadius, BorderRadius.circular(18), reason: 'esquinas de ${labels[i]}');
      expect(size.height, greaterThanOrEqualTo(48));
    }

    // La barra ya no es una pildora completa.
    final barBox = tester
        .widgetList<Container>(find.byType(Container))
        .map((c) => c.decoration)
        .whereType<BoxDecoration>()
        .where((d) => d.border != null && d.borderRadius == BorderRadius.circular(24));
    expect(barBox, isNotEmpty);
  });

  testWidgets('FadeUpIn con menos movimiento muestra al instante', (tester) async {
    await tester.pumpWidget(_wrap(const FadeUpIn(child: Text('hola')), reduce: true));
    final opacity = tester.widget<Opacity>(find.byType(Opacity).first);
    expect(opacity.opacity, 1);
  });

  testWidgets('EmptyState y SkeletonList se dibujan', (tester) async {
    await tester.pumpWidget(_wrap(const EmptyState(title: 'Sin eventos')));
    expect(find.text('Sin eventos'), findsOneWidget);
    await tester.pumpWidget(_wrap(const SkeletonList(), reduce: true));
    expect(find.byType(SkeletonCard), findsWidgets);
  });
}
