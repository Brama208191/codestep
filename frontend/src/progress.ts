const SOLVED_KEY = "codestep.solved";
const CLIENT_KEY = "codestep.client";

let sessionClientId: string | null = null;

export function getClientId(): string {
  const stored = read(CLIENT_KEY);
  if (stored) return stored;
  sessionClientId ??= crypto.randomUUID();
  write(CLIENT_KEY, sessionClientId);
  return sessionClientId;
}
const codeKey = (slug: string) => `codestep.code.${slug}`;

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    return;
  }
}

export function getSolved(): Set<string> {
  try {
    return new Set(JSON.parse(read(SOLVED_KEY) ?? "[]"));
  } catch {
    return new Set();
  }
}

export function markSolved(slug: string) {
  const solved = getSolved();
  solved.add(slug);
  write(SOLVED_KEY, JSON.stringify([...solved]));
}

export function loadCode(slug: string): string | null {
  return read(codeKey(slug));
}

export function saveCode(slug: string, code: string) {
  write(codeKey(slug), code);
}

export function clearCode(slug: string) {
  write(codeKey(slug), null);
}
