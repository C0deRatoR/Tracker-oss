import { createRemoteJWKSet, jwtVerify } from 'jose'

// Declared rather than pulled from @types/node: two env reads are not worth a types
// package that Vercel's own build-time tsc could not resolve under pnpm's layout, and
// which logged TS2688 on every deploy.
declare const process: { env: Record<string, string | undefined> }

/**
 * Borrows one Gemini key to every install of the app, so nobody has to bring their own.
 *
 * Deliberately a transparent pass-through of `generateContent`: the request and response
 * bodies are Google's, untouched, so the Android client keeps its own DTOs, its prompt
 * cache and — the part that matters — its failure matrix, which already knows what a 429
 * or a safety block means. Only the host and the auth header differ from calling Google
 * directly, which is why the client can still do exactly that when a user sets their own key.
 */

/** Google's public keys for Firebase ID tokens. `jose` caches and refreshes these itself. */
const JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
)

const GEMINI_ORIGIN = 'https://generativelanguage.googleapis.com'

/**
 * The only shape that gets forwarded.
 *
 * Everything after `/v1beta/` arrives as user input and ends up in a URL we fetch, so it is
 * matched against this rather than sanitised — a model id is a short, boring string, and
 * anything else is someone probing for a way to point this proxy at a host of their choosing.
 */
const ALLOWED_PATH = /^models\/[a-zA-Z0-9.-]{1,64}:generateContent$/

type Verdict =
  | { ok: true; uid: string }
  | { ok: false; status: number; error: string }

/**
 * Exported by method name, not as a default.
 *
 * A default export is handed Vercel's legacy `(req, res)` pair, so a handler written against
 * the web `Request` silently receives the wrong object, returns a `Response` nobody reads,
 * and the caller hangs until it times out. Naming the method is what selects the web
 * signature — and it means anything other than POST is refused before reaching this code.
 */
export async function POST(request: Request): Promise<Response> {
  const startedAt = Date.now()

  const geminiKey = process.env.GEMINI_API_KEY
  const projectId = process.env.FIREBASE_PROJECT_ID
  if (!geminiKey || !projectId) {
    // A missing env var is our fault, not the caller's, and it must not read as a bad key.
    console.error('proxy_misconfigured', {
      hasGeminiKey: Boolean(geminiKey),
      hasProjectId: Boolean(projectId),
    })
    return problem(500, 'Proxy is not configured.')
  }

  const path = decodeURIComponent(new URL(request.url).searchParams.get('path') ?? '')
  if (!ALLOWED_PATH.test(path)) {
    return problem(404, 'Unknown endpoint.')
  }

  const verdict = await verify(request.headers.get('authorization'), projectId)
  if (!verdict.ok) {
    return problem(verdict.status, verdict.error)
  }

  const body = await request.text()

  let upstream: Response
  try {
    upstream = await fetch(`${GEMINI_ORIGIN}/v1beta/${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-goog-api-key': geminiKey },
      body,
    })
  } catch (cause) {
    console.error('upstream_unreachable', { uid: short(verdict.uid), path, cause: String(cause) })
    // 502 rather than a rewritten Gemini error: the client reads 5xx as "try again shortly",
    // which is the right advice when it is the hop to Google that broke.
    return problem(502, "Couldn't reach Gemini.")
  }

  // Never the bodies: the request carries a meal photo and the response describes what
  // someone ate. Everything here is either an id, a status or a duration.
  console.log('proxied', {
    uid: short(verdict.uid),
    path,
    status: upstream.status,
    requestBytes: body.length,
    ms: Date.now() - startedAt,
  })

  // Status and payload verbatim, so the client's existing handling of 429, safety blocks and
  // malformed replies keeps working against the proxy exactly as it did against Google.
  return new Response(upstream.body, {
    status: upstream.status,
    headers: { 'content-type': upstream.headers.get('content-type') ?? 'application/json' },
  })
}

/**
 * Accepts a Firebase ID token from this project and nothing else.
 *
 * `jwtVerify` covers signature, expiry and not-before; issuer and audience are what stop a
 * token minted for somebody else's Firebase project from spending our quota.
 */
async function verify(header: string | null, projectId: string): Promise<Verdict> {
  const token = header?.match(/^Bearer (.+)$/)?.[1]
  if (!token) {
    return { ok: false, status: 401, error: 'Missing bearer token.' }
  }

  try {
    const { payload } = await jwtVerify(token, JWKS, {
      issuer: `https://securetoken.google.com/${projectId}`,
      audience: projectId,
    })

    // Firebase puts the stable per-install id in `sub`. A token without one is not something
    // we can meter or revoke against, so it does not get to spend the shared key.
    const uid = typeof payload.sub === 'string' ? payload.sub : ''
    if (!uid) {
      return { ok: false, status: 401, error: 'Token carries no subject.' }
    }

    return { ok: true, uid }
  } catch (cause) {
    // Deliberately opaque to the caller — expired, wrong project and forged are the same
    // answer from outside. The reason is logged for us, without the token itself.
    console.warn('token_rejected', { reason: String(cause) })
    return { ok: false, status: 401, error: 'Token was rejected.' }
  }
}

/**
 * Mirrors Google's own error envelope, so a client that already parses their failures for a
 * message does not need a second shape for ours.
 */
function problem(status: number, message: string): Response {
  return new Response(JSON.stringify({ error: { code: status, message } }), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

/** Enough of a uid to follow one install through the logs, not enough to be an identifier. */
function short(uid: string): string {
  return uid.slice(0, 8)
}
