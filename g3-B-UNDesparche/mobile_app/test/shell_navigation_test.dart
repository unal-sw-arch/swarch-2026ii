import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/core/widgets/floating_nav_bar.dart';
import 'package:mobile_app/features/events/presentation/screens/event_detail_screen.dart';
import 'package:mobile_app/features/events/presentation/widgets/event_card.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/main.dart';

void main() {
  setUp(() async {
    GoogleFonts.config.allowRuntimeFetching = false;
    await initializeDateFormatting('es', null);
    await di.sl.reset();
    await di.initDependencies(enableFirebaseAuth: false);
  });

  Future<void> boot(WidgetTester tester) async {
    tester.view.physicalSize = const Size(390, 844) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump(const Duration(milliseconds: 900));
  }

  testWidgets('"Ver en mapa" abre la pestaña Mapa con el evento seleccionado', (tester) async {
    await boot(tester);

    final firstCard = find.byType(EventCard).first;
    final title = tester.widget<EventCard>(firstCard).event.title;
    await tester.tap(firstCard);
    await tester.pumpAndSettle(); // go_router resuelve la ruta de forma asincrona
    expect(find.byType(EventDetailScreen), findsOneWidget);

    // En Windows se usa el mapa de reemplazo (sin Google Maps), que si muestra la tarjeta del evento.
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    await tester.tap(find.text('Ver en mapa'));
    await tester.pumpAndSettle();

    expect(find.byType(EventDetailScreen), findsNothing);
    expect(find.text('Ver Detalles del Evento'), findsOneWidget, reason: 'la tarjeta del evento seleccionado no aparece');
    expect(find.text(title), findsWidgets);
    debugDefaultTargetPlatformOverride = null; // Flutter lo exige antes de terminar el test
  });

  testWidgets('El ultimo evento de la lista queda por encima de la barra flotante', (tester) async {
    await boot(tester);
    await tester.fling(find.byType(Scrollable).last, const Offset(0, -3000), 5000);
    await tester.pump(const Duration(seconds: 2));

    final barTop = tester.getTopLeft(find.byType(FloatingNavBar)).dy;
    final lastBottom = tester.getBottomLeft(find.byType(EventCard).last).dy;
    expect(lastBottom, lessThanOrEqualTo(barTop), reason: 'la barra tapa el final de la lista');
  });
}
