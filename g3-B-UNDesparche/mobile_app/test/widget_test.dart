import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:mobile_app/main.dart';
import 'package:mobile_app/injection_container.dart' as di;
import 'package:mobile_app/features/home/presentation/screens/main_shell_screen.dart';

void main() {
  setUp(() async {
    await initializeDateFormatting('es', null);
    await di.sl.reset();
    await di.initDependencies(enableFirebaseAuth: false);
  });

  testWidgets('Carga inicial de la app UNDesparche con GoRouter', (WidgetTester tester) async {
    await tester.pumpWidget(const UNDesparcheApp());
    await tester.pump();
    // Superar el retardo de 200ms de autenticación inicial
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    // Superar los retardos de 600ms de carga de eventos e inventario
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pump();

    expect(find.byType(MainShellScreen), findsOneWidget);
  });
}
