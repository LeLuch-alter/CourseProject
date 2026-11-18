import { Camera } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useToast } from '../lib/toast'
import { receiptUrl } from '../api/debts'

// Opens the private receipt photo through a short-lived signed link.
export function ReceiptLink({ path }: { path: string }) {
  const { t } = useI18n()
  const toast = useToast()
  return (
    <button
      type="button"
      className="text-accent2 hover:text-ink transition"
      title={t('receipt.open')}
      aria-label={t('receipt.open')}
      onClick={async () => {
        // Browsers (Safari above all) block window.open() after an await, so the tab is
        // opened right on the click and gets its address once the signed link is ready.
        const tab = window.open('', '_blank')
        try {
          const url = await receiptUrl(path)
          if (tab) {
            tab.opener = null
            tab.location.href = url
          } else {
            window.location.href = url
          }
        } catch (e) {
          tab?.close()
          toast.error(t('common.loadFailed'), e instanceof Error ? e.message : undefined)
        }
      }}
    >
      <Camera size={14} />
    </button>
  )
}
