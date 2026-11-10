// Lectura tolerante y TIPADA de JSON del backend. Evita los crashes que
// `strict-casts` destapó: un valor que llega como número, null o tipo
// inesperado ya no se asigna directo a String/int/bool.

String asString(dynamic value, [String fallback = '']) {
  if (value == null) return fallback;
  return value.toString();
}

String? asStringOrNull(dynamic value) {
  if (value == null) return null;
  return value.toString();
}

int asInt(dynamic value, [int fallback = 0]) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

double asDouble(dynamic value, [double fallback = 0]) {
  if (value is num) return value.toDouble();
  if (value is String) return double.tryParse(value) ?? fallback;
  return fallback;
}

bool asBool(dynamic value, [bool fallback = false]) {
  if (value is bool) return value;
  if (value is String) return value.toLowerCase() == 'true';
  if (value is num) return value != 0;
  return fallback;
}
