// Opciones de Firebase para UNDesparche.
//
// Tomadas de la config web del proyecto `undesparche` (frontend_web/.env.example).
// Son credenciales PÚBLICAS de cliente (no secretas): identifican el proyecto,
// no autorizan nada por sí solas.
//
// NOTA: cuando tengas `google-services.json` del registro Android nativo
// (recomendado para FCM y para una appId propia de Android), reemplaza el bloque
// `android` con esos valores, o regenera este archivo con `flutterfire configure`.
import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart'
    show defaultTargetPlatform, kIsWeb, TargetPlatform;

/// Opciones por plataforma para inicializar Firebase.
class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return android;
      case TargetPlatform.iOS:
        return web; // No hay registro iOS; usa la config de cliente del proyecto.
      default:
        return web;
    }
  }

  /// Config del proyecto `undesparche` (misma que usa frontend_web).
  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyCoZkLqHwyj3mdn6_sRxdT95VzvATQTlVM',
    appId: '1:899346011232:web:6527fbb7261a8696d11f05',
    messagingSenderId: '899346011232',
    projectId: 'undesparche',
    authDomain: 'undesparche.firebaseapp.com',
    storageBucket: 'undesparche.firebasestorage.app',
  );

  /// Android: valores de android/app/google-services.json (app
  /// co.edu.unal.undesparche).
  static const FirebaseOptions android = FirebaseOptions(
    apiKey: 'AIzaSyDxnVmjOGxx9t4x8ZKa7q0b1XEW06zKgzg',
    appId: '1:899346011232:android:90583be3ba7d5d9ad11f05',
    messagingSenderId: '899346011232',
    projectId: 'undesparche',
    storageBucket: 'undesparche.firebasestorage.app',
  );
}
