import { useEffect, useRef } from 'react'

// Animated dot-matrix background. Dot size follows an offscreen "relief" image
// (optional themed shapes: a coin and a receipt, drawn once and blurred) plus slow
// layered waves and a ripple under the pointer. Pure canvas, no assets.
// Respects prefers-reduced-motion (renders one static frame).
export function DotField({ className = '', density = 14, intensity = 1, themed = false }: { className?: string; density?: number; intensity?: number; themed?: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current!
    const ctx = canvas.getContext('2d')!
    let raf = 0,
      w = 0,
      h = 0,
      dpr = 1
    let relief: Uint8ClampedArray | null = null
    let rw = 0,
      rh = 0
    const mouse = { x: -9999, y: -9999 }
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const isDark = () => document.documentElement.classList.contains('dark')

    // Draws soft white shapes on black at 1/4 resolution; brightness later drives dot size.
    const buildRelief = () => {
      if (!themed) return
      rw = Math.max(1, Math.round(w / 4))
      rh = Math.max(1, Math.round(h / 4))
      const off = document.createElement('canvas')
      off.width = rw
      off.height = rh
      const o = off.getContext('2d')!
      o.fillStyle = '#000'
      o.fillRect(0, 0, rw, rh)
      o.filter = 'blur(6px)'
      o.strokeStyle = '#fff'
      o.fillStyle = '#fff'
      o.lineCap = 'round'
      o.lineJoin = 'round'
      // big coin, left
      const cx = rw * 0.22,
        cy = rh * 0.62,
        cr = Math.min(rw, rh) * 0.26
      o.lineWidth = cr * 0.28
      o.beginPath()
      o.arc(cx, cy, cr, 0, Math.PI * 2)
      o.stroke()
      o.lineWidth = cr * 0.16
      o.beginPath()
      o.arc(cx, cy, cr * 0.62, 0, Math.PI * 2)
      o.stroke()
      o.beginPath()
      o.moveTo(cx - cr * 0.3, cy)
      o.lineTo(cx + cr * 0.3, cy)
      o.moveTo(cx, cy - cr * 0.3)
      o.lineTo(cx, cy + cr * 0.3)
      o.stroke()
      // receipt, right, slightly tilted
      const px = rw * 0.8,
        py = rh * 0.42,
        pw = Math.min(rw, rh) * 0.36,
        ph = pw * 1.35
      o.save()
      o.translate(px, py)
      o.rotate(-0.18)
      o.lineWidth = pw * 0.1
      o.beginPath()
      o.moveTo(-pw / 2, -ph / 2)
      o.lineTo(pw / 2, -ph / 2)
      o.lineTo(pw / 2, ph / 2)
      for (let i = 0; i < 6; i++) o.lineTo(pw / 2 - (pw / 6) * (i + 0.5), ph / 2 - (i % 2 ? 0 : pw * 0.08))
      o.lineTo(-pw / 2, ph / 2)
      o.closePath()
      o.stroke()
      o.lineWidth = pw * 0.07
      for (let i = 0; i < 3; i++) {
        o.beginPath()
        o.moveTo(-pw * 0.3, -ph * 0.28 + i * ph * 0.17)
        o.lineTo(pw * (i === 2 ? 0.05 : 0.3), -ph * 0.28 + i * ph * 0.17)
        o.stroke()
      }
      o.beginPath()
      o.moveTo(-pw * 0.25, ph * 0.22)
      o.lineTo(-pw * 0.08, ph * 0.36)
      o.lineTo(pw * 0.3, ph * 0.05)
      o.stroke()
      o.restore()
      relief = o.getImageData(0, 0, rw, rh).data
    }

    const resize = () => {
      dpr = Math.min(2, window.devicePixelRatio || 1)
      w = canvas.clientWidth
      h = canvas.clientHeight
      canvas.width = w * dpr
      canvas.height = h * dpr
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
      buildRelief()
    }

    const draw = (t: number) => {
      ctx.clearRect(0, 0, w, h)
      const dark = isDark()
      const s = t * 0.00022
      const cols = Math.ceil(w / density) + 1,
        rows = Math.ceil(h / density) + 1
      for (let i = 0; i < cols; i++) {
        for (let j = 0; j < rows; j++) {
          const x = i * density,
            y = j * density
          const nx = x / w,
            ny = y / h
          let v = Math.sin(nx * 5.5 + s * 1.8) * 0.55 + Math.sin(ny * 7 - s * 1.3 + nx * 2.5) * 0.45 + Math.sin((nx * 1.7 + ny) * 11 + s * 0.7) * 0.3 + Math.cos(nx * 13 - ny * 9 - s) * 0.2
          if (relief) {
            const ix = Math.min(rw - 1, Math.round(nx * (rw - 1))),
              iy = Math.min(rh - 1, Math.round(ny * (rh - 1)))
            // shapes dominate where present, waves fill the rest more quietly
            v = v * 0.35 + (relief[(iy * rw + ix) * 4] / 255) * 2.2 * (0.85 + 0.15 * Math.sin(s * 3 + nx * 4))
          }
          const dx = x - mouse.x,
            dy = y - mouse.y,
            d = Math.sqrt(dx * dx + dy * dy)
          if (d < 240) v += (1 - d / 240) * 1.4
          // clear an ellipse around the centre where the content sits
          const ex = (nx - 0.5) / 0.38,
            ey = (ny - 0.5) / 0.46
          const mask = Math.min(1, Math.max(0, Math.sqrt(ex * ex + ey * ey) - 0.75) * 1.6)
          const r = Math.max(0, v - 0.1) * density * 0.3 * intensity * mask
          if (r < 0.35) continue
          const a = Math.min(1, r / (density * 0.3)) * (dark ? 0.85 : 0.5)
          ctx.fillStyle = dark ? `rgba(243,243,243,${a})` : `rgba(16,16,16,${a})`
          ctx.beginPath()
          ctx.arc(x, y, Math.min(r, density * 0.42), 0, Math.PI * 2)
          ctx.fill()
        }
      }
    }

    const loop = (t: number) => {
      draw(t)
      raf = requestAnimationFrame(loop)
    }
    const onMove = (e: PointerEvent) => {
      const b = canvas.getBoundingClientRect()
      mouse.x = e.clientX - b.left
      mouse.y = e.clientY - b.top
    }
    const onLeave = () => {
      mouse.x = -9999
      mouse.y = -9999
    }

    resize()
    window.addEventListener('resize', resize)
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerleave', onLeave)
    if (reduce) draw(0)
    else raf = requestAnimationFrame(loop)
    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('resize', resize)
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerleave', onLeave)
    }
  }, [density, intensity, themed])

  return <canvas ref={ref} aria-hidden className={`pointer-events-none ${className}`} />
}
