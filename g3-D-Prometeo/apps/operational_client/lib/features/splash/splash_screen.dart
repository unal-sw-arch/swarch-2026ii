import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

import '../../services/client_auth_api.dart';
import '../../services/cloud_api.dart';
import '../../models/client_user.dart';
import '../auth/login_page.dart';
import '../rooms/rooms_page.dart';

/// Punto único para las comprobaciones que deben terminar antes del login.
/// Las verificaciones reales pueden añadirse a esta función sin cambiar la UI.
Future<void> runInitialChecks() async {
  await Future.wait([
    verifyCloudApiIsRunning(),
    // Mantiene visible la animación el tiempo suficiente para evitar un destello.
    Future<void>.delayed(const Duration(milliseconds: 1400)),
  ]);
}

typedef ClientRoomsBuilder =
    Widget Function(
      ClientSession session,
      bool spanish,
      Future<void> Function() onLogout,
    );

class SplashScreen extends StatefulWidget {
  const SplashScreen({
    super.key,
    required this.strings,
    this.initialChecks = runInitialChecks,
    this.restoreSession = restoreStoredClientSession,
    this.logout = logoutClient,
    this.roomsBuilder,
  });

  final AppStrings strings;
  final Future<void> Function() initialChecks;
  final Future<ClientSession?> Function() restoreSession;
  final Future<void> Function(ClientSession session) logout;
  final ClientRoomsBuilder? roomsBuilder;

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulseController;
  late final Animation<double> _pulse;
  _StartupView _view = _StartupView.splash;
  ClientSession? _authenticatedSession;
  bool _loginIsFading = false;
  final _roomsKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1050),
    )..repeat(reverse: true);
    _pulse = Tween<double>(begin: .78, end: 1.16).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOutCubic),
    );
    _initialize();
  }

  Future<void> _initialize() async {
    try {
      await widget.initialChecks();
      if (!mounted) return;
      _authenticatedSession = await widget.restoreSession();
    } on Object catch (error, stackTrace) {
      debugPrint('Las comprobaciones iniciales fallaron: $error');
      debugPrintStack(stackTrace: stackTrace);
      if (!mounted) return;
      _pulseController.stop();
      setState(() => _view = _StartupView.unavailable);
      return;
    }
    if (!mounted) return;
    _pulseController.stop();
    setState(
      () => _view = _authenticatedSession == null
          ? _StartupView.login
          : _StartupView.rooms,
    );
  }

  void _retry() {
    setState(() => _view = _StartupView.splash);
    _pulseController.repeat(reverse: true);
    _initialize();
  }

  Future<void> _logout() async {
    final session = _authenticatedSession;
    if (session == null) return;
    await widget.logout(session);
    if (!mounted) return;
    setState(() {
      _authenticatedSession = null;
      _loginIsFading = false;
      _view = _StartupView.login;
    });
  }

  Widget _rooms() =>
      widget.roomsBuilder?.call(
        _authenticatedSession!,
        widget.strings.isSpanish,
        _logout,
      ) ??
      RoomsPage(
        key: _roomsKey,
        session: _authenticatedSession!,
        spanish: widget.strings.isSpanish,
        onLogout: _logout,
      );

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 560),
      reverseDuration: const Duration(milliseconds: 560),
      switchInCurve: Curves.easeInOut,
      switchOutCurve: Curves.easeInOut,
      // La pantalla nueva permanece completamente visible debajo mientras la
      // anterior se desvanece. Así nunca aparece el fondo negro del compositor.
      layoutBuilder: (currentChild, previousChildren) => Stack(
        fit: StackFit.expand,
        children: [?currentChild, ...previousChildren],
      ),
      transitionBuilder: (child, animation) =>
          _FadeOutTransition(animation: animation, child: child),
      child: _view == _StartupView.rooms
          ? _rooms()
          : _view == _StartupView.login
          ? Stack(
              key: const ValueKey('login-stage'),
              fit: StackFit.expand,
              children: [
                if (_authenticatedSession != null) _rooms(),
                AnimatedOpacity(
                  opacity: _loginIsFading ? 0 : 1,
                  duration: const Duration(milliseconds: 560),
                  curve: Curves.easeInOut,
                  onEnd: () {
                    if (_loginIsFading && mounted) {
                      setState(() => _view = _StartupView.rooms);
                    }
                  },
                  child: IgnorePointer(
                    ignoring: _loginIsFading,
                    child: LoginPage(
                      key: const ValueKey('login-screen'),
                      strings: widget.strings,
                      onAuthenticated: (session) => setState(() {
                        _authenticatedSession = session;
                        _loginIsFading = true;
                      }),
                    ),
                  ),
                ),
              ],
            )
          : _view == _StartupView.unavailable
          ? _CloudUnavailable(
              key: const ValueKey('cloud-unavailable'),
              spanish: widget.strings.isSpanish,
              onRetry: _retry,
            )
          : Scaffold(
              key: const ValueKey('splash-screen'),
              body: Center(
                child: AnimatedBuilder(
                  animation: _pulse,
                  builder: (context, child) => Transform.scale(
                    scale: _pulse.value,
                    child: Opacity(
                      opacity: .68 + (((_pulse.value - .78) / .38) * .32),
                      child: child,
                    ),
                  ),
                  child: SizedBox.square(
                    dimension: 150,
                    child: SvgPicture.asset(
                      'assets/logo.svg',
                      key: const ValueKey('splash-logo'),
                      fit: BoxFit.contain,
                    ),
                  ),
                ),
              ),
            ),
    );
  }
}

enum _StartupView { splash, unavailable, login, rooms }

class _CloudUnavailable extends StatelessWidget {
  const _CloudUnavailable({
    super.key,
    required this.spanish,
    required this.onRetry,
  });

  final bool spanish;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.cloud_off_rounded,
              size: 64,
              color: Color(0xFF8798AC),
            ),
            const SizedBox(height: 18),
            Text(
              spanish ? 'Servidor no disponible' : 'Server unavailable',
              style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 8),
            Text(
              spanish
                  ? 'No se pudo conectar con el servicio en la nube.'
                  : 'The cloud service could not be reached.',
              textAlign: TextAlign.center,
              style: const TextStyle(color: Color(0xFF6D7075)),
            ),
            const SizedBox(height: 24),
            FilledButton.icon(
              key: const ValueKey('retry-cloud-check'),
              onPressed: onRetry,
              icon: const Icon(Icons.refresh_rounded),
              label: Text(spanish ? 'Reintentar' : 'Retry'),
            ),
          ],
        ),
      ),
    ),
  );
}

class _FadeOutTransition extends StatelessWidget {
  const _FadeOutTransition({required this.animation, required this.child});

  final Animation<double> animation;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: animation,
      child: child,
      builder: (context, child) {
        if (animation.status != AnimationStatus.reverse) return child!;
        return Opacity(opacity: animation.value, child: child);
      },
    );
  }
}
