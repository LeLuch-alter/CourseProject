import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { UserPlus } from 'lucide-react'
import { useI18n } from '../lib/i18n'
import { useAuth } from '../lib/auth'
import { useToast } from '../lib/toast'
import { findByUsername } from '../api/extra'
import { listFriendships, sendFriendRequest } from '../api/social'
import type { Profile } from '../api/types'
import { Avatar, Empty, Skeleton } from '../components/ui'

// Opened from a QR code: /add/:username. Shows the person and sends a friend request.
export function AddFriendPage() {
  const { username = '' } = useParams()
  const { t, locale } = useI18n()
  const { user } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<Profile | null | undefined>(undefined)
  const [state, setState] = useState<'idle' | 'sent' | 'friends' | 'self'>('idle')

  useEffect(() => {
    findByUsername(username)
      .then(async (p) => {
        setProfile(p)
        if (!p) return
        if (p.id === user!.id) {
          setState('self')
          return
        }
        const fs = await listFriendships()
        const rel = fs.find((f) => f.requester_id === p.id || f.addressee_id === p.id)
        if (rel) setState(rel.status === 'ACCEPTED' ? 'friends' : 'sent')
      })
      .catch(() => setProfile(null))
  }, [username, user])

  async function add() {
    if (!profile) return
    try {
      await sendFriendRequest(profile.id)
      setState('sent')
      toast.success(t('friends.requested'))
    } catch (e) {
      toast.error(t('common.saveFailed'), e instanceof Error ? e.message : undefined)
    }
  }

  if (profile === undefined) return <Skeleton className="h-48 max-w-md mx-auto" />
  if (profile === null) return <Empty title={t('common.nothing')} />

  return (
    <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="card max-w-md mx-auto p-8 flex flex-col items-center text-center">
      <Avatar profile={profile} size={96} />
      <div className="text-xl mt-4">{profile.display_name}</div>
      <div className="font-mono text-[11px] text-faint">@{profile.username}</div>
      {profile.bio && <p className="text-sm text-muted mt-2">{profile.bio}</p>}
      <div className="text-xs text-faint mt-3">{t('friends.online', new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(new Date(profile.created_at)))}</div>
      <div className="mt-6 w-full">
        {state === 'idle' && (
          <button className="btn-primary w-full" onClick={() => void add()}>
            <UserPlus size={16} />
            {t('friends.add')}
          </button>
        )}
        {state === 'sent' && <div className="chip h-9 justify-center w-full">{t('friends.requested')}</div>}
        {state === 'friends' && (
          <button className="btn-outline w-full" onClick={() => navigate(`/friends/${profile.id}`)}>
            {t('friends.chat')}
          </button>
        )}
        {state === 'self' && <div className="text-sm text-muted">{t('qr.self')}</div>}
      </div>
    </motion.div>
  )
}
