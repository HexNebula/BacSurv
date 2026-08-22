package ma.bacsurv.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * The two administrative languages of Morocco: French and Arabic.
 *
 * <p>The interface keeps the choice and states it on every request through
 * {@code Accept-Language}. It used to live in a cookie the server set, which
 * made sense while the server rendered the pages; now that it does not, the
 * language belongs to the caller. Anything else resolves to French rather
 * than to a browser's own preference, so a machine set to English does not
 * produce sentences in no supported language at all.
 */
@Configuration
public class LocaleConfig {

    public static final Locale FRENCH = Locale.forLanguageTag("fr");
    /**
     * Moroccan Arabic, not Arabic in general: the Maghreb writes numbers with
     * the digits 0-9, while plain {@code ar} formats them as ٠-٩. The bundle
     * stays {@code messages_ar.properties} — ar-MA falls back to it.
     */
    public static final Locale ARABIC = Locale.forLanguageTag("ar-MA");
    public static final List<Locale> SUPPORTED = List.of(FRENCH, ARABIC);

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
                // The header is read here rather than through the default
                // resolution: that matches the exact tag first and falls back
                // to French before a language-level comparison ever happens,
                // so a caller asking for "ar" — the natural thing to send —
                // would be answered in French while "ar-MA" worked.
                return java.util.Collections.list(request.getLocales()).stream()
                        .map(LocaleConfig::supported)
                        .flatMap(java.util.Optional::stream)
                        .findFirst()
                        .orElse(FRENCH);
            }
        };
        resolver.setSupportedLocales(SUPPORTED);
        resolver.setDefaultLocale(FRENCH);
        return resolver;
    }

    /** Matched by language, so {@code ar} and {@code ar-MA} both mean Arabic. */
    static java.util.Optional<Locale> supported(Locale requested) {
        return SUPPORTED.stream()
                .filter(supported -> requested != null
                        && supported.getLanguage().equals(requested.getLanguage()))
                .findFirst();
    }

    static Locale supportedOrDefault(Locale locale) {
        return supported(locale).orElse(FRENCH);
    }

    /** Arabic is written right to left. */
    public static boolean isRightToLeft(Locale locale) {
        return locale != null && ARABIC.getLanguage().equals(locale.getLanguage());
    }
}
