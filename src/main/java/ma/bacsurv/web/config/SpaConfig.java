package ma.bacsurv.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The interface is a single page: the browser owns the addresses, the server
 * owns {@code /api}.
 *
 * <p>Refreshing on a deep address has to work — an administrator sends a
 * colleague a link to a session, or simply reloads — so anything that is not
 * the API and not a file is answered with the page itself, and the router
 * takes it from there. Without this, only the first address ever loads.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        // one or two segments deep, excluding anything with a dot so real
        // files — the scripts, the stylesheet — are still served as files
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}/{sub:[^\\.]*}").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}/{sub:[^\\.]*}/{leaf:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
