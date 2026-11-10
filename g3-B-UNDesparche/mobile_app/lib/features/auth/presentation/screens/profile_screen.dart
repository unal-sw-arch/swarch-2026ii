import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/theme/app_colors.dart';
import '../../../../core/theme/app_text_styles.dart';
import '../../../../core/theme/theme_controller.dart';
import '../../../../core/widgets/hero_card.dart';
import '../../../../core/widgets/hero_flat_button.dart';
import '../../../../core/widgets/hero_status_chip.dart';
import '../../../../injection_container.dart' as di;
import '../../domain/entities/user_entity.dart';
import '../bloc/auth_bloc.dart';
import '../bloc/auth_event.dart';

/// Pantalla de Perfil: datos del usuario y estado institucional (RN-03).
class ProfileScreen extends StatelessWidget {
  final UserEntity user;

  const ProfileScreen({super.key, required this.user});

  String _formatRole(UserRole role) {
    switch (role) {
      case UserRole.systemAdmin:
        return 'Administrador del Sistema';
      case UserRole.eventAdmin:
        return 'Administrador de Eventos';
      case UserRole.implementAdmin:
        return 'Administrador de Implementos';
      case UserRole.communityMember:
        return 'Comunidad UNAL';
      case UserRole.guest:
        return 'Visitante Externo';
    }
  }

  String _formatFaculty(String? faculty) {
    if (faculty == null || faculty.trim().isEmpty) return 'No especificada';
    final map = {
      'ING': 'Facultad de Ingeniería',
      'MED': 'Facultad de Medicina',
      'ART': 'Facultad de Artes',
      'CHS': 'Facultad de Ciencias Humanas',
      'CCS': 'Facultad de Ciencias',
      'ECO': 'Facultad de Ciencias Económicas',
      'AGR': 'Facultad de Ciencias Agrarias',
      'DER': 'Facultad de Derecho y Ciencias Políticas',
      'ENF': 'Facultad de Enfermería',
      'ODO': 'Facultad de Odontología',
      'VET': 'Facultad de Medicina Veterinaria y Zootecnia',
    };
    return map[faculty.toUpperCase()] ?? faculty;
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mi Perfil'),
        actions: [
          IconButton(
            icon: Icon(isDark ? Icons.light_mode_outlined : Icons.dark_mode_outlined),
            tooltip: isDark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro',
            onPressed: () =>
                unawaited(di.sl<ThemeController>().toggle(Theme.of(context).brightness)),
          ),
          IconButton(
            icon: const Icon(Icons.logout, color: AppColors.danger),
            tooltip: 'Cerrar sesión',
            onPressed: () =>
                context.read<AuthBloc>().add(const AuthSignOutRequested()),
          ),
        ],
      ),
      body: ListView(
        padding: EdgeInsets.fromLTRB(16, 16, 16, 16 + MediaQuery.paddingOf(context).bottom),
        children: [
          _UserCard(user: user, isDark: isDark),
          const SizedBox(height: 16),
          if (user.isSanctioned) _SanctionBanner(isDark: isDark),
          if (user.isGuest) _GuestBanner(isDark: isDark),
          const SizedBox(height: 16),

          // Información Institucional
          Text('Información Institucional', style: context.displaySmall),
          const SizedBox(height: 8),

          HeroCard(
            child: Column(
              children: [
                _InfoTile(
                  icon: Icons.badge_outlined,
                  label: 'Rol en la plataforma',
                  value: _formatRole(user.role),
                ),
                const Divider(height: 20),
                _InfoTile(
                  icon: Icons.account_balance_outlined,
                  label: 'Facultad',
                  value: _formatFaculty(user.faculty),
                ),
                const Divider(height: 20),
                _InfoTile(
                  icon: Icons.email_outlined,
                  label: 'Correo Institucional',
                  value: user.email,
                ),
                const Divider(height: 20),
                _InfoTile(
                  icon: Icons.verified_user_outlined,
                  label: 'Estado de la cuenta',
                  value: user.isSanctioned ? 'Sancionado (RN-03)' : (user.isGuest ? 'Visitante' : 'Activo'),
                  valueColor: user.isSanctioned
                      ? AppColors.danger
                      : (user.isGuest ? AppColors.occupied : AppColors.success),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          HeroFlatButton(
            text: 'Cerrar Sesión',
            icon: Icons.logout,
            destructive: true,
            onPressed: () =>
                context.read<AuthBloc>().add(const AuthSignOutRequested()),
          ),
        ],
      ),
    );
  }
}

/// Fila con icono, etiqueta y valor
class _InfoTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color? valueColor;

  const _InfoTile({
    required this.icon,
    required this.label,
    required this.value,
    this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Row(
      children: [
        Icon(icon, size: 22, color: AppColors.mintFocus),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: context.bodySmall),
              const SizedBox(height: 2),
              Text(
                value,
                style: context.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                  color: valueColor ?? (isDark ? AppColors.darkTextPrimary : AppColors.lightTextPrimary),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Tarjeta principal: avatar, nombre, correo y chips de estado.
class _UserCard extends StatelessWidget {
  final UserEntity user;
  final bool isDark;

  const _UserCard({required this.user, required this.isDark});

  @override
  Widget build(BuildContext context) {
    return HeroCard(
      child: Row(
        children: [
          CircleAvatar(
            radius: 36,
            backgroundColor: AppColors.mintPrimary.withValues(alpha: 0.15),
            foregroundImage: user.avatarUrl != null ? NetworkImage(user.avatarUrl!) : null,
            // Si la foto no carga (sin red, enlace roto), se queda la inicial.
            onForegroundImageError: user.avatarUrl != null ? (_, __) {} : null,
            child: Text(
              user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'U',
              style: context.displayLarge
                  .copyWith(color: isDark ? AppColors.mintLime : AppColors.mintInk),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(user.fullName, style: context.displaySmall),
                const SizedBox(height: 2),
                Text(user.email, style: context.bodySmall),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  children: [
                    if (user.isGuest)
                      const HeroStatusChip(label: 'VISITANTE', baseColor: AppColors.occupied)
                    else if (user.role == UserRole.systemAdmin)
                      const HeroStatusChip(label: 'ADMIN SISTEMA', baseColor: AppColors.mintPrimary)
                    else if (user.role == UserRole.eventAdmin)
                      const HeroStatusChip(label: 'ADMIN EVENTOS', baseColor: AppColors.mintPrimary)
                    else if (user.role == UserRole.implementAdmin)
                      const HeroStatusChip(label: 'ADMIN IMPLEMENTOS', baseColor: AppColors.mintPrimary)
                    else
                      const HeroStatusChip(label: 'COMUNIDAD UNAL', baseColor: AppColors.mintPrimary),
                    if (user.isSanctioned)
                      const HeroStatusChip(label: 'SANCIONADO (RN-03)', baseColor: AppColors.danger)
                    else if (!user.isGuest)
                      const HeroStatusChip(label: 'ACTIVO', baseColor: AppColors.success),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Banner de sanción (RN-03).
class _SanctionBanner extends StatelessWidget {
  final bool isDark;

  const _SanctionBanner({required this.isDark});

  @override
  Widget build(BuildContext context) {
    return HeroCard(
      backgroundColor: AppColors.danger.withValues(alpha: 0.08),
      child: Row(
        children: [
          const Icon(Icons.warning_amber_rounded, color: AppColors.danger, size: 32),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Cuenta Sancionada (Regla RN-03)',
                  style: context.displaySmall.copyWith(fontSize: 15, color: AppColors.danger),
                ),
                const SizedBox(height: 4),
                Text(
                  'No puedes realizar reservas de implementos debido a un implemento devuelto en estado no conforme. Acércate a la taquilla de bienestar de tu facultad.',
                  style: context.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Banner informativo para visitantes.
class _GuestBanner extends StatelessWidget {
  final bool isDark;

  const _GuestBanner({required this.isDark});

  @override
  Widget build(BuildContext context) {
    return HeroCard(
      backgroundColor: AppColors.mintPrimary.withValues(alpha: 0.06),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: AppColors.mintFocus, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Modo Visitante Activo', style: context.displaySmall.copyWith(fontSize: 15)),
                const SizedBox(height: 4),
                Text(
                  'Puedes consultar la agenda de eventos y recorrer el campus. Para reservar implementos deportivos necesitas ingresar con una cuenta institucional @unal.edu.co.',
                  style: context.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
