import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_app/core/auth/email_domain.dart';

void main() {
  group('RN-01: dominio institucional', () {
    test('acepta correos @unal.edu.co', () {
      expect(isInstitutionalEmail('cgomez@unal.edu.co'), isTrue);
      expect(isInstitutionalEmail('CGOMEZ@UNAL.EDU.CO'), isTrue);
      expect(isInstitutionalEmail('  ana@unal.edu.co  '), isTrue);
    });

    test('rechaza otros dominios', () {
      expect(isInstitutionalEmail('usuario@gmail.com'), isFalse);
      expect(isInstitutionalEmail('alguien@unal.edu.com'), isFalse);
      expect(isInstitutionalEmail('alguien@fake-unal.edu.co.evil.com'), isFalse);
      expect(isInstitutionalEmail(''), isFalse);
    });
  });
}
