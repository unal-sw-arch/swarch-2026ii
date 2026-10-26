import 'dart:async';

import 'package:flutter/material.dart';
import 'package:motor/motor.dart';

import '../../models/client_user.dart';
import '../../services/client_auth_api.dart';

const _snappyMotion = CupertinoMotion(
  duration: Duration(milliseconds: 260),
  bounce: -.08,
);
const _bouncyMotion = CupertinoMotion.bouncy(
  duration: Duration(milliseconds: 520),
);

class AppStrings {
  const AppStrings({
    required this.welcome,
    required this.username,
    required this.password,
    required this.signIn,
    required this.showPassword,
    required this.hidePassword,
    required this.isSpanish,
  });

  factory AppStrings.fromLocale(Locale locale) {
    if (locale.languageCode.toLowerCase() == 'es') {
      return const AppStrings(
        welcome: '¡Bienvenido!',
        username: 'Usuario',
        password: 'Contraseña',
        signIn: 'Ingresar',
        showPassword: 'Mostrar contraseña',
        hidePassword: 'Ocultar contraseña',
        isSpanish: true,
      );
    }
    return const AppStrings(
      welcome: 'Welcome!',
      username: 'Username',
      password: 'Password',
      signIn: 'Sign In',
      showPassword: 'Show password',
      hidePassword: 'Hide password',
      isSpanish: false,
    );
  }

  final String welcome;
  final String username;
  final String password;
  final String signIn;
  final String showPassword;
  final String hidePassword;
  final bool isSpanish;
}

typedef ClientAuthenticator =
    Future<ClientSession> Function(String username, String password);

class LoginPage extends StatefulWidget {
  const LoginPage({
    super.key,
    required this.strings,
    this.authenticate = loginClient,
    this.onAuthenticated,
  });

  final AppStrings strings;
  final ClientAuthenticator authenticate;
  final ValueChanged<ClientSession>? onAuthenticated;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  late final Timer _welcomeDelay;
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _passwordVisible = false;
  bool _isSubmitting = false;
  String? _error;

  Future<void> _submit() async {
    if (_isSubmitting) return;
    FocusScope.of(context).unfocus();
    if (_usernameController.text.trim().isEmpty ||
        _passwordController.text.isEmpty) {
      setState(
        () => _error = widget.strings.isSpanish
            ? 'Completa el usuario y la contraseña.'
            : 'Enter your username and password.',
      );
      return;
    }
    setState(() {
      _isSubmitting = true;
      _error = null;
    });
    var transitionStarted = false;
    try {
      final user = await widget.authenticate(
        _usernameController.text,
        _passwordController.text,
      );
      if (mounted && widget.onAuthenticated != null) {
        // El estado de carga y los controladores se conservan mientras el
        // padre desvanece esta misma instancia de LoginPage.
        transitionStarted = true;
        widget.onAuthenticated!(user);
      }
    } on Object {
      if (mounted) {
        setState(
          () => _error = widget.strings.isSpanish
              ? 'Credenciales incorrectas o servidor no disponible.'
              : 'Incorrect credentials or server unavailable.',
        );
      }
    } finally {
      if (mounted && !transitionStarted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  void initState() {
    super.initState();
    _welcomeDelay = Timer(const Duration(milliseconds: 180), () {
      if (mounted) setState(() => _welcomeVisible = true);
    });
  }

  bool _welcomeVisible = false;

  @override
  void dispose() {
    _welcomeDelay.cancel();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Container(
                padding: const EdgeInsets.fromLTRB(24, 26, 24, 48),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(15),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x0B000000),
                      blurRadius: 24,
                      offset: Offset(0, 10),
                    ),
                  ],
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SingleMotionBuilder(
                      value: _welcomeVisible ? 1 : .72,
                      from: .72,
                      motion: _bouncyMotion,
                      builder: (context, value, child) =>
                          Transform.scale(scale: value, child: child),
                      child: Text(
                        widget.strings.welcome,
                        style: const TextStyle(
                          color: Color(0xFF202020),
                          fontSize: 28,
                          height: 1.15,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -1.2,
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                    AnimatedLoginField(
                      key: const ValueKey('username-field'),
                      controller: _usernameController,
                      label: widget.strings.username,
                      prefixIcon: Icons.badge_outlined,
                      textInputAction: TextInputAction.next,
                    ),
                    const SizedBox(height: 16),
                    AnimatedLoginField(
                      key: const ValueKey('password-field'),
                      controller: _passwordController,
                      label: widget.strings.password,
                      prefixIcon: Icons.dialpad_rounded,
                      obscureText: !_passwordVisible,
                      textInputAction: TextInputAction.done,
                      suffix: AnimatedEyeButton(
                        isVisible: _passwordVisible,
                        showLabel: widget.strings.showPassword,
                        hideLabel: widget.strings.hidePassword,
                        onPressed: () => setState(
                          () => _passwordVisible = !_passwordVisible,
                        ),
                      ),
                    ),
                    const SizedBox(height: 25),
                    SizedBox(
                      width: double.infinity,
                      height: 56,
                      child: FilledButton(
                        onPressed: _isSubmitting ? null : _submit,
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF8798AC),
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 15,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        child: _isSubmitting
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(widget.strings.signIn),
                                  const SizedBox(width: 10),
                                  const Icon(Icons.arrow_forward, size: 19),
                                ],
                              ),
                      ),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 14),
                      Text(
                        _error!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Color(0xFFC94E4E)),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class AnimatedLoginField extends StatefulWidget {
  const AnimatedLoginField({
    super.key,
    required this.controller,
    required this.label,
    required this.prefixIcon,
    this.obscureText = false,
    this.textInputAction,
    this.suffix,
  });

  final TextEditingController controller;
  final String label;
  final IconData prefixIcon;
  final bool obscureText;
  final TextInputAction? textInputAction;
  final Widget? suffix;

  @override
  State<AnimatedLoginField> createState() => _AnimatedLoginFieldState();
}

class _AnimatedLoginFieldState extends State<AnimatedLoginField> {
  late final FocusNode _focusNode;

  bool get _isFloating =>
      _focusNode.hasFocus || widget.controller.text.isNotEmpty;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode()..addListener(_refresh);
    widget.controller.addListener(_refresh);
  }

  void _refresh() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _focusNode
      ..removeListener(_refresh)
      ..dispose();
    widget.controller.removeListener(_refresh);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const duration = Duration(milliseconds: 230);
    const textColor = Color(0xFF4D5055);

    return AnimatedContainer(
      duration: duration,
      curve: Curves.easeOutCubic,
      height: 64,
      decoration: BoxDecoration(
        color: const Color(0xFFECECEB),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: _focusNode.hasFocus
              ? const Color(0xFF8798AC)
              : Colors.transparent,
          width: 1.4,
        ),
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          Positioned(
            left: 17,
            top: 0,
            bottom: 0,
            child: Icon(
              widget.prefixIcon,
              color: const Color(0xFF999A98),
              size: 23,
            ),
          ),
          TextField(
            controller: widget.controller,
            focusNode: _focusNode,
            obscureText: widget.obscureText,
            obscuringCharacter: '•',
            textInputAction: widget.textInputAction,
            style: const TextStyle(
              color: textColor,
              fontSize: 18,
              height: 1.15,
            ),
            cursorColor: const Color(0xFF8798AC),
            decoration: InputDecoration(
              border: InputBorder.none,
              isCollapsed: true,
              contentPadding: EdgeInsets.fromLTRB(
                64,
                _isFloating ? 27 : 21,
                widget.suffix == null ? 17 : 50,
                10,
              ),
            ),
          ),
          Positioned(
            left: 64,
            top: 20,
            child: IgnorePointer(
              child: RepaintBoundary(
                child: SingleMotionBuilder(
                  value: _isFloating ? 1 : 0,
                  motion: _snappyMotion,
                  builder: (context, value, child) => Transform.translate(
                    offset: Offset(0, -13 * value),
                    child: Transform.scale(
                      alignment: Alignment.topLeft,
                      scale: 1 - (.36 * value),
                      child: DefaultTextStyle(
                        style: TextStyle(
                          color: Color.lerp(
                            textColor,
                            const Color(0xFF74869A),
                            value.clamp(0, 1),
                          ),
                          fontSize: 18,
                          height: 1.15,
                          fontWeight: FontWeight.w500,
                        ),
                        child: child!,
                      ),
                    ),
                  ),
                  child: Text(widget.label),
                ),
              ),
            ),
          ),
          if (widget.suffix != null)
            Positioned(right: 7, child: widget.suffix!),
        ],
      ),
    );
  }
}

class AnimatedEyeButton extends StatelessWidget {
  const AnimatedEyeButton({
    super.key,
    required this.isVisible,
    required this.onPressed,
    required this.showLabel,
    required this.hideLabel,
  });

  final bool isVisible;
  final VoidCallback onPressed;
  final String showLabel;
  final String hideLabel;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: isVisible ? hideLabel : showLabel,
      child: IconButton(
        onPressed: onPressed,
        splashRadius: 20,
        icon: SizedBox(
          width: 24,
          height: 24,
          child: Stack(
            alignment: Alignment.center,
            children: [
              const Icon(
                Icons.visibility_outlined,
                color: Color(0xFF999A98),
                size: 23,
              ),
              SingleMotionBuilder(
                value: isVisible ? 0 : 1,
                motion: _snappyMotion,
                builder: (context, value, child) => Opacity(
                  opacity: value.clamp(0, 1),
                  child: Transform.scale(
                    scale: .55 + (.45 * value),
                    child: child,
                  ),
                ),
                child: Transform.rotate(
                  angle: -.76,
                  child: Container(
                    width: 25,
                    height: 2.2,
                    decoration: BoxDecoration(
                      color: const Color(0xFF999A98),
                      borderRadius: BorderRadius.circular(2),
                      border: Border.all(
                        color: const Color(0xFFECECEB),
                        width: .3,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
