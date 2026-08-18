package ma.bacsurv.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * The interface exists in the two administrative languages of Morocco:
 * French and Arabic. The choice is kept in a cookie so it survives
 * navigation, and can be changed anywhere with ?lang=ar / ?lang=fr.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    public static final Locale FRENCH = Locale.forLanguageTag("fr");
    public static final Locale ARABIC = Locale.forLanguageTag("ar");
    public static final List<Locale> SUPPORTED = List.of(FRENCH, ARABIC);

    @Bean
    public LocaleResolver localeResolver() {
        // anything other than the two supported languages resolves to French,
        // so a stray ?lang= value cannot leave the pages half-translated
        CookieLocaleResolver resolver = new CookieLocaleResolver("bacsurv-lang") {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return supportedOrDefault(super.resolveLocale(request));
            }
        };
        resolver.setDefaultLocale(FRENCH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        return resolver;
    }

    static Locale supportedOrDefault(Locale locale) {
        return SUPPORTED.stream()
                .filter(supported -> locale != null
                        && supported.getLanguage().equals(locale.getLanguage()))
                .findFirst()
                .orElse(FRENCH);
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    /** Arabic is written right to left; templates use this for dir/lang. */
    public static boolean isRightToLeft(Locale locale) {
        return locale != null && ARABIC.getLanguage().equals(locale.getLanguage());
    }
}
