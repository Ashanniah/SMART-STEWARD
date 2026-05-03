import {
  browserLocalPersistence,
  browserSessionPersistence,
  setPersistence,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { AGENCY_ALLOWED_ROLES, USERS_COLLECTION } from '../constants/agencyAuth';
import { getFirebaseAuth, getFirestoreDb, isFirebaseConfigured } from '../firebase/config';

export const LOGIN_MSG = {
  success: 'Login successful.',
  notConfigured:
    'Sign-in is not configured. Add your Firebase keys to the environment (see .env.example).',
  notAuthorized: 'This account is not authorized for agency access.',
  notVerified: 'Please verify your email before logging in.',
  disabledAccount: 'This account has been disabled.',
  invalidCredentials: 'Invalid email or password.',
  userNotFound: 'No account found with this email.',
  wrongPassword: 'Incorrect password. Please try again.',
  tooManyAttempts: 'Too many login attempts. Please try again later.',
  verifyAccessFailed: 'Unable to verify account access. Please try again.',
};

function mapAuthError(code) {
  switch (code) {
    case 'auth/user-not-found':
      return LOGIN_MSG.userNotFound;
    case 'auth/wrong-password':
      return LOGIN_MSG.wrongPassword;
    case 'auth/invalid-email':
      return 'Please enter a valid email address.';
    case 'auth/invalid-credential':
      return LOGIN_MSG.invalidCredentials;
    case 'auth/user-disabled':
      return LOGIN_MSG.disabledAccount;
    case 'auth/too-many-requests':
      return LOGIN_MSG.tooManyAttempts;
    case 'auth/network-request-failed':
      return 'Network error. Check your connection and try again.';
    default:
      return LOGIN_MSG.invalidCredentials;
  }
}

function isAgencyRole(roleValue) {
  const r = String(roleValue ?? '')
    .trim()
    .toLowerCase();
  return AGENCY_ALLOWED_ROLES.includes(r);
}

/**
 * @returns {Promise<{ ok: true } | { ok: false, message: string }>}
 */
export async function performAgencyLogin({ email, password, rememberMe }) {
  if (!isFirebaseConfigured) {
    return { ok: false, message: LOGIN_MSG.notConfigured };
  }

  const auth = getFirebaseAuth();
  const db = getFirestoreDb();
  if (!auth || !db) {
    return { ok: false, message: LOGIN_MSG.notConfigured };
  }

  try {
    await setPersistence(
      auth,
      rememberMe ? browserLocalPersistence : browserSessionPersistence
    );

    const cred = await signInWithEmailAndPassword(auth, email, password);
    const user = cred.user;

    let snap;
    try {
      snap = await getDoc(doc(db, USERS_COLLECTION, user.uid));
    } catch {
      await signOut(auth);
      return { ok: false, message: LOGIN_MSG.verifyAccessFailed };
    }

    if (!snap.exists()) {
      await signOut(auth);
      return { ok: false, message: LOGIN_MSG.notAuthorized };
    }

    const data = snap.data();

    if (data.disabled === true) {
      await signOut(auth);
      return { ok: false, message: LOGIN_MSG.disabledAccount };
    }

    if (!isAgencyRole(data.role)) {
      await signOut(auth);
      return { ok: false, message: LOGIN_MSG.notAuthorized };
    }

    return { ok: true };
  } catch (err) {
    const code = err?.code ?? '';
    const auth = getFirebaseAuth();
    if (auth?.currentUser) {
      try {
        await signOut(auth);
      } catch {
        /* ignore */
      }
    }
    return { ok: false, message: mapAuthError(code) };
  }
}
