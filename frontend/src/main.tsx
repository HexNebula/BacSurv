import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from 'react-aria'
import { Toast } from '@heroui/react'
import App from './App.tsx'
import './index.css'
import { LANGUAGES, applyLanguage, storedLanguage, useLanguage } from './i18n'

// the saved choice decides direction before the first paint, so an Arabic
// user never sees the page laid out left to right and then flip
applyLanguage(storedLanguage())

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // a centre's rooms and teachers change a few times a year, not a minute
      staleTime: 30_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function Root() {
  // follows the switch: read once, a date field keeps asking for jj/mm/aaaa
  // long after the rest of the page has turned Arabic
  const language = useLanguage()

  return (
    /* React Aria formats dates and numbers from this, and mirrors its
       components: ar-MA gives 0123456789 where plain ar gives ٠١٢٣ */
    <I18nProvider locale={LANGUAGES[language].tag}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
        {/*
          Toasts carry one kind of problem only: something the administrator
          just pressed was refused, and retyping makes it go away. A schedule
          that cannot exist is not announced here — that is a standing fact
          about the session and belongs on the session's own screen, where it
          stays until it is fixed rather than fading after five seconds.
        */}
        {/* "end", not "right": it settles on the side the page reads towards */}
        <Toast.Provider placement="bottom end" />
      </QueryClientProvider>
    </I18nProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
