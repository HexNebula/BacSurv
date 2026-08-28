import type { ReactNode } from 'react'
import {
  Dialog as AriaDialog,
  Heading,
  Modal,
  ModalOverlay,
} from 'react-aria-components'
import { X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'

/**
 * A dialog for the two things that genuinely need one: filling in a form, and
 * reading an import report before it is committed.
 *
 * <p>Deleting is not one of them. A confirmation belongs in the row being
 * deleted, where the thing you are about to lose is still on screen.
 */
export function Dialog({
  isOpen,
  onClose,
  title,
  subtitle,
  footer,
  width = 'md',
  children,
}: {
  isOpen: boolean
  onClose: () => void
  title: string
  subtitle?: ReactNode
  footer?: ReactNode
  width?: 'md' | 'lg'
  children: ReactNode
}) {
  const { t } = useTranslation()

  return (
    <ModalOverlay
      isOpen={isOpen}
      onOpenChange={(next) => !next && onClose()}
      isDismissable
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-[var(--color-ink)]/25 p-4 pt-[8vh] backdrop-blur-[2px]
        entering:animate-in entering:fade-in exiting:animate-out exiting:fade-out"
    >
      <Modal
        className={`w-full ${width === 'lg' ? 'max-w-2xl' : 'max-w-lg'}
          entering:animate-in entering:zoom-in-95 entering:slide-in-from-top-2 exiting:animate-out exiting:fade-out`}
      >
        <AriaDialog className="rounded-[var(--radius-card)] bg-[var(--color-surface)] shadow-[var(--shadow-raised)] outline-none">
          <header className="flex items-start justify-between gap-4 px-6 pb-4 pt-5">
            <div className="min-w-0">
              <Heading slot="title" className="text-[17px] font-semibold tracking-[-0.01em]">
                {title}
              </Heading>
              {subtitle && (
                <p className="mt-1 text-[12.5px] text-[var(--color-quiet)]">{subtitle}</p>
              )}
            </div>
            <Button variant="quiet" size="sm" isIcon bare aria-label={t('app.cancel')} onPress={onClose}>
              <X size={16} aria-hidden />
            </Button>
          </header>

          <div className="max-h-[62vh] overflow-y-auto px-6 pb-2">{children}</div>

          {footer && (
            <footer className="mt-2 flex items-center justify-end gap-2 rounded-b-[var(--radius-card)] border-t border-[var(--color-hairline)] bg-[var(--color-sunken)] px-6 py-4">
              {footer}
            </footer>
          )}
        </AriaDialog>
      </Modal>
    </ModalOverlay>
  )
}
