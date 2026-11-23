// Loads sample data through the real API (Firebase Auth sign-up + REST calls). Idempotent for users.
// Usage: node scripts/seed.mjs   (backend must be running on :8081)
const KEY = 'AIzaSyAlVi41KCuNhrVTqQUIPRM9_QSn6f9EdzY';
const API = process.env.API_URL ?? 'http://localhost:8081/api';
const PASSWORD = 'Classlink123!';

async function auth(kind, email) {
  const r = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:${kind}?key=${KEY}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
  });
  return { ok: r.ok, body: await r.json() };
}

async function user(prenom, nom, email) {
  let r = await auth('signUp', email);
  if (!r.ok) r = await auth('signInWithPassword', email);
  if (!r.ok) throw new Error(`auth ${email}: ${JSON.stringify(r.body)}`);
  const token = r.body.idToken;
  const call = async (method, path, body) => {
    const res = await fetch(API + path, {
      method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`);
    return text ? JSON.parse(text) : null;
  };
  await call('POST', '/auth/register', { nom, prenom });
  return call;
}

const day = (n, h = 18) => {
  const d = new Date(Date.now() + n * 86400000); d.setHours(h, 0, 0, 0);
  const p = (x) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:00:00`;
};

const prof = await user('Alice', 'Durand', 'prof@classlink.test');
const bob = await user('Bob', 'Martin', 'bob@classlink.test');
const lea = await user('Léa', 'Petit', 'lea@classlink.test');

const existing = await prof('GET', '/classes');
if (existing.length) { console.log('Already seeded.'); process.exit(0); }

const defs = [
  ['Mathématiques 3A', 'Mathématiques', 'Algèbre et analyse'],
  ['Informatique L2', 'Informatique', 'Programmation orientée objet'],
];
for (const [nom, matiere, description] of defs) {
  const c = await prof('POST', '/classes', { nom, matiere, description });
  await bob('POST', '/classes/join', { codeAcces: c.codeAcces });
  await lea('POST', '/classes/join', { codeAcces: c.codeAcces });
  console.log(`${nom} — code ${c.codeAcces}`);
  await prof('POST', `/classes/${c.id}/publications`, { type: 'ANNONCE', titre: 'Bienvenue', contenu: `Bienvenue dans ${nom} !` });
  await prof('POST', `/classes/${c.id}/publications`, { type: 'COURS', titre: 'Chapitre 1', contenu: 'Support de cours du chapitre 1.' });
  const d1 = await prof('POST', `/classes/${c.id}/publications`, { type: 'DEVOIR', titre: 'TP 1', contenu: 'Exercices 1 à 5.', dateLimite: day(3), bareme: 20 });
  await prof('POST', `/classes/${c.id}/publications`, { type: 'DEVOIR', titre: 'Projet', contenu: 'Projet de fin de module.', dateLimite: day(12, 23), bareme: 40 });
  await bob('POST', `/publications/${d1.id}/comments`, { contenu: 'Peut-on le rendre en binôme ?' });
  await prof('POST', `/publications/${d1.id}/comments`, { contenu: 'Non, travail individuel.' });
  const r = await bob('POST', `/assignments/${d1.id}/submissions`, { texte: 'Voici mes réponses au TP 1.' });
  await lea('POST', `/assignments/${d1.id}/submissions`, { texte: 'Mon rendu du TP 1.' });
  await prof('PUT', `/submissions/${r.id}/grade`, { note: 16.5, correction: 'Très bon travail.' });
}
console.log(`Done. Logins: prof@/bob@/lea@classlink.test — password ${PASSWORD}`);