import { useMemo } from 'react'
import { motion } from 'framer-motion'

// A long receipt tape running vertically behind the sign-in card. The tape scrolls
// upward in a seamless loop; on each row a debt line is "written" and ticked off.
// Hairline strokes only: chalk for paper and text, compass gold for ticks and amounts.
const ROW_H = 56
const ROWS = 14
const W = 560

export function TapeHero({ className = '' }: { className?: string }) {
  // deterministic pseudo-random widths so the server/client never disagree
  const rows = useMemo(
    () =>
      Array.from({ length: ROWS }, (_, i) => {
        const a = ((i * 7919) % 97) / 97
        const b = ((i * 104729) % 89) / 89
        return { w1: 120 + a * 140, w2: 60 + b * 90, paid: i % 3 !== 1 }
      }),
    [],
  )
  const H = ROWS * ROW_H

  return (
    <div aria-hidden className={`pointer-events-none absolute left-1/2 top-0 bottom-0 -translate-x-1/2 overflow-hidden ${className}`} style={{ width: W }}>
      {/* fade the tape in and out at the viewport edges */}
      <div className="absolute inset-0 z-10" style={{ background: 'linear-gradient(to bottom, rgb(var(--bg)) 0%, transparent 18%, transparent 82%, rgb(var(--bg)) 100%)' }} />
      <motion.div className="absolute left-0 right-0" style={{ top: 0 }} animate={{ y: [0, -H] }} transition={{ duration: 48, ease: 'linear', repeat: Infinity }}>
        {[0, 1].map((copy) => (
          <svg key={copy} width={W} height={H} viewBox={`0 0 ${W} ${H}`} fill="none" className="block text-ink">
            {/* paper edges with the classic receipt zigzag only on the very first/last copy */}
            <path d={`M60 0V${H}`} stroke="currentColor" strokeOpacity="0.28" strokeWidth="1.2" />
            <path d={`M${W - 60} 0V${H}`} stroke="currentColor" strokeOpacity="0.28" strokeWidth="1.2" />
            {/* perforation dots along the edges */}
            {Array.from({ length: Math.floor(H / 28) }, (_, i) => (
              <g key={i} stroke="currentColor" strokeOpacity="0.18">
                <circle cx="72" cy={14 + i * 28} r="1.6" />
                <circle cx={W - 72} cy={14 + i * 28} r="1.6" />
              </g>
            ))}
            {rows.map((r, i) => {
              const y = i * ROW_H + ROW_H / 2
              const delay = copy * 2 + i * 0.35
              return (
                <g key={i}>
                  <line x1="60" x2={W - 60} y1={i * ROW_H} y2={i * ROW_H} stroke="currentColor" strokeOpacity="0.1" strokeDasharray="2 6" />
                  {/* description line, written left to right */}
                  <motion.line x1="100" x2={100 + r.w1} y1={y - 8} y2={y - 8} stroke="currentColor" strokeOpacity="0.5" strokeWidth="2" strokeLinecap="round" initial={{ pathLength: 0 }} animate={{ pathLength: [0, 1, 1, 1] }} transition={{ duration: 6, times: [0, 0.15, 0.9, 1], delay, repeat: Infinity, repeatDelay: 2 }} />
                  <motion.line x1="100" x2={100 + r.w2} y1={y + 8} y2={y + 8} stroke="currentColor" strokeOpacity="0.28" strokeWidth="2" strokeLinecap="round" initial={{ pathLength: 0 }} animate={{ pathLength: [0, 1, 1, 1] }} transition={{ duration: 6, times: [0, 0.2, 0.9, 1], delay: delay + 0.2, repeat: Infinity, repeatDelay: 2 }} />
                  {/* amount block on the right */}
                  <motion.rect x={W - 190} y={y - 7} width="70" height="14" rx="2" stroke="rgb(var(--accent2))" strokeOpacity="0.8" strokeWidth="1.2" initial={{ opacity: 0 }} animate={{ opacity: [0, 1, 1, 1] }} transition={{ duration: 6, times: [0, 0.25, 0.9, 1], delay: delay + 0.4, repeat: Infinity, repeatDelay: 2 }} />
                  {/* tick for paid rows, a thin dash for open ones */}
                  {r.paid ? (
                    <motion.path d={`M${W - 100} ${y}l6 6 12-14`} stroke="rgb(var(--accent2))" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" initial={{ pathLength: 0 }} animate={{ pathLength: [0, 0, 1, 1] }} transition={{ duration: 6, times: [0, 0.45, 0.6, 1], delay, repeat: Infinity, repeatDelay: 2 }} />
                  ) : (
                    <motion.line x1={W - 100} x2={W - 82} y1={y} y2={y} stroke="currentColor" strokeOpacity="0.3" strokeWidth="2" strokeLinecap="round" initial={{ opacity: 0 }} animate={{ opacity: [0, 1, 1, 1] }} transition={{ duration: 6, times: [0, 0.5, 0.9, 1], delay, repeat: Infinity, repeatDelay: 2 }} />
                  )}
                </g>
              )
            })}
          </svg>
        ))}
      </motion.div>
    </div>
  )
}
