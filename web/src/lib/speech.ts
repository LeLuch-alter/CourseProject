// Browser speech: recognition (Chrome, Edge, Safari) and synthesis. Feature-detected,
// so on browsers without support the assistant simply stays text-only.
import type { Lang } from './i18n'

const SPEECH_LANG: Record<Lang, string> = { ru: 'ru-RU', kk: 'kk-KZ', en: 'en-US' }

interface Recognition {
  lang: string
  interimResults: boolean
  continuous: boolean
  onresult: ((e: { results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null
  onerror: ((e: { error: string }) => void) | null
  onend: (() => void) | null
  start: () => void
  stop: () => void
}

function recognitionClass(): (new () => Recognition) | null {
  const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition }
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null
}

export const canListen = () => recognitionClass() !== null
export const canSpeak = () => 'speechSynthesis' in window

// Starts listening; calls onText with the live transcript and onDone with the final text.
export function listen(lang: Lang, onText: (t: string) => void, onDone: (t: string) => void, onError: (code: string) => void): () => void {
  const Cls = recognitionClass()
  if (!Cls) {
    onError('unsupported')
    return () => undefined
  }
  const r = new Cls()
  r.lang = SPEECH_LANG[lang]
  r.interimResults = true
  r.continuous = false
  let text = ''
  r.onresult = (e) => {
    text = Array.from(e.results).map((x) => x[0].transcript).join(' ')
    onText(text)
  }
  r.onerror = (e) => onError(e.error)
  r.onend = () => onDone(text.trim())
  r.start()
  return () => r.stop()
}

export function speak(text: string, lang: Lang) {
  if (!canSpeak()) return
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text.replace(/[*_`#]/g, ''))
  u.lang = SPEECH_LANG[lang]
  const voice = window.speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith(SPEECH_LANG[lang].slice(0, 2)))
  if (voice) u.voice = voice
  window.speechSynthesis.speak(u)
}

export const stopSpeaking = () => {
  if (canSpeak()) window.speechSynthesis.cancel()
}
