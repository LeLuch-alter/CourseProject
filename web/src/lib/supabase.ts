import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY as string

if (!url || !key) {
  throw new Error('Set VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY in web/.env')
}
// A secret key would bypass Row Level Security; refuse to start with one.
if (/^sb_secret_|service_role/.test(key)) {
  throw new Error('Secret key detected in the web client. Use the publishable key.')
}

// After a tab sleeps in the background a request can hang without ever finishing,
// which leaves the page empty. A time limit turns that into a normal error the page can show.
const REQUEST_TIMEOUT_MS = 20_000
const fetchWithTimeout: typeof fetch = (input, init) => {
  const timeout = AbortSignal.timeout(REQUEST_TIMEOUT_MS)
  const signal = init?.signal ? AbortSignal.any([init.signal, timeout]) : timeout
  return fetch(input, { ...init, signal })
}

export const supabase = createClient(url, key, {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
  global: { fetch: fetchWithTimeout },
})
