import 'package:get_it/get_it.dart';
import 'core/config/app_config.dart';
import 'core/network/dio_client.dart';
import 'core/services/notification_service.dart';
import 'core/services/token_storage.dart';
import 'core/theme/theme_controller.dart';

// Eventos
import 'features/events/domain/repositories/events_repository.dart';
import 'features/events/data/repositories/mock_events_repository.dart';
import 'features/events/data/repositories/http_events_repository.dart';
import 'features/events/presentation/bloc/events_bloc.dart';

// Inventario
import 'features/inventory/domain/repositories/inventory_repository.dart';
import 'features/inventory/data/repositories/mock_inventory_repository.dart';
import 'features/inventory/data/repositories/http_inventory_repository.dart';
import 'features/inventory/presentation/bloc/inventory_bloc.dart';

// Autenticacion y Usuarios
import 'features/auth/data/datasources/firebase_auth_datasource.dart';
import 'features/auth/domain/repositories/users_repository.dart';
import 'features/auth/data/repositories/mock_users_repository.dart';
import 'features/auth/data/repositories/http_users_repository.dart';
import 'features/auth/data/repositories/firebase_users_repository.dart';
import 'features/auth/presentation/bloc/auth_bloc.dart';

final sl = GetIt.instance;

/// Inicializa el contenedor de inyeccion de dependencias.
/// Respeta el patron de Inversion de Dependencias: Si [AppConfig.useMockData] es verdadero,
/// inyecta las implementaciones mockeadas. Al cambiar a falso, inyecta los clientes HTTP remotos.
///
/// [enableFirebaseAuth] permite a los tests desactivar Firebase (que requiere
/// `Firebase.initializeApp`) y usar el repo mock de usuarios.
Future<void> initDependencies({bool? enableFirebaseAuth}) async {
  final useFirebaseAuth = enableFirebaseAuth ?? AppConfig.useFirebaseAuth;

  // --- Servicios Globales & Red ---
  if (!sl.isRegistered<DioClient>()) {
    sl.registerLazySingleton<DioClient>(() => DioClient());
  }
  if (!sl.isRegistered<NotificationService>()) {
    sl.registerLazySingleton<NotificationService>(() => NotificationService());
  }

  if (!sl.isRegistered<ThemeController>()) {
    sl.registerLazySingleton<ThemeController>(() => ThemeController());
  }

  if (!sl.isRegistered<TokenStorage>()) {
    sl.registerLazySingleton<TokenStorage>(() => TokenStorage());
  }

  if (useFirebaseAuth && !sl.isRegistered<FirebaseAuthDataSource>()) {
    sl.registerLazySingleton<FirebaseAuthDataSource>(
        () => FirebaseAuthDataSource());
  }

  // --- BLoCs ---
  if (!sl.isRegistered<AuthBloc>()) {
    sl.registerFactory(() => AuthBloc(
          usersRepository: sl(),
          // Al cerrar sesión, cierra el stream de reservas y re-registra el
          // repo para que un nuevo login tenga un stream fresco (#11).
          onSignOut: () async {
            if (sl.isRegistered<InventoryRepository>()) {
              await sl<InventoryRepository>().dispose();
              await sl.unregister<InventoryRepository>();
            }
            if (AppConfig.useMockData) {
              sl.registerLazySingleton<InventoryRepository>(
                  () => MockInventoryRepository());
            } else {
              sl.registerLazySingleton<InventoryRepository>(
                  () => HttpInventoryRepository(client: sl()));
            }
          },
        ));
  }
  if (!sl.isRegistered<EventsBloc>()) {
    sl.registerFactory(() => EventsBloc(eventsRepository: sl()));
  }
  if (!sl.isRegistered<InventoryBloc>()) {
    sl.registerFactory(() => InventoryBloc(inventoryRepository: sl()));
  }

  if (AppConfig.useMockData) {
    // --- MODO MOCKEADO DE DATOS (Actual) ---
    if (!sl.isRegistered<EventsRepository>()) {
      sl.registerLazySingleton<EventsRepository>(() => MockEventsRepository());
    }
    if (!sl.isRegistered<InventoryRepository>()) {
      sl.registerLazySingleton<InventoryRepository>(() => MockInventoryRepository());
    }
  } else {
    // --- MODO REMOTO DE DATOS (Futuro API Gateway) ---
    if (!sl.isRegistered<EventsRepository>()) {
      sl.registerLazySingleton<EventsRepository>(() => HttpEventsRepository(client: sl()));
    }
    if (!sl.isRegistered<InventoryRepository>()) {
      sl.registerLazySingleton<InventoryRepository>(() => HttpInventoryRepository(client: sl()));
    }
  }

  // --- AUTENTICACIÓN (independiente del gateway de datos) ---
  if (!sl.isRegistered<UsersRepository>()) {
    if (useFirebaseAuth) {
      sl.registerLazySingleton<UsersRepository>(() => FirebaseUsersRepository(
            authDataSource: sl<FirebaseAuthDataSource>(),
            tokenStorage: sl<TokenStorage>(),
            client: sl<DioClient>(),
          ));
      // El interceptor renueva el token de Firebase en un 401 y, si persiste,
      // marca la sesión como expirada (#5).
      final dioClient = sl<DioClient>();
      final auth = sl<FirebaseAuthDataSource>();
      dioClient.authInterceptor.onRefreshToken =
          () => auth.getIdToken(forceRefresh: true);
      dioClient.authInterceptor.onSessionExpired = () async {
        await sl<UsersRepository>().signOut();
      };
    } else if (AppConfig.useMockData) {
      sl.registerLazySingleton<UsersRepository>(() => MockUsersRepository());
    } else {
      sl.registerLazySingleton<UsersRepository>(() => HttpUsersRepository(client: sl()));
    }
  }
}

