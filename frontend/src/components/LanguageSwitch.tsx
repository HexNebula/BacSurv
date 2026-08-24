import { LANGUAGES, applyLanguage, useLanguage, type Language } from '../i18n'

/**
 * French and Arabic, both first class, one press apart.
 *
 * <p>It reads the same source the whole application reads, so the button and
 * React Aria's formatting can never disagree about which language is on — a
 * page turning Arabic while a date field still asks for jj/mm/aaaa was a real
 * bug here once.
 */
export function LanguageSwitch() {
  const current = useLanguage()

  return (
    <div className="no-print flex items-center gap-0.5 rounded-[var(--radius-field)] bg-[var(--color-sunken)] p-1 ring-1 ring-[var(--color-hairline)]">
      {(Object.keys(LANGUAGES) as Language[]).map((language) => (
        <button
          key={language}
          type="button"
          onClick={() => applyLanguage(language)}
          lang={language}
          className={`rounded-[7px] px-2.5 py-1.5 text-[12.5px] font-medium transition-colors ${
            current === language
              ? 'bg-[var(--color-surface)] text-[var(--color-accent-ink)] shadow-[var(--shadow-card)]'
              : 'text-[var(--color-quiet)] hover:text-[var(--color-ink)]'
          }`}
        >
          {LANGUAGES[language].label}
        </button>
      ))}
    </div>
  )
}
