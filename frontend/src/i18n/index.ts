import { useSyncExternalStore } from 'react'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import fr from './fr.json'
import ar from './ar.json'

/**
 * The two administrative languages of Morocco.
 *
 * Arabic is `ar-MA`, not plain `ar`: the generic Arabic locale formats numbers
 * as ٠١٢٣, while an administration here writes 0123456789. Dates, counts and
 * matricules all go through this, so the locale tag is not cosmetic.
 */
export const LANGUAGES = {
  fr: { tag: 'fr-MA', label: 'Français', dir: 'ltr' },
  ar: { tag: 'ar-MA', label: 'العربية', dir: 'rtl' },
} as const

export type Language = keyof typeof LANGUAGES

const STORAGE_KEY = 'bacsurv-lang'

export function storedLanguage(): Language {
  const saved = localStorage.getItem(STORAGE_KEY)
  return saved === 'ar' || saved === 'fr' ? saved : 'fr'
}

const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => void listeners.delete(listener)
}

/**
 * The current language, as React state.
 *
 * <p>React Aria formats dates and numbers from the locale it is handed at
 * render time, not from {@code <html lang>}. Read once at start-up it goes
 * stale the moment somebody switches language: the page turns Arabic while a
 * date field still asks for jj/mm/aaaa. Subscribing keeps the two together
 * without a reload.
 */
export function useLanguage(): Language {
  return useSyncExternalStore(subscribe, storedLanguage, storedLanguage)
}

/** Direction and language belong on <html>: the whole page mirrors, not a div. */
export function applyLanguage(language: Language) {
  const { dir } = LANGUAGES[language]
  document.documentElement.lang = language
  document.documentElement.dir = dir
  localStorage.setItem(STORAGE_KEY, language)
  void i18n.changeLanguage(language)
  listeners.forEach((listener) => listener())
}

void i18n.use(initReactI18next).init({
  resources: { fr: { translation: fr }, ar: { translation: ar } },
  lng: storedLanguage(),
  fallbackLng: 'fr',
  interpolation: { escapeValue: false },
})

export default i18n
