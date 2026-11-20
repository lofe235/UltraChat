'use strict';

const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_SITE_URL = 'https://ultrachat2026.alwaysdata.net';
const DEFAULT_TTL_MINUTES = 10;
const CONFIG_CACHE_MS = 2_000;

function escapeHtml(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function prettyCode(code) {
  const digits = String(code || '').replace(/\D/g, '');
  if (digits.length === 6) return `${digits.slice(0, 3)} ${digits.slice(3)}`;
  return digits;
}

function formatRequestTime(date, timeZone) {
  try {
    return new Intl.DateTimeFormat('ru-RU', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: timeZone || 'Europe/Moscow',
    }).format(date || new Date());
  } catch {
    return (date || new Date()).toISOString();
  }
}

/** Простой текстовый вариант: нужен старым клиентам и для доставляемости. */
function renderText({ code, ttlMinutes, device, time, siteUrl }) {
  return [
    'UltraChat — вход по коду',
    '',
    `Ваш код: ${code}`,
    `Код действует ${ttlMinutes} минут.`,
    '',
    `Запрос: ${device} · ${time}`,
    '',
    'Если это были не вы — просто проигнорируйте письмо.',
    'Никому не сообщайте код: сотрудники UltraChat никогда его не спрашивают.',
    '',
    siteUrl,
  ].join('\n');
}

function fallbackHtml({ codePretty, ttlMinutes, device, time, year, siteUrl }) {
  return `<!DOCTYPE html>
<html lang="ru"><body style="margin:0;padding:32px 12px;background:#eef2f8;font-family:Segoe UI,Arial,sans-serif;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#eef2f8;">
<tr><td align="center">
<table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0"
  style="width:560px;max-width:560px;background:#070c18;border-radius:24px;border:1px solid #1d2b47;overflow:hidden;">
<tr><td colspan="2" style="padding:32px 36px 0;font-size:24px;font-weight:700;color:#f8fafc;">UltraChat</td></tr>
<tr><td colspan="2" style="padding:20px 36px 0;font-size:28px;font-weight:700;color:#f8fafc;">Вход в UltraChat</td></tr>
<tr><td colspan="2" style="padding:22px 36px 0;">
  <div style="background:#0d1729;border:1px solid #27405f;border-radius:18px;padding:26px 18px;text-align:center;font-family:Consolas,monospace;font-size:42px;font-weight:700;color:#7dd3fc;letter-spacing:6px;">${escapeHtml(codePretty)}</div>
</td></tr>
<tr><td colspan="2" style="padding:22px 36px 0;font-size:15px;line-height:1.6;color:#9fb0c9;">
  Код действует ${escapeHtml(ttlMinutes)} минут. Запрос: ${escapeHtml(device)} · ${escapeHtml(time)}.
</td></tr>
<tr><td colspan="2" style="padding:26px 36px 34px;font-size:12px;line-height:1.7;color:#64748b;">
  Если это были не вы — просто проигнорируйте письмо. Никому не сообщайте код.<br>
  <a href="${escapeHtml(siteUrl)}" style="color:#38bdf8;text-decoration:none;">${escapeHtml(siteUrl)}</a> · © ${escapeHtml(year)} UltraChat
</td></tr>
</table>
</td></tr></table>
</body></html>`;
}

function renderHtml(template, values) {
  let html = String(template || '');
  const map = {
    '{{CODE}}': values.code,
    '{{CODE_PRETTY}}': values.codePretty,
    '{{TTL}}': values.ttlMinutes,
    '{{DEVICE}}': values.device,
    '{{TIME}}': values.time,
    '{{YEAR}}': values.year,
    '{{SITE_URL}}': values.siteUrl,
  };
  for (const [token, value] of Object.entries(map)) {
    html = html.split(token).join(escapeHtml(value));
  }
  return html;
}

function loadTemplate(explicitPath) {
  const candidates = [
    explicitPath,
    path.join(__dirname, '..', 'email', 'code.html'),
    path.join(__dirname, '..', '..', 'email', 'code.html'),
  ].filter(Boolean);
  for (const candidate of candidates) {
    try {
      const html = fs.readFileSync(candidate, 'utf8');
      if (html.includes('{{CODE}}') || html.includes('{{CODE_PRETTY}}')) return html;
    } catch {
      /* пробуем следующий путь */
    }
  }
  return null;
}

/**
 * Настройки можно положить в data/mail.json — так пароль приложения Gmail
 * не нужно прописывать в панели хостинга и он не попадает в git.
 * Формат:
 * { "user": "ultrachat@gmail.com", "pass": "app-password",
 *   "from": "ultrachat@gmail.com", "fromName": "UltraChat" }
 */
function loadFileConfig(configPath) {
  if (!configPath) return {};
  try {
    const parsed = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * Отправка писем с кодом входа.
 *
 * Настройки берутся по приоритету: аргументы → data/mail.json → окружение.
 * Для Gmail достаточно "user" + "pass" (пароль приложения).
 */
function createMailer(options = {}) {
  const template = loadTemplate(options.templatePath);
  let cached = null;
  let cachedAt = 0;
  let transporter = null;
  let transporterKey = '';

  function resolve() {
    const now = Date.now();
    if (cached && now - cachedAt < CONFIG_CACHE_MS) return cached;
    const env = process.env;
    const file = loadFileConfig(options.configPath);
    const pick = (...keys) => {
      for (const key of keys) {
        if (options[key] != null && options[key] !== '') return options[key];
        if (file[key] != null && file[key] !== '') return file[key];
        if (env[key] != null && env[key] !== '') return env[key];
      }
      return '';
    };

    const gmailUser = String(pick('GMAIL_USER', 'user')).trim();
    const host = String(pick('SMTP_HOST', 'host') || (gmailUser ? 'smtp.gmail.com' : '')).trim();
    const port = Number(pick('SMTP_PORT', 'port') || (gmailUser ? 465 : 587));
    const secure = options.secure != null
      ? options.secure === true
      : String(pick('SMTP_SECURE', 'secure') || (port === 465 ? '1' : '')) === '1';
    const user = String(pick('SMTP_USER', 'user') || gmailUser).trim();
    const pass = String(pick('SMTP_PASS', 'GMAIL_APP_PASSWORD', 'pass')).replace(/\s+/g, '');

    cached = {
      host,
      port,
      secure,
      user,
      pass,
      fromName: String(pick('fromName', 'MAIL_FROM_NAME') || 'UltraChat').trim(),
      fromEmail: String(pick('from', 'MAIL_FROM') || user || '').trim(),
      replyTo: String(pick('replyTo', 'MAIL_REPLY_TO') || '').trim(),
      siteUrl: String(pick('siteUrl', 'SITE_URL') || DEFAULT_SITE_URL).trim(),
      timeZone: String(pick('timeZone', 'MAIL_TIMEZONE') || 'Europe/Moscow').trim(),
      ttlMinutes: Number(pick('ttlMinutes', 'MAIL_CODE_TTL_MINUTES') || DEFAULT_TTL_MINUTES) || DEFAULT_TTL_MINUTES,
      enabled: Boolean(host && user && pass),
    };
    cachedAt = now;
    return cached;
  }

  function transport(config) {
    const key = `${config.host}:${config.port}:${config.user}`;
    if (transporter && transporterKey === key) return transporter;
    // require ленивый: без настроенного SMTP сервер работает как раньше.
    const nodemailer = require('nodemailer');
    transporter = nodemailer.createTransport({
      host: config.host,
      port: config.port,
      secure: config.secure,
      auth: { user: config.user, pass: config.pass },
      connectionTimeout: 15_000,
      greetingTimeout: 15_000,
      socketTimeout: 25_000,
    });
    transporterKey = key;
    return transporter;
  }

  function buildMessage({ to, code, device, now }) {
    const config = resolve();
    const values = {
      code: String(code),
      codePretty: prettyCode(code),
      ttlMinutes: config.ttlMinutes,
      device: device || 'Неизвестное устройство',
      time: formatRequestTime(now, config.timeZone),
      year: String((now || new Date()).getFullYear()),
      siteUrl: config.siteUrl,
    };
    return {
      config,
      message: {
        from: config.fromName ? `"${config.fromName}" <${config.fromEmail}>` : config.fromEmail,
        to,
        replyTo: config.replyTo || undefined,
        subject: `${values.code} — код входа в UltraChat`,
        text: renderText({ code: values.code, ttlMinutes: values.ttlMinutes, device: values.device, time: values.time, siteUrl: values.siteUrl }),
        html: template ? renderHtml(template, values) : fallbackHtml(values),
        headers: {
          'X-Entity-Ref-ID': `ultrachat-${Date.now()}`,
          'X-Auto-Response-Suppress': 'All',
        },
      },
    };
  }

  async function sendCode({ to, code, device, now }) {
    const { config, message } = buildMessage({ to, code, device, now });
    if (!config.enabled) {
      const err = new Error('SMTP не настроен');
      err.code = 'mail_not_configured';
      throw err;
    }
    return transport(config).sendMail(message);
  }

  async function verify() {
    const config = resolve();
    if (!config.enabled) return { ok: false, error: 'mail_not_configured' };
    try {
      await transport(config).verify();
      return { ok: true, host: config.host, port: config.port, user: config.user, from: config.fromEmail };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  }

  return {
    get enabled() { return resolve().enabled; },
    get fromEmail() { return resolve().fromEmail; },
    get siteUrl() { return resolve().siteUrl; },
    get ttlMinutes() { return resolve().ttlMinutes; },
    configPath: options.configPath || '',
    sendCode,
    verify,
    renderPreview(overrides = {}) {
      const config = resolve();
      const values = {
        code: overrides.code || '482916',
        codePretty: prettyCode(overrides.code || '482916'),
        ttlMinutes: config.ttlMinutes,
        device: overrides.device || 'Windows · Chrome',
        time: formatRequestTime(overrides.now, config.timeZone),
        year: String((overrides.now || new Date()).getFullYear()),
        siteUrl: config.siteUrl,
      };
      return {
        html: template ? renderHtml(template, values) : fallbackHtml(values),
        text: renderText(values),
        subject: `${values.code} — код входа в UltraChat`,
      };
    },
  };
}

module.exports = {
  createMailer,
  renderHtml,
  renderText,
  prettyCode,
  formatRequestTime,
  escapeHtml,
  DEFAULT_TTL_MINUTES,
};
