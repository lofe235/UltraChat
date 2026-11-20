'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const MAX_EMAIL_LENGTH = 254;
const DEFAULT_TTL_MS = 10 * 60 * 1000;
const DEFAULT_COOLDOWN_MS = 60 * 1000;
const DEFAULT_MAX_PER_HOUR = 5;
const DEFAULT_MAX_ATTEMPTS = 5;
const HOUR_MS = 60 * 60 * 1000;

function normalizeEmail(raw) {
  return String(raw || '')
    .trim()
    .toLowerCase()
    .slice(0, MAX_EMAIL_LENGTH);
}

/**
 * Достаточно строгая проверка: одна @, домен с точкой, без пробелов.
 * Полную валидацию всё равно делает почтовый сервер, нам важно отсечь мусор.
 */
function isValidEmail(email) {
  if (typeof email !== 'string') return false;
  if (email.length < 6 || email.length > MAX_EMAIL_LENGTH) return false;
  if (!/^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/.test(email)) return false;
  if (email.includes('..')) return false;
  return true;
}

function hashEmail(email) {
  return crypto.createHash('sha256').update(`email:${normalizeEmail(email)}`).digest('hex');
}

/**
 * Хранение кодов входа: в файле лежат только HMAC-хеши, сам код нигде
 * не сохраняется. Шесть цифр — это миллион вариантов, поэтому обычный
 * sha256 без секрета здесь не годится: его можно перебрать офлайн.
 */
class EmailAuth {
  constructor(options = {}) {
    this.dataDir = options.dataDir;
    this.mailer = options.mailer || null;
    this.ttlMs = Number(options.ttlMs) > 0 ? Number(options.ttlMs) : DEFAULT_TTL_MS;
    this.cooldownMs = Number.isFinite(options.cooldownMs)
      ? Math.max(0, Number(options.cooldownMs))
      : DEFAULT_COOLDOWN_MS;
    this.maxPerHour = Number(options.maxPerHour) > 0 ? Number(options.maxPerHour) : DEFAULT_MAX_PER_HOUR;
    this.maxAttempts = Number(options.maxAttempts) > 0 ? Number(options.maxAttempts) : DEFAULT_MAX_ATTEMPTS;
    this.exposeCode = options.exposeCode === true;
    this.now = typeof options.now === 'function' ? options.now : () => Date.now();
    this.secret = this.loadSecret(options.secret);
    this.file = path.join(this.dataDir, 'email-codes.json');
    this.codes = this.loadCodes();
  }

  loadSecret(explicit) {
    if (explicit) return String(explicit);
    const file = path.join(this.dataDir, 'email-secret.txt');
    try {
      const existing = fs.readFileSync(file, 'utf8').trim();
      if (existing) return existing;
    } catch {
      /* файла ещё нет */
    }
    const secret = crypto.randomBytes(32).toString('hex');
    try {
      fs.mkdirSync(this.dataDir, { recursive: true });
      fs.writeFileSync(file, `${secret}\n`, { encoding: 'utf8', mode: 0o600 });
    } catch {
      /* секрет останется в памяти до перезапуска */
    }
    return secret;
  }

  loadCodes() {
    try {
      const raw = fs.readFileSync(this.file, 'utf8');
      const parsed = JSON.parse(raw);
      const codes = parsed && typeof parsed.codes === 'object' && parsed.codes ? parsed.codes : {};
      const now = this.now();
      const result = {};
      for (const [key, entry] of Object.entries(codes)) {
        if (!entry || typeof entry !== 'object') continue;
        if (Number(entry.expiresAt) <= now) continue;
        result[key] = entry;
      }
      if (Object.keys(result).length !== Object.keys(codes).length) {
        this.codes = result;
        this.save();
      }
      return result;
    } catch {
      return {};
    }
  }

  save() {
    try {
      fs.mkdirSync(this.dataDir, { recursive: true });
      const tmp = `${this.file}.tmp`;
      fs.writeFileSync(tmp, JSON.stringify({ version: 1, codes: this.codes }), 'utf8');
      fs.renameSync(tmp, this.file);
    } catch (err) {
      console.error('[email-auth] не удалось сохранить коды:', err.message);
    }
  }

  hashCode(email, code) {
    return crypto
      .createHmac('sha256', this.secret)
      .update(`${normalizeEmail(email)}:${String(code)}`)
      .digest('hex');
  }

  /** Убирает просроченные записи, чтобы файл не рос бесконечно. */
  cleanup() {
    const now = this.now();
    let changed = false;
    for (const [key, entry] of Object.entries(this.codes)) {
      if (!entry || Number(entry.expiresAt) <= now) {
        delete this.codes[key];
        changed = true;
      }
    }
    if (changed) this.save();
  }

  rateLimited(key, now) {
    const entry = this.codes[key];
    if (!entry) return null;
    const sentAt = Number(entry.sentAt) || 0;
    if (now - sentAt < this.cooldownMs) {
      return Math.max(1, Math.ceil((this.cooldownMs - (now - sentAt)) / 1000));
    }
    const recent = (Array.isArray(entry.sentTimes) ? entry.sentTimes : [])
      .map(Number)
      .filter((ts) => now - ts < HOUR_MS);
    if (recent.length >= this.maxPerHour) {
      return Math.max(1, Math.ceil((HOUR_MS - (now - recent[0])) / 1000));
    }
    return null;
  }

  async requestCode({ email, device }) {
    const normalized = normalizeEmail(email);
    if (!isValidEmail(normalized)) {
      return { ok: false, status: 400, error: 'invalid_email' };
    }
    if (!this.mailer || !this.mailer.enabled) {
      return { ok: false, status: 503, error: 'mail_not_configured' };
    }

    const now = this.now();
    const key = hashEmail(normalized);
    const wait = this.rateLimited(key, now);
    if (wait) {
      return { ok: false, status: 429, error: 'rate_limited', retryAfter: wait };
    }

    const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
    const previous = this.codes[key];
    const sentTimes = (Array.isArray(previous && previous.sentTimes) ? previous.sentTimes : [])
      .map(Number)
      .filter((ts) => now - ts < HOUR_MS);

    this.codes[key] = {
      codeHash: this.hashCode(normalized, code),
      createdAt: now,
      expiresAt: now + this.ttlMs,
      attempts: 0,
      sentAt: now,
      sentTimes: [...sentTimes, now].slice(-this.maxPerHour),
    };
    this.save();

    try {
      await this.mailer.sendCode({ to: normalized, code, device });
    } catch (err) {
      delete this.codes[key];
      this.save();
      console.error('[email-auth] письмо не отправлено:', err.message);
      return { ok: false, status: 503, error: 'mail_failed' };
    }

    return {
      ok: true,
      ttlMinutes: Math.max(1, Math.round(this.ttlMs / 60_000)),
      ...(this.exposeCode ? { devCode: code } : {}),
    };
  }

  /** Проверка кода. Аккаунт создаёт вызывающая сторона: у неё есть Store. */
  verifyCode({ email, code }) {
    const normalized = normalizeEmail(email);
    if (!isValidEmail(normalized)) {
      return { ok: false, status: 400, error: 'invalid_email' };
    }
    const digits = String(code || '').replace(/\D/g, '');
    if (digits.length !== 6) {
      return { ok: false, status: 400, error: 'invalid_code' };
    }

    const key = hashEmail(normalized);
    const entry = this.codes[key];
    if (!entry) {
      return { ok: false, status: 400, error: 'code_not_requested' };
    }
    const now = this.now();
    if (Number(entry.expiresAt) <= now) {
      delete this.codes[key];
      this.save();
      return { ok: false, status: 400, error: 'code_expired' };
    }
    if (Number(entry.attempts) >= this.maxAttempts) {
      delete this.codes[key];
      this.save();
      return { ok: false, status: 429, error: 'too_many_attempts' };
    }

    const expected = Buffer.from(String(entry.codeHash || ''), 'hex');
    const actual = Buffer.from(this.hashCode(normalized, digits), 'hex');
    const match = expected.length === actual.length && crypto.timingSafeEqual(expected, actual);

    if (!match) {
      entry.attempts = Number(entry.attempts || 0) + 1;
      const left = Math.max(0, this.maxAttempts - entry.attempts);
      if (left === 0) delete this.codes[key];
      this.save();
      return { ok: false, status: 400, error: 'invalid_code', attemptsLeft: left };
    }

    delete this.codes[key];
    this.save();
    return { ok: true, email: normalized };
  }
}

module.exports = {
  EmailAuth,
  normalizeEmail,
  isValidEmail,
  hashEmail,
  MAX_EMAIL_LENGTH,
  DEFAULT_TTL_MS,
  DEFAULT_COOLDOWN_MS,
  DEFAULT_MAX_PER_HOUR,
  DEFAULT_MAX_ATTEMPTS,
};
