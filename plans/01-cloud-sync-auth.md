# Cloud Sync — Phase 1: Google SSO Auth

## Project Overview (for context)

**Chobi** is a minimal Android expense tracker built with Jetpack Compose and Room (local SQLite). Currently offline-only. Goal: add cloud sync so data persists across devices.

- **GitHub (private)**: `/Volumes/realme/Dev/chobi`
- **App package**: `mrndstvndv.me.chobi`
- **Tech**: Kotlin, Jetpack Compose, Room, Navigation3, DataStore
- **Build system**: Gradle (Kotlin DSL) with version catalog (`gradle/libs.versions.toml`)

### What we're building

```
┌──────────────────────────────┐
│        Android App           │
│  Room (local SQLite)         │
│  AuthManager (Google SSO)    │
│  SyncEngine (future)         │
└──────────┬───────────────────┘
           │ HTTPS (authenticated)
           ▼
┌──────────────────────────────┐
│    Cloudflare Workers API     │
│  POST /auth/google            │
│  POST /auth/refresh           │
│  GET  /auth/me                │
│  POST /sync/push (future)     │
│  GET  /sync/pull (future)      │
├──────────────────────────────┤
│  D1 (users, data tables)      │
│  KV (sessions)                │
└──────────────────────────────┘
```

### How the pieces connect

```
[Android App]                 [Cloudflare Worker]            [Google]
     │                               │                         │
     │── POST /auth/google ──────────►│                         │
     │   { idToken }                  │─── fetch JWKS ─────────►│
     │                               │◄── verify JWT ──────────│
     │                               │─── upsert user in D1 ──►│
     │                               │─── create session in KV │
     │◄── { accessToken, user } ─────│                         │
     │                               │                         │
     │── (future) GET /sync/pull ───►│  (uses accessToken      │
     │   Authorization: Bearer ...   │   to identify user)     │
```

### Auth is the prerequisite

Sync depends on auth — the Worker needs a user identity to scope data. No auth = no sync.

---

## Step 1: Google Cloud Console Setup

**What**: Create OAuth 2.0 credentials so Google can issue identity tokens that your app and Worker trust.

### 1.1 Create OAuth consent screen

1. Go to [Google Cloud Console > APIs & Services > OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent)
2. User type: **External**
3. Fill in:
   - App name: `Chobi`
   - User support email: your email
   - Developer contact: your email
4. Scopes: add `.../auth/userinfo.email` and `.../auth/userinfo.profile` (or just skip — the defaults work for Sign-In)
5. Test users: add your Google account(s)
6. Publishing status: leave as **Testing** for now (you can publish later when ready for other users)

### 1.2 Create Android OAuth client ID

1. Go to [Credentials](https://console.cloud.google.com/apis/credentials)
2. **Create Credentials** → **OAuth client ID**
3. Application type: **Android**
4. Package name: `mrndstvndv.me.chobi`
5. SHA-1 signing certificate fingerprint:

   **Debug** (needed for development builds):
   ```bash
   # On Mac/Linux — prints the debug keystore SHA-1
   keytool -exportcert -alias androiddebugkey \
     -keystore ~/.android/debug.keystore \
     -storepass android -keypass android \
     | openssl sha1 -binary | xxd -p | fold -w 2 | paste -sd':' -
   ```
   Typical debug SHA-1: `5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`

   **Release** (for production — uses the keystore at `./release.jks`):
   ```bash
   keytool -exportcert -alias release \
     -keystore /Volumes/realme/Dev/chobi/release.jks \
     | openssl sha1 -binary | xxd -p | fold -w 2 | paste -sd':' -
   ```
   The alias might differ — check `keystore.properties` for the `keyAlias` value.

6. Note the **Android client ID** (looks like `xxx.apps.googleusercontent.com`)

### 1.3 Create Web OAuth client ID

1. **Create Credentials** → **OAuth client ID**
2. Application type: **Web application**
3. Name: `Chobi Worker`
4. Authorized redirect URIs: leave empty (we don't use the web flow — this client ID is only for token verification)
5. Note the **Web client ID** (looks like `yyy.apps.googleusercontent.com`)

### 1.4 Save credentials

Store securely — you'll need these in steps 2 and 3:

| Credential | Purpose |
|---|---|
| **Android client ID** | Used by Android Credential Manager to request the ID token |
| **Web client ID** | Used by Cloudflare Worker to verify the ID token's `aud` (audience) claim |

### Resources

- [Google Identity: Get an ID Token](https://developers.google.com/identity/oauth2/web/guides/use-token-model)
- [Android Credential Manager — Google Sign-In](https://developer.android.com/training/sign-in/credential-manager)
- [Google OAuth 2.0 for Mobile & Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)

---

## Step 2: Cloudflare Workers Auth API

**What**: A Workers project with an auth endpoint that validates Google ID tokens and issues session tokens.

### 2.1 Scaffold the project

```bash
cd /Volumes/realme/Dev/chobi
mkdir -p workers/auth
cd workers/auth

# Create package.json
bun init -y
bun add jose  # JWT verification library
bun add -d typescript @cloudflare/workers-types

# Init wrangler
bunx wrangler init
```

### 2.2 Configure Cloudflare resources

**D1 database** (for users):

```bash
bunx wrangler d1 create chobi-auth
# → Note the database_id output
```

**KV namespace** (for sessions):

```bash
bunx wrangler kv:namespace create CHOB_SESSIONS
# → Note the id output
```

### 2.3 `wrangler.toml`

```toml
name = "chobi-auth"
main = "src/index.ts"
compatibility_date = "2026-07-07"

# D1 binding
[[d1_databases]]
binding = "DB"
database_name = "chobi-auth"
database_id = "<paste-database-id>"

# KV binding
[[kv_namespaces]]
binding = "SESSIONS"
id = "<paste-namespace-id>"
```

### 2.4 D1 schema (`schema.sql`)

```sql
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  google_sub TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL,
  name TEXT NOT NULL,
  picture TEXT,
  created_at INTEGER NOT NULL DEFAULT (unixepoch()),
  updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);

CREATE INDEX idx_users_google_sub ON users(google_sub);
```

Apply:

```bash
bunx wrangler d1 execute chobi-auth --file=schema.sql
```

### 2.5 Worker implementation (`src/index.ts`)

Full implementation — see [src/index.ts template below](#appendix-a-worker-index.ts).

**Key logic:**

```
POST /auth/google
  1. Parse { idToken } from request body
  2. Fetch Google's JWKS (cached at edge)
  3. Verify JWT signature + issuer + audience (web client ID)
  4. Extract payload: sub, email, name, picture
  5. Upsert user in D1 (key on google_sub)
  6. Generate session token (random UUID)
  7. Store in KV with 7-day TTL: { userId, createdAt }
  8. Return { accessToken, refreshToken, user: { id, email, name } }

POST /auth/refresh
  1. Parse { refreshToken } from request body
  2. Look up in KV, verify it exists and is not expired
  3. Generate new session token, delete old one
  4. Return new accessToken

GET /auth/me
  1. Extract Bearer token from Authorization header
  2. Look up in KV → get userId
  3. Fetch user from D1
  4. Return user info
```

### 2.6 Set environment secrets

```bash
echo "<web-client-id>" | wrangler secret put GOOGLE_WEB_CLIENT_ID
```

### 2.7 Deploy

```bash
bunx wrangler deploy
```

**Note the URL**: `https://chobi-auth.<your-subdomain>.workers.dev`

### Resources

- [Cloudflare Workers — Get Started](https://developers.cloudflare.com/workers/get-started/guide/)
- [D1 — Get Started](https://developers.cloudflare.com/d1/get-started/)
- [KV — Bindings](https://developers.cloudflare.com/kv/api/)
- [`jose` — JWT verification](https://github.com/panva/jose)
- [Google OpenID Connect — JWKS endpoint](https://developers.google.com/identity/protocols/oauth2/openid-connect#obtainuserinfo)

---

## Step 3: Android Auth Integration

**What**: Add Google Sign-In to Chobi using Android's Credential Manager, wire it to the Worker backend.

### 3.1 Add dependencies

Update `gradle/libs.versions.toml`:

```toml
[versions]
androidxCredentials = "1.5.0"
googlePlayServicesAuth = "21.2.0"

[libraries]
androidx-credentials = { module = "androidx.credentials:credentials", version.ref = "androidxCredentials" }
androidx-credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "androidxCredentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "androidxCredentials" }
```

Add to `app/build.gradle.kts` in `dependencies` block:

```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services)
implementation(libs.googleid)
```

### 3.2 Create auth data layer

**New files to create:**

```
app/src/main/java/com/example/chobi/data/auth/
├── AuthManager.kt          # Google Sign-In + Worker API calls
├── AuthState.kt            # Sealed interface for auth state
├── AuthApi.kt              # HTTP client for Worker auth endpoints
└── SessionStore.kt         # Persist tokens in DataStore
```

**`AuthState.kt`** — the user-facing auth state:

```kotlin
sealed interface AuthState {
  data object Unknown : AuthState
  data object SignedOut : AuthState
  data class SignedIn(
    val accessToken: String,
    val user: UserInfo
  ) : AuthState
  data object Loading : AuthState
  data class Error(val message: String) : AuthState
}

data class UserInfo(
  val id: String,
  val email: String,
  val name: String,
  val picture: String?
)
```

**`SessionStore.kt`** — persist tokens in DataStore:

```kotlin
// Stores accessToken + userId + userEmail + userName in DataStore preferences
// Keys: AUTH_ACCESS_TOKEN, AUTH_USER_ID, AUTH_USER_EMAIL, AUTH_USER_NAME
```

**`AuthApi.kt`** — HTTP client for Worker:

```kotlin
// POST /auth/google — sends { idToken }, receives { accessToken, user }
// POST /auth/refresh — sends { refreshToken }, receives { accessToken }
// GET  /auth/me — Bearer token in header, receives { id, email, name, picture }
```

**`AuthManager.kt`** — orchestrates the flow:

```kotlin
class AuthManager(
  private val activity: Activity,
  private val authApi: AuthApi,
  private val sessionStore: SessionStore
) {
  // _authState: MutableStateFlow<AuthState>
  // val authState: StateFlow<AuthState>
  
  // Check session store on init → restore session if token exists
  // signIn() → launch Google Credential Manager flow
  // signOut() → clear tokens, notify Worker to revoke (optional)
}
```

### 3.3 Wire Credential Manager (in `AuthManager`)

The Google Sign-In flow using Credential Manager:

```kotlin
suspend fun signIn() {
  // 1. Create GetCredentialRequest with Google ID credential option
  val googleIdOption = GetGoogleIdOption.Builder()
    .setServerClientId(WEB_CLIENT_ID)  // The Web client ID from step 1
    .setFilterByAuthorizedAccounts(false)
    .setAutoSelectEnabled(false)
    .build()

  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  // 2. Launch Credential Manager
  val credentialManager = CredentialManager.create(activity)
  val result = credentialManager.getCredential(activity, request)

  // 3. Extract Google ID token from result
  val credential = result.credential
  val googleIdToken = (credential as GoogleIdTokenCredential).idToken

  // 4. Send to Worker backend
  val authResponse = authApi.authenticateWithGoogle(googleIdToken)

  // 5. Store session
  sessionStore.saveTokens(
    accessToken = authResponse.accessToken,
    user = authResponse.user
  )

  // 6. Emit SignedIn state
  _authState.value = AuthState.SignedIn(
    accessToken = authResponse.accessToken,
    user = authResponse.user
  )
}
```

### 3.4 Add sign-in UI

**In `MainScreen.kt`** — add a sign-in section to the Settings dialog:

- Add a toggle-able section "Cloud Sync" in the settings dialog
- If `AuthState.SignedOut`: show "Sign in with Google" button
- If `AuthState.SigningIn`: show progress indicator
- If `AuthState.SignedIn`: show user email/name + "Sign out" button + sync status

The button uses `rememberLauncherForActivityResult` with Credential Manager internally, or `AuthManager` handles it and the UI observes the `authState` flow.

### 3.5 Wire into ViewModel

**`MainScreenViewModel.kt`** addition:

```kotlin
// Receive AuthManager via constructor or factory
val authState: StateFlow<AuthState> = authManager.authState

fun signIn() {
  viewModelScope.launch {
    authManager.signIn()
  }
}

fun signOut() {
  viewModelScope.launch {
    authManager.signOut()
  }
}
```

### 3.6 Test end-to-end

1. Deploy Worker (`bunx wrangler deploy`)
2. Run Android app in debug
3. Open Settings → tap "Sign in with Google"
4. Account picker appears → select account
5. App sends ID token to Worker → Worker returns session
6. Settings shows "Signed in as you@gmail.com"

### Resources

- [Android Credential Manager — Google Sign-In](https://developer.android.com/training/sign-in/credential-manager)
- [Google Identity — ID Token on Android](https://developers.google.com/identity/sign-in/android/credential-manager)
- [OkHttp (for AuthApi)](https://square.github.io/okhttp/) or use `HttpURLConnection` / Ktor / Retrofit
- [DataStore documentation](https://developer.android.com/topic/libraries/architecture/datastore)

---

## Appendix A: Worker `src/index.ts` (full)

```typescript
import { jwtVerify, createRemoteJWKSet } from 'jose';

interface Env {
  DB: D1Database;
  SESSIONS: KVNamespace;
  GOOGLE_WEB_CLIENT_ID: string;
}

const JWKS = createRemoteJWKSet(
  new URL('https://www.googleapis.com/oauth2/v3/certs')
);

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      if (url.pathname === '/auth/google' && request.method === 'POST') {
        return await handleGoogleAuth(request, env, corsHeaders);
      }
      if (url.pathname === '/auth/refresh' && request.method === 'POST') {
        return await handleRefresh(request, env, corsHeaders);
      }
      if (url.pathname === '/auth/me' && request.method === 'GET') {
        return await handleMe(request, env, corsHeaders);
      }
      return new Response('Not Found', { status: 404, headers: corsHeaders });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Internal error';
      return new Response(JSON.stringify({ error: message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
  },
};

async function handleGoogleAuth(
  request: Request,
  env: Env,
  cors: Record<string, string>
): Promise<Response> {
  const { idToken } = await request.json<{ idToken: string }>();

  const { payload } = await jwtVerify(idToken, JWKS, {
    issuer: 'https://accounts.google.com',
    audience: env.GOOGLE_WEB_CLIENT_ID,
  });

  const googleSub = payload.sub as string;
  const email = payload.email as string;
  const name = payload.name as string;
  const picture = payload.picture as string | undefined;

  // Upsert user
  const existing = await env.DB.prepare(
    'SELECT id FROM users WHERE google_sub = ?'
  ).bind(googleSub).first<{ id: string }>();

  let userId: string;
  if (existing) {
    userId = existing.id;
    await env.DB.prepare(
      'UPDATE users SET email = ?, name = ?, picture = ?, updated_at = unixepoch() WHERE id = ?'
    ).bind(email, name, picture ?? null, userId).run();
  } else {
    userId = crypto.randomUUID();
    await env.DB.prepare(
      'INSERT INTO users (id, google_sub, email, name, picture, created_at, updated_at) VALUES (?, ?, ?, ?, ?, unixepoch(), unixepoch())'
    ).bind(userId, googleSub, email, name, picture ?? null).run();
  }

  // Create session
  const accessToken = crypto.randomUUID();
  const refreshToken = crypto.randomUUID();
  const session = { userId, createdAt: Date.now() };

  await env.SESSIONS.put(accessToken, JSON.stringify(session), {
    expirationTtl: 86400 * 7, // 7 days
  });
  await env.SESSIONS.put(refreshToken, JSON.stringify({ userId, accessToken }), {
    expirationTtl: 86400 * 30, // 30 days
  });

  return new Response(
    JSON.stringify({
      accessToken,
      refreshToken,
      user: { id: userId, email, name, picture: picture ?? null },
    }),
    { headers: { ...cors, 'Content-Type': 'application/json' } }
  );
}

async function handleRefresh(
  request: Request,
  env: Env,
  cors: Record<string, string>
): Promise<Response> {
  const { refreshToken } = await request.json<{ refreshToken: string }>();

  const session = await env.SESSIONS.get(refreshToken);
  if (!session) {
    return new Response(JSON.stringify({ error: 'Invalid refresh token' }), {
      status: 401,
      headers: { ...cors, 'Content-Type': 'application/json' },
    });
  }

  const { userId } = JSON.parse(session);
  await env.SESSIONS.delete(refreshToken);

  const newAccessToken = crypto.randomUUID();
  const newRefreshToken = crypto.randomUUID();

  await env.SESSIONS.put(newAccessToken, JSON.stringify({ userId, createdAt: Date.now() }), {
    expirationTtl: 86400 * 7,
  });
  await env.SESSIONS.put(newRefreshToken, JSON.stringify({ userId, accessToken: newAccessToken }), {
    expirationTtl: 86400 * 30,
  });

  return new Response(
    JSON.stringify({ accessToken: newAccessToken, refreshToken: newRefreshToken }),
    { headers: { ...cors, 'Content-Type': 'application/json' } }
  );
}

async function handleMe(
  request: Request,
  env: Env,
  cors: Record<string, string>
): Promise<Response> {
  const auth = request.headers.get('Authorization');
  if (!auth?.startsWith('Bearer ')) {
    return new Response(JSON.stringify({ error: 'Missing token' }), {
      status: 401,
      headers: { ...cors, 'Content-Type': 'application/json' },
    });
  }

  const accessToken = auth.slice(7);
  const session = await env.SESSIONS.get(accessToken);
  if (!session) {
    return new Response(JSON.stringify({ error: 'Invalid or expired token' }), {
      status: 401,
      headers: { ...cors, 'Content-Type': 'application/json' },
    });
  }

  const { userId } = JSON.parse(session);
  const user = await env.DB.prepare(
    'SELECT id, email, name, picture FROM users WHERE id = ?'
  ).bind(userId).first();

  if (!user) {
    return new Response(JSON.stringify({ error: 'User not found' }), {
      status: 404,
      headers: { ...cors, 'Content-Type': 'application/json' },
    });
  }

  return new Response(JSON.stringify(user), {
    headers: { ...cors, 'Content-Type': 'application/json' },
  });
}
```

## Appendix B: `wrangler.toml` reference

```toml
name = "chobi-auth"
main = "src/index.ts"
compatibility_date = "2026-07-07"

[[d1_databases]]
binding = "DB"
database_name = "chobi-auth"
database_id = "<from-wrangler-d1-create>"

[[kv_namespaces]]
binding = "SESSIONS"
id = "<from-wrangler-kv-create>"

[vars]
GOOGLE_WEB_CLIENT_ID = "<set-as-secret-instead>"
```

## Appendix C: Key architectural decisions

| Decision | Choice | Rationale |
|---|---|---|
| ID strategy | UUID (v4) for user IDs | Client-generated, no auto-increment conflicts during sync |
| Session storage | Cloudflare KV | Fast reads, built-in TTL, cheap. Sessions are read-heavy, write-rare |
| User database | D1 (SQLite) | Same SQL semantics as Room, relational queries for sync later |
| Token format | Opaque UUID (not JWT) | Simpler — no signing/verification needed. KV lookup is fast enough. JWT would add complexity without benefit for first-party auth |
| Token expiry | 7 days access, 30 days refresh | Good balance of security and UX. Refresh flow is seamless |
| Android auth | Credential Manager (not browser-based) | Native system picker, no redirects, familiar UX |

## Appendix D: Sequence diagram (auth flow)

```
User            Android App          Credential Manager     Cloudflare Worker     Google
 │                  │                       │                     │                  │
 │  tap Sign In     │                       │                     │                  │
 │─────────────────►│                       │                     │                  │
 │                  │  GetGoogleIdOption    │                     │                  │
 │                  │──────────────────────►│                     │                  │
 │                  │  account picker       │                     │                  │
 │  pick account    │◄──────────────────────│                     │                  │
 │◄─────────────────│                       │                     │                  │
 │                  │  return ID token      │                     │                  │
 │                  │◄──────────────────────│                     │                  │
 │                  │                       │                     │                  │
 │                  │  POST /auth/google    │                     │                  │
 │                  │  { idToken }          │────────────────────►│                  │
 │                  │                       │                     │  fetch JWKS      │
 │                  │                       │                     │─────────────────►│
 │                  │                       │                     │◄─────────────────│
 │                  │                       │                     │  verify JWT      │
 │                  │                       │                     │  upsert user D1  │
 │                  │                       │                     │  create session  │
 │                  │  { accessToken,       │                     │                  │
 │                  │    refreshToken,      │◄────────────────────│                  │
 │                  │    user }             │                     │                  │
 │                  │                       │                     │                  │
 │  "Signed in"     │                       │                     │                  │
 │◄─────────────────│                       │                     │                  │
```
