import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/main.dart';
import 'package:mobile_app/features/home/presentation/screens/main_shell_screen.dart';
import 'package:mobile_app/features/inventory/presentation/widgets/active_reservation_banner.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    await initializeDateFormatting('es', null);
    await di.sl.reset();
    // Datos mock + sin Firebase: la sesión resuelve a estudiante activo.
    await di.initDependencies(enableFirebaseAuth: false);
  });

  Future<void> settle(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump();
  }

  testWidgets('Flujo: entrar, reservar un implemento y cancelar', (tester) async {
    await tester.pumpWidget(const UNDesparcheApp());
    await settle(tester);

    // La app abre en el shell con sus 4 pestañas.
    expect(find.byType(MainShellScreen), findsOneWidget);

    // Ir a la pestaña Implementos.
    await tester.tap(find.text('Implementos').first);
    await settle(tester);

    // Reservar el primer implemento disponible.
    final reserveButtons = find.textContaining('Reservar (10 min)');
    expect(reserveButtons, findsWidgets);
    await tester.tap(reserveButtons.first);
    await settle(tester);

    // Aceptar el descargo de responsabilidad (RF-20 / RF-21).
    final accept = find.text('Confirmar y Reservar');
    expect(accept, findsOneWidget);
    await tester.tap(accept);
    await settle(tester);

    // Aparece el banner de reserva con el temporizador de 10 min (RN-02).
    expect(find.byType(ActiveReservationBanner), findsOneWidget);

    // Cancelar la reserva.
    final cancelText = find.text('Cancelar');
    expect(cancelText, findsWidgets);
    await tester.tap(cancelText.first);
    await settle(tester);

    // El banner desaparece tras cancelar.
    expect(find.byType(ActiveReservationBanner), findsNothing);
  });
}
