import { useEffect, useRef } from 'react'

// Night sky behind the sign-in page: three depth layers of twinkling stars that drift
// slowly and shift a little with the pointer, plus a rare shooting star. A few stars
// are tinted compass gold. In the light theme the stars become faint graphite dots.
// Respects prefers-reduced-motion (one static frame).
interface Star { x: number; y: number; r: number; depth: number; phase: number; speed: number; gold: boolean }
interface Meteor { x: number; y: number; vx: number; vy: number; life: number }

export function Starfield({ className = '' }: { className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current!
    const ctx = canvas.getContext('2d')!
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let w = 0, h = 0, raf = 0
    let stars: Star[] = []
    let meteor: Meteor | null = null
    let nextMeteor = 3000
    const pointer = { x: 0, y: 0, tx: 0, ty: 0 }

    const resize = () => {
      const dpr = Math.min(2, window.devicePixelRatio || 1)
      w = canvas.clientWidth
      h = canvas.clientHeight
      canvas.width = w * dpr
      canvas.height = h * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      const count = Math.round((w * h) / 2600)
      stars = Array.from({ length: count }, () => {
        const depth = Math.random() < 0.6 ? 1 : Math.random() < 0.7 ? 2 : 3
        return { x: Math.random() * w, y: Math.random() * h, r: 0.35 + depth * 0.35 + Math.random() * 0.4, depth, phase: Math.random() * Math.PI * 2, speed: 0.5 + Math.random() * 1.5, gold: Math.random() < 0.06 }
      })
    }

    const draw = (time: number, dt: number) => {
      const dark = document.documentElement.classList.contains('dark')
      ctx.clearRect(0, 0, w, h)
      pointer.x += (pointer.tx - pointer.x) * 0.04
      pointer.y += (pointer.ty - pointer.y) * 0.04
      for (const s of stars) {
        // slow drift to the left; nearer layers move faster and react more to the pointer
        s.x -= dt * 0.004 * s.depth
        if (s.x < -2) s.x = w + 2
        const px = s.x + pointer.x * s.depth * 6
        const py = s.y + pointer.y * s.depth * 6
        const tw = 0.55 + 0.45 * Math.sin(time * 0.001 * s.speed + s.phase)
        const a = (dark ? 0.35 + 0.65 * tw : 0.12 + 0.25 * tw) * (s.depth === 1 ? 0.6 : 1)
        ctx.fillStyle = s.gold ? `rgba(196,178,140,${a})` : dark ? `rgba(243,243,243,${a})` : `rgba(16,16,16,${a})`
        ctx.beginPath()
        ctx.arc(px, py, s.r, 0, Math.PI * 2)
        ctx.fill()
        if (s.depth === 3 && tw > 0.92 && dark) {
          // short cross flare on the brightest moment of a near star
          ctx.strokeStyle = `rgba(243,243,243,${(tw - 0.92) * 6})`
          ctx.lineWidth = 0.6
          ctx.beginPath()
          ctx.moveTo(px - 4, py)
          ctx.lineTo(px + 4, py)
          ctx.moveTo(px, py - 4)
          ctx.lineTo(px, py + 4)
          ctx.stroke()
        }
      }
      nextMeteor -= dt
      if (!meteor && nextMeteor <= 0) {
        meteor = { x: Math.random() * w * 0.7 + w * 0.3, y: Math.random() * h * 0.3, vx: -0.9, vy: 0.45, life: 1 }
        nextMeteor = 6000 + Math.random() * 9000
      }
      if (meteor) {
        meteor.x += meteor.vx * dt
        meteor.y += meteor.vy * dt
        meteor.life -= dt / 1100
        if (meteor.life <= 0) meteor = null
        else {
          const g = ctx.createLinearGradient(meteor.x, meteor.y, meteor.x - meteor.vx * 140, meteor.y - meteor.vy * 140)
          const c = dark ? '243,243,243' : '16,16,16'
          g.addColorStop(0, `rgba(${c},${0.9 * meteor.life})`)
          g.addColorStop(1, `rgba(${c},0)`)
          ctx.strokeStyle = g
          ctx.lineWidth = 1.2
          ctx.beginPath()
          ctx.moveTo(meteor.x, meteor.y)
          ctx.lineTo(meteor.x - meteor.vx * 140, meteor.y - meteor.vy * 140)
          ctx.stroke()
        }
      }
    }

    let last = performance.now()
    const loop = (time: number) => {
      const dt = Math.min(64, time - last)
      last = time
      draw(time, dt)
      raf = requestAnimationFrame(loop)
    }
    const onMove = (e: PointerEvent) => {
      pointer.tx = e.clientX / window.innerWidth - 0.5
      pointer.ty = e.clientY / window.innerHeight - 0.5
    }

    resize()
    window.addEventListener('resize', resize)
    window.addEventListener('pointermove', onMove)
    if (reduce) draw(0, 0)
    else raf = requestAnimationFrame(loop)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', resize)
      window.removeEventListener('pointermove', onMove)
    }
  }, [])

  return <canvas ref={ref} aria-hidden className={`pointer-events-none ${className}`} />
}

// A thin planet arc with an orbiting moon, drawn in hairlines like the rest of the site.
export function Planet({ className = '' }: { className?: string }) {
  return (
    <div aria-hidden className={`pointer-events-none absolute ${className}`}>
      <svg viewBox="0 0 600 600" className="w-full h-full text-ink" fill="none">
        <circle cx="300" cy="300" r="210" stroke="currentColor" strokeOpacity="0.18" strokeWidth="1" />
        <circle cx="300" cy="300" r="210" stroke="currentColor" strokeOpacity="0.07" strokeWidth="40" />
        {Array.from({ length: 9 }, (_, i) => (
          <ellipse key={i} cx="300" cy="300" rx="210" ry={24 + i * 22} stroke="currentColor" strokeOpacity={0.05 + (i % 3) * 0.02} strokeWidth="1" />
        ))}
        <ellipse cx="300" cy="300" rx="290" ry="70" stroke="rgb(var(--accent2))" strokeOpacity="0.55" strokeWidth="1.2" transform="rotate(-18 300 300)" />
        <g className="origin-center" style={{ animation: 'orbit 28s linear infinite', transformOrigin: '300px 300px' }}>
          <circle cx="590" cy="300" r="7" fill="rgb(var(--accent2))" fillOpacity="0.8" transform="rotate(-18 300 300)" />
        </g>
      </svg>
    </div>
  )
}
