import 'package:flutter/material.dart';

import 'features/auth/login_page.dart';
import 'features/splash/splash_screen.dart';
import 'models/client_user.dart';
import 'services/client_auth_api.dart';

void main() => runApp(const RestaurantApp());

class RestaurantApp extends StatefulWidget {
  const RestaurantApp({
    super.key,
    this.initialChecks = runInitialChecks,
    this.restoreSession = restoreStoredClientSession,
    this.logout = logoutClient,
  });

  final Future<void> Function() initialChecks;
  final Future<ClientSession?> Function() restoreSession;
  final Future<void> Function(ClientSession session) logout;

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
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Restaurant Login',
      theme: ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFFF9F9F8),
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF8798AC)),
        fontFamily: 'sans-serif',
      ),
      home: SplashScreen(
        strings: AppStrings.fromLocale(_locale),
        initialChecks: widget.initialChecks,
        restoreSession: widget.restoreSession,
        logout: widget.logout,
      ),
    );
  }
}
