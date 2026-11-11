import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:go_router/go_router.dart';
import 'core/config/app_config.dart';
import 'core/services/notification_service.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_controller.dart';
import 'core/router/app_router.dart';
import 'features/auth/presentation/bloc/auth_bloc.dart';
import 'features/auth/presentation/bloc/auth_event.dart';
import 'firebase_options.dart';
import 'injection_container.dart' as di;

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  if (AppConfig.useFirebaseAuth) {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
  }
  await initializeDateFormatting('es', null);
  await di.initDependencies();
  await di.sl<ThemeController>().load();
  runApp(const UNDesparcheApp());
}

/// Aplicación principal configurada con enrutamiento declarativo [GoRouter]
class UNDesparcheApp extends StatefulWidget {
  const UNDesparcheApp({super.key});

  @override
  State<UNDesparcheApp> createState() => _UNDesparcheAppState();
}

class _UNDesparcheAppState extends State<UNDesparcheApp> {
  late final AuthBloc _authBloc;
  late final GoRouter _router;
  late final ThemeController _themeController;
  late final NotificationService _notificationService;

  @override
  void initState() {
    super.initState();
    _themeController = di.sl<ThemeController>();
    _notificationService = di.sl<NotificationService>();
    _authBloc = di.sl<AuthBloc>()..add(const AuthCheckRequested());
    _router = AppRouter.createRouter(_authBloc);
  }

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RepositoryProvider<NotificationService>.value(
      value: _notificationService,
      child: BlocProvider.value(
        value: _authBloc,
        child: ValueListenableBuilder<ThemeMode>(
          valueListenable: _themeController,
          builder: (context, themeMode, _) => MaterialApp.router(
            title: 'UNDesparche Móvil',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: themeMode,
            routerConfig: _router,
          ),
        ),
      ),
    );
  }
}

