import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { CircleCheck, TriangleAlert, X } from 'lucide-react'

/**
 * Toasts carry one kind of thing: something the administrator just pressed was
 * refused, or just worked. A schedule that cannot exist is not announced here —
 * that is a standing fact about the session and belongs on the session's own
 * screen, where it stays until it is fixed rather than fading after five
 * seconds.
 *
 * <p>Written here rather than taken from a library: the whole need is a list,
 * a timer and an animation, and the one thing that must be right — settling on
 * the side the page reads towards — is a logical property, not a package.
 */
export type Tone = 'ok' | 'bad'

type Note = { id: number; tone: Tone; text: string }

let queue: Note[] = []
let nextId = 1
const listeners = new Set<() => void>()

function publish() {
  listeners.forEach((listener) => listener())
}

function push(tone: Tone, text: string) {
  queue = [...queue, { id: nextId++, tone, text }]
  publish()
}

function drop(id: number) {
  queue = queue.filter((note) => note.id !== id)
  publish()
}

/** Called from anywhere, including outside React — `lib/mutation.ts` uses it. */
export const toast = {
  ok: (text: string) => push('ok', text),
  bad: (text: string) => push('bad', text),
}

export function Toaster() {
  const [notes, setNotes] = useState<Note[]>(queue)
  const timers = useRef(new Map<number, number>())

  useEffect(() => {
    const listener = () => setNotes(queue)
    listeners.add(listener)
    return () => void listeners.delete(listener)
  }, [])

  // a refusal is a sentence to read, so it is given longer than a confirmation
  useEffect(() => {
    for (const note of notes) {
      if (timers.current.has(note.id)) continue
      const life = note.tone === 'bad' ? 8000 : 4000
      timers.current.set(
        note.id,
        window.setTimeout(() => {
          timers.current.delete(note.id)
          drop(note.id)
        }, life),
      )
    }
  }, [notes])

  const close = useCallback((id: number) => {
    const timer = timers.current.get(id)
    if (timer) window.clearTimeout(timer)
    timers.current.delete(id)
    drop(id)
  }, [])

  const tones = useMemo(
    () => ({
      ok: {
        ring: 'ring-[var(--color-good)]/25',
        mark: 'text-[var(--color-good)]',
        Icon: CircleCheck,
      },
      bad: {
        ring: 'ring-[var(--color-alarm)]/30',
        mark: 'text-[var(--color-alarm)]',
        Icon: TriangleAlert,
      },
    }),
    [],
  )

  return createPortal(
    /* `end`, not `right`: it settles on the side the page reads towards */
    <div className="no-print pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-end gap-2 p-5 ltr:items-end rtl:items-start">
      <AnimatePresence initial={false}>
        {notes.map((note) => {
          const { ring, mark, Icon } = tones[note.tone]
          return (
            <motion.div
              key={note.id}
              layout
              initial={{ opacity: 0, y: 12, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, scale: 0.97, transition: { duration: 0.15 } }}
              transition={{ type: 'spring', stiffness: 420, damping: 32 }}
              role={note.tone === 'bad' ? 'alert' : 'status'}
              className={`pointer-events-auto flex w-[min(26rem,calc(100vw-2.5rem))] items-start gap-3 rounded-[var(--radius-card)]
                bg-[var(--color-surface)] px-4 py-3.5 shadow-[var(--shadow-raised)] ring-1 ${ring}`}
            >
              <Icon size={17} className={`mt-px shrink-0 ${mark}`} aria-hidden />
              <p className="min-w-0 flex-1 text-[13px] leading-relaxed">{note.text}</p>
              <button
                type="button"
                onClick={() => close(note.id)}
                className="-me-1 shrink-0 rounded-md p-1 text-[var(--color-faint)] transition-colors hover:bg-[var(--color-sunken)] hover:text-[var(--color-ink)]"
              >
                <X size={14} aria-hidden />
              </button>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>,
    document.body,
  )
}
