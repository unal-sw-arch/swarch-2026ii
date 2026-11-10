import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/widgets/brand_header.dart';
import '../../../../core/widgets/fade_up_in.dart';
import '../../../../core/widgets/hero_card.dart';
import '../../../../core/widgets/hero_flat_button.dart';
import '../../../../core/widgets/hero_solid_button.dart';
import '../bloc/auth_bloc.dart';
import '../bloc/auth_event.dart';
import '../bloc/auth_state.dart';

/// Pantalla de Bienvenida con ingreso real por Google (RN-01) o modo Visitante.
class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final secondary =
        isDark ? AppColors.darkTextSecondary : AppColors.lightTextSecondary;

    return Scaffold(
      body: BlocConsumer<AuthBloc, AuthState>(
        listener: (context, state) {
          if (state is AuthError) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(state.message),
                backgroundColor: AppColors.danger,
              ),
            );
          }
        },
        builder: (context, state) {
          final isLoading = state is AuthLoading;
          return Stack(
            children: [
              // Brillo menta arriba, como el fondo de la web
              Positioned(
                top: -120,
                left: -80,
                right: -80,
                height: 460,
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: RadialGradient(
                        colors: [
                          AppColors.mintPrimary
                              .withValues(alpha: isDark ? 0.22 : 0.18),
                          AppColors.mintPrimary.withValues(alpha: 0.0),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              SafeArea(
                child: LayoutBuilder(
                  builder: (context, constraints) => SingleChildScrollView(
                    child: ConstrainedBox(
                      constraints:
                          BoxConstraints(minHeight: constraints.maxHeight),
                      child: IntrinsicHeight(
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: AppSpacing.xl,
                              vertical: AppSpacing.xl),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Spacer(),
                              FadeUpIn(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const BrandMark(size: 64),
                                    const SizedBox(height: AppSpacing.xl),
                                    Text(
                                      'UNDesparche',
                                      style: theme.textTheme.displaySmall
                                          ?.copyWith(
                                        fontSize: 38,
                                        fontWeight: FontWeight.w800,
                                        letterSpacing: -0.8,
                                      ),
                                    ),
                                    const SizedBox(height: AppSpacing.sm),
                                    Text(
                                      'El pulso deportivo, recreativo y cultural de la Universidad Nacional de Colombia (Sede Bogotá).',
                                      style: TextStyle(
                                          fontSize: 16,
                                          height: 1.5,
                                          color: secondary),
                                    ),
                                  ],
                                ),
                              ),
                              const Spacer(),
                              FadeUpIn(
                                index: 2,
                                child: HeroCard(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      Text('Selecciona tu modo de ingreso',
                                          style: theme.textTheme.titleLarge
                                              ?.copyWith(fontSize: 18)),
                                      const SizedBox(height: AppSpacing.sm),
                                      Text(
                                        'Los estudiantes UNAL ingresan con su cuenta @unal.edu.co para solicitar implementos y participar en torneos. Los visitantes disfrutan de la agenda cultural y deportiva abierta.',
                                        style: TextStyle(
                                            fontSize: 12.5,
                                            height: 1.45,
                                            color: secondary),
                                      ),
                                      const SizedBox(height: AppSpacing.xl),
                                      HeroSolidButton(
                                        text: isLoading
                                            ? 'Ingresando...'
                                            : 'Ingresar con Google UNAL',
                                        icon: Icons.school,
                                        onPressed: isLoading
                                            ? null
                                            : () => context.read<AuthBloc>().add(
                                                const AuthGoogleSignInRequested()),
                                      ),
                                      const SizedBox(height: AppSpacing.md),
                                      HeroFlatButton(
                                        text: 'Explorar como Visitante',
                                        icon: Icons.travel_explore,
                                        onPressed: isLoading
                                            ? null
                                            : () => context.read<AuthBloc>().add(
                                                const AuthGuestSignInRequested()),
                                      ),
                                      const SizedBox(height: AppSpacing.md),
                                      Center(
                                        child: Text(
                                          'Solo se permite el acceso a cuentas @unal.edu.co (RN-01).',
                                          textAlign: TextAlign.center,
                                          style: TextStyle(
                                              fontSize: 11, color: secondary),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: AppSpacing.lg),
                              Center(
                                child: Text(
                                  'Campus Ciudad Universitaria · Bogotá D.C.',
                                  style:
                                      TextStyle(fontSize: 11, color: secondary),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
