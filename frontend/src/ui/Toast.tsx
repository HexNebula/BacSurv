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

/**
 * An action carried by a toast: one press, offered rather than demanded.
 *
 * <p>Some changes leave the distribution out of date, and the administrator
 * should be able to relaunch it from where they are instead of navigating to
 * find the button. A modal would ask the same thing by taking the screen away
 * — and after the second rule changed in a row it would be dismissed without
 * being read.
 */
export type ToastAction = { label: string; run: () => void }

type Note = { id: number; tone: Tone; text: string; action?: ToastAction }

let queue: Note[] = []
let nextId = 1
const listeners = new Set<() => void>()

function publish() {
  listeners.forEach((listener) => listener())
}

function push(tone: Tone, text: string, action?: ToastAction) {
  queue = [...queue, { id: nextId++, tone, text, action }]
  publish()
}

function drop(id: number) {
  queue = queue.filter((note) => note.id !== id)
  publish()
}

/** Called from anywhere, including outside React — `lib/mutation.ts` uses it. */
export const toast = {
  ok: (text: string, action?: ToastAction) => push('ok', text, action),
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
      // one carrying an action is given time to be pressed
      const life = note.tone === 'bad' ? 8000 : note.action ? 9000 : 4000
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
              <div className="min-w-0 flex-1">
                <p className="text-[13px] leading-relaxed">{note.text}</p>
                {note.action && (
                  <button
                    type="button"
                    onClick={() => {
                      note.action?.run()
                      close(note.id)
                    }}
                    className="mt-2 rounded-[var(--radius-field)] bg-[var(--color-accent)] px-3 py-1.5
                      text-[12.5px] font-medium text-[var(--color-surface)] transition-opacity hover:opacity-90"
                  >
                    {note.action.label}
                  </button>
                )}
              </div>
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
