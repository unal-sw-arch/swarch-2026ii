import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/theme/app_theme.dart';
import 'package:mobile_app/core/widgets/hero_text_field.dart';

void main() {
  Widget buildTestableWidget(Widget child) {
    return MaterialApp(
      theme: AppTheme.lightTheme,
      home: Scaffold(
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: child,
        ),
      ),
    );
  }

  group('Pruebas de HeroTextField (Fase 1: Componentes Atómicos HeroUI)', () {
    testWidgets('Renderiza labelText, hintText y prefixIcon correctamente', (tester) async {
      await tester.pumpWidget(
        buildTestableWidget(
          const HeroTextField(
            labelText: 'Correo Institucional',
            hintText: 'ejemplo@unal.edu.co',
            prefixIcon: Icons.email_outlined,
          ),
        ),
      );

      expect(find.text('Correo Institucional'), findsOneWidget);
      expect(find.byIcon(Icons.email_outlined), findsOneWidget);
    });

    testWidgets('Emite cambios de texto mediante el callback onChanged', (tester) async {
      String changedText = '';

      await tester.pumpWidget(
        buildTestableWidget(
          HeroTextField(
            onChanged: (val) => changedText = val,
          ),
        ),
      );

      await tester.enterText(find.byType(TextFormField), 'Camilo Gomez');
      expect(changedText, equals('Camilo Gomez'));
    });

    testWidgets('HeroTextField.search muestra botón de limpiar y vacía el texto al pulsarlo', (tester) async {
      final controller = TextEditingController(text: 'Microfutbol');
      String currentQuery = 'Microfutbol';

      await tester.pumpWidget(
        buildTestableWidget(
          HeroTextField.search(
            controller: controller,
            hintText: 'Buscar eventos...',
            onChanged: (val) => currentQuery = val,
          ),
        ),
      );

      // Debe aparecer el icono de limpiar (clear) ya que hay texto
      expect(find.byIcon(Icons.clear), findsOneWidget);

      // Pulsar el botón de limpiar
      await tester.tap(find.byIcon(Icons.clear));
      await tester.pumpAndSettle();

      expect(controller.text, isEmpty);
      expect(currentQuery, isEmpty);
      // El botón de limpiar debe desaparecer al quedar vacío
      expect(find.byIcon(Icons.clear), findsNothing);
    });
  });
}
