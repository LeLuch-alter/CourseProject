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
        try {
          window.open(await receiptUrl(path), '_blank', 'noopener')
        } catch (e) {
          toast.error(t('common.loadFailed'), e instanceof Error ? e.message : undefined)
        }
      }}
    >
      <Camera size={14} />
    </button>
  )
}
