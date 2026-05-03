const EMAIL_RE =
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmailFormat(email) {
  return EMAIL_RE.test(String(email).trim());
}

/**
 * @returns {{ ok: true, email: string } | { ok: false, kind: 'form'|'email'|'password', message: string }}
 */
export function validateLoginForm(rawEmail, rawPassword) {
  const email = String(rawEmail ?? '').trim();
  const password = String(rawPassword ?? '');

  if (!email && !password) {
    return { ok: false, kind: 'form', message: 'Please complete all required fields.' };
  }
  if (!email) {
    return { ok: false, kind: 'email', message: 'Please enter your email address.' };
  }
  if (!isValidEmailFormat(email)) {
    return { ok: false, kind: 'email', message: 'Please enter a valid email address.' };
  }
  if (!password) {
    return { ok: false, kind: 'password', message: 'Please enter your password.' };
  }
  if (password.length < 8) {
    return { ok: false, kind: 'password', message: 'Password must be at least 8 characters.' };
  }

  return { ok: true, email };
}
