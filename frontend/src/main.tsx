import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nProvider } from 'react-aria'
import App from './App.tsx'
import './index.css'
import { LANGUAGES, applyLanguage, storedLanguage } from './i18n'

// the saved choice decides direction before the first paint, so an Arabic
// user never sees the page laid out left to right and then flip
const language = storedLanguage()
applyLanguage(language)

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

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* React Aria formats dates and numbers from this, and mirrors its
        components: ar-MA gives 0123456789 where plain ar gives ٠١٢٣ */}
    <I18nProvider locale={LANGUAGES[language].tag}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
)
