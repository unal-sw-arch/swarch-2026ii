import 'package:flutter/material.dart';

import '../../services/admin_api.dart';
import 'login_page.dart';

typedef RegisterAdminCallback =
    Future<RegisteredAdmin> Function({
      required String fullName,
      required String username,
      required String password,
    });

class RegistrationPage extends StatefulWidget {
  const RegistrationPage({
    super.key,
    required this.strings,
    required this.onSignIn,
    this.onRegisterAdmin = registerAdmin,
  });

  final AppStrings strings;
  final VoidCallback onSignIn;
  final RegisterAdminCallback onRegisterAdmin;

  @override
  State<RegistrationPage> createState() => _RegistrationPageState();
}

class _RegistrationPageState extends State<RegistrationPage> {
  final _fullNameController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _passwordVisible = false;
  bool _isSubmitting = false;
  String? _errorMessage;

  Future<void> _submit() async {
    if (_isSubmitting) return;
    final fullName = _fullNameController.text.trim();
    final username = _usernameController.text.trim();
    final password = _passwordController.text;

    if (fullName.isEmpty || username.isEmpty || password.isEmpty) {
      setState(() => _errorMessage = widget.strings.fillAllFields);
      return;
    }
    if (password.length < 12) {
      setState(() => _errorMessage = widget.strings.passwordTooShort);
      return;
    }

    FocusScope.of(context).unfocus();
    setState(() {
      _isSubmitting = true;
      _errorMessage = null;
    });
    try {
      await widget.onRegisterAdmin(
        fullName: fullName,
        username: username,
        password: password,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(widget.strings.registrationSuccess)),
      );
      widget.onSignIn();
    } on AdminRegistrationException catch (error) {
      if (!mounted) return;
      setState(() {
        _errorMessage = error.code == 'USERNAME_TAKEN'
            ? widget.strings.usernameTaken
            : widget.strings.registrationError;
      });
    } on Object {
      if (!mounted) return;
      setState(() => _errorMessage = widget.strings.registrationError);
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  @override
  void dispose() {
    _fullNameController.dispose();
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
                padding: const EdgeInsets.fromLTRB(24, 26, 24, 34),
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
                    Text(
                      widget.strings.registrationTitle,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Color(0xFF202020),
                        fontSize: 28,
                        height: 1.15,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -1.2,
                      ),
                    ),
                    const SizedBox(height: 9),
                    Text(
                      widget.strings.registrationSubtitle,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: Color(0xFF4D5055),
                        fontSize: 14,
                      ),
                    ),
                    const SizedBox(height: 28),
                    AnimatedLoginField(
                      key: const ValueKey('full-name-field'),
                      controller: _fullNameController,
                      label: widget.strings.fullName,
                      prefixIcon: Icons.person_outline,
                      textInputAction: TextInputAction.next,
                    ),
                    const SizedBox(height: 16),
                    AnimatedLoginField(
                      key: const ValueKey('register-username-field'),
                      controller: _usernameController,
                      label: widget.strings.username,
                      prefixIcon: Icons.badge_outlined,
                      textInputAction: TextInputAction.next,
                    ),
                    const SizedBox(height: 16),
                    AnimatedLoginField(
                      key: const ValueKey('register-password-field'),
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
                        key: const ValueKey('create-account'),
                        onPressed: _isSubmitting ? null : _submit,
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF8798AC),
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          textStyle: const TextStyle(
                            fontSize: 15,
                            letterSpacing: 1.1,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        child: _isSubmitting
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2.2,
                                  color: Colors.white,
                                ),
                              )
                            : Text(widget.strings.createAccount),
                      ),
                    ),
                    if (_errorMessage != null) ...[
                      const SizedBox(height: 12),
                      Text(
                        _errorMessage!,
                        key: const ValueKey('registration-error'),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Color(0xFFB23A3A),
                          fontSize: 13,
                        ),
                      ),
                    ],
                    const SizedBox(height: 22),
                    Wrap(
                      alignment: WrapAlignment.center,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        Text(
                          widget.strings.alreadyHaveAccount,
                          style: const TextStyle(
                            color: Color(0xFF778395),
                            fontSize: 14,
                          ),
                        ),
                        TextButton(
                          key: const ValueKey('return-to-login'),
                          onPressed: widget.onSignIn,
                          child: Text(widget.strings.signIn),
                        ),
                      ],
                    ),
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
