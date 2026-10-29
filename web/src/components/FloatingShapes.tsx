import { motion } from 'framer-motion'

// Slow-drifting hairline outlines (coins, receipts, a check) behind the app.
// Purely decorative: aria-hidden, pointer-events none, very low contrast.
const SHAPES = [
  { kind: 'coin', x: '8%', y: '18%', size: 120, dur: 26, delay: 0 },
  { kind: 'receipt', x: '78%', y: '12%', size: 90, dur: 32, delay: -8 },
  { kind: 'coin', x: '86%', y: '70%', size: 70, dur: 22, delay: -14 },
  { kind: 'check', x: '14%', y: '76%', size: 80, dur: 28, delay: -5 },
  { kind: 'receipt', x: '52%', y: '88%', size: 60, dur: 36, delay: -20 },
] as const

export function FloatingShapes() {
  return (
    <div aria-hidden className="pointer-events-none fixed inset-0 overflow-hidden">
      {SHAPES.map((s, i) => (
        <motion.div
          key={i}
          className="absolute text-ink/[0.08] dark:text-ink/[0.07]"
          style={{ left: s.x, top: s.y, width: s.size, height: s.size }}
          animate={{ y: [0, -28, 0, 18, 0], x: [0, 12, 0, -10, 0], rotate: [0, 4, 0, -3, 0] }}
          transition={{ duration: s.dur, delay: s.delay, repeat: Infinity, ease: 'easeInOut' }}
        >
          {s.kind === 'coin' && (
            <svg viewBox="0 0 100 100" fill="none" stroke="currentColor" strokeWidth="1.2">
              <circle cx="50" cy="50" r="46" />
              <circle cx="50" cy="50" r="34" strokeDasharray="3 5" />
              <path d="M38 50h24M50 38v24" strokeLinecap="round" />
            </svg>
          )}
          {s.kind === 'receipt' && (
            <svg viewBox="0 0 100 100" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round">
              <path d="M22 6h56v84l-7-5-7 5-7-5-7 5-7-5-7 5-7-5-7 5V6Z" />
              <path d="M34 28h32M34 42h32M34 56h20" strokeLinecap="round" />
            </svg>
          )}
          {s.kind === 'check' && (
            <svg viewBox="0 0 100 100" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="8" y="8" width="84" height="84" rx="10" />
              <path d="M30 52l14 14 28-32" />
            </svg>
          )}
        </motion.div>
      ))}
    </div>
  )
}
