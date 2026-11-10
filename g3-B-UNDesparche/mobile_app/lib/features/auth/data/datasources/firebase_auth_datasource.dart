import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_sign_in/google_sign_in.dart';

/// Fuente de datos de autenticación sobre Firebase Auth + Google Sign-In.
/// Equivale a `config/firebase.ts` + el flujo de `AuthContext.tsx` de la web:
/// inicia sesión con Google restringido al dominio UNAL, expone el usuario
/// actual, su token y los cambios de sesión.
class FirebaseAuthDataSource {
  final FirebaseAuth _auth;
  final GoogleSignIn _googleSignIn;

  FirebaseAuthDataSource({
    FirebaseAuth? auth,
    GoogleSignIn? googleSignIn,
  })  : _auth = auth ?? FirebaseAuth.instance,
        _googleSignIn = googleSignIn ??
            GoogleSignIn(
              // Sugiere cuentas del dominio UNAL (como hd en la web). La
              // validación estricta RN-01 se aplica luego sobre el email.
              hostedDomain: 'unal.edu.co',
            );

  /// Usuario de Firebase actualmente autenticado (o null).
  User? get currentUser => _auth.currentUser;

  /// Cambios de sesión: emite el usuario al entrar y null al salir.
  Stream<User?> authStateChanges() => _auth.authStateChanges();

  /// Inicia sesión con Google y devuelve las credenciales de Firebase.
  /// Lanza [FirebaseAuthException] o [GoogleSignInCancelled] si falla/cancela.
  Future<UserCredential> signInWithGoogle() async {
    final googleUser = await _googleSignIn.signIn();
    if (googleUser == null) {
      // El usuario cerró el diálogo de Google.
      throw const GoogleSignInCancelled();
    }

    final googleAuth = await googleUser.authentication;
    final credential = GoogleAuthProvider.credential(
      accessToken: googleAuth.accessToken,
      idToken: googleAuth.idToken,
    );

    return _auth.signInWithCredential(credential);
  }

  /// Token JWT de Firebase del usuario actual. [forceRefresh] lo renueva.
  Future<String?> getIdToken({bool forceRefresh = false}) async {
    final user = _auth.currentUser;
    if (user == null) return null;
    return user.getIdToken(forceRefresh);
  }

  /// Cierra sesión en Firebase y en Google.
  Future<void> signOut() async {
    await _googleSignIn.signOut();
    await _auth.signOut();
  }
}

/// El usuario canceló el diálogo de Google Sign-In.
class GoogleSignInCancelled implements Exception {
  const GoogleSignInCancelled();
}
