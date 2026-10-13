import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'features/auth/admin_auth_view.dart';
import 'services/admin_api.dart';
import 'features/dashboard/admin_dashboard.dart';
import 'features/auth/login_page.dart';
import 'features/splash/splash_screen.dart';

/// Atajo exclusivo de debug. Valores: splash, login, register o dashboard.
const _debugStartScreen = String.fromEnvironment(
  'START_SCREEN',
  defaultValue: 'splash',
);

void main() => runApp(const RestaurantApp());

class RestaurantApp extends StatefulWidget {
  const RestaurantApp({
    super.key,
    this.initialChecks = runInitialChecks,
    this.restoreSession = restoreStoredAdminSession,
  });

  final Future<void> Function() initialChecks;
  final Future<AdminSession?> Function() restoreSession;

  @override
  State<RestaurantApp> createState() => _RestaurantAppState();
}

class _RestaurantAppState extends State<RestaurantApp>
    with WidgetsBindingObserver {
  Locale _locale = WidgetsBinding.instance.platformDispatcher.locale;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeLocales(List<Locale>? locales) {
    setState(() => _locale = locales?.first ?? const Locale('en'));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = AppStrings.fromLocale(_locale);
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Restaurant Admin',
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF9F9F8),
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF8798AC)),
        fontFamily: 'sans-serif',
      ),
      home: _initialScreen(strings),
    );
  }

  Widget _initialScreen(AppStrings strings) {
    // En release este bloque nunca se ejecuta, aunque START_SCREEN esté
    // definido accidentalmente durante la compilación.
    if (kDebugMode) {
      switch (_debugStartScreen.toLowerCase()) {
        case 'dashboard':
          return AdminDashboard(strings: strings, onLogout: () {});
        case 'register':
          return AdminAuthView(strings: strings, initialRegistration: true);
        case 'login':
          return AdminAuthView(strings: strings);
        case 'splash':
          break;
      }
    }

    return SplashScreen(
      strings: strings,
      initialChecks: widget.initialChecks,
      restoreSession: widget.restoreSession,
    );
  }
}
