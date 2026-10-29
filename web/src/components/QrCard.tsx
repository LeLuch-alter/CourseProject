import { useEffect, useRef, useState } from 'react'
import { Copy, Check } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import QRCode from 'qrcode'
import { useTheme } from '../lib/theme'

// QR with a link that opens the site and sends a friend request to this user.
export function QrCard({ username, size = 168 }: { username: string; size?: number }) {
  const ref = useRef<HTMLCanvasElement>(null)
  const { resolved } = useTheme()
  const { t } = useI18n()
  const [copied, setCopied] = useState(false)
  const url = `${window.location.origin}/add/${username}`

  useEffect(() => {
    if (!ref.current) return
    void QRCode.toCanvas(ref.current, url, {
      width: size,
      margin: 1,
      color: { dark: resolved === 'dark' ? '#f3f3f3' : '#101010', light: '#00000000' },
    })
  }, [url, size, resolved])

  return (
    <div className="flex flex-col items-center gap-2">
      <canvas ref={ref} width={size} height={size} className="rounded-lg" />
      <button
        type="button"
        className="btn-ghost h-8 px-3 text-xs border border-line"
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(url)
            setCopied(true)
            setTimeout(() => setCopied(false), 1500)
          } catch {
            /* clipboard blocked by the browser */
          }
        }}
      >
        {copied ? <Check size={13} className="text-good" /> : <Copy size={13} />}
        {copied ? t('common.copied') : t('qr.copy')}
      </button>
    </div>
  )
}
