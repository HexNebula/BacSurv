import { useTranslation } from 'react-i18next'

/** Anything the administration calls by two names. */
export type Named = { name: string; nameFr?: string | null }

/**
 * Which of a record's two names to lead with.
 *
 * <p>The lists a centre works from arrive in Arabic, so `name` is the Arabic
 * one and it is always there; `nameFr` is a label somebody typed for the
 * documents written in French, and it is often missing. The server keeps them
 * apart on purpose — only `name` is ever compared — and this is the screen's
 * half of that: it chooses what to print and never invents the other.
 *
 * <p>A French interface leads with the French name when there is one and falls
 * back to the Arabic without apology; an Arabic interface always leads with the
 * Arabic. The other name follows quietly where a row has room for it, because
 * an administrator reading a French list still has to find the person on the
 * ministry's Arabic one.
 */
export function useNames() {
  const { i18n } = useTranslation()
  const french = i18n.language.startsWith('fr')

  /** What the row leads with. */
  const label = (one: Named | undefined) => {
    if (!one) return ''
    return french && one.nameFr ? one.nameFr : one.name
  }

  /** The same thing said the other way, or nothing when it adds nothing. */
  const second = (one: Named | undefined) => {
    if (!one) return null
    const other = french ? one.name : one.nameFr
    return other && other !== label(one) ? other : null
  }

  return { label, second, french }
}
