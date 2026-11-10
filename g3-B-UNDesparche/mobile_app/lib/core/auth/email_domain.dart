import '../config/app_config.dart';

/// Regla RN-01: solo correos del dominio institucional UNAL.
/// Función pura, compartida y testeable.
bool isInstitutionalEmail(String email) {
  return email.toLowerCase().trim().endsWith('@${AppConfig.institutionalDomain}');
}
