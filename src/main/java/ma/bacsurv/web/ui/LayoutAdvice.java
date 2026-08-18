package ma.bacsurv.web.ui;

import ma.bacsurv.web.config.LocaleConfig;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/** Language and text direction, available to every page without repetition. */
@ControllerAdvice(assignableTypes = {UiController.class, CenterController.class,
        AssignmentController.class})
public class LayoutAdvice {

    @ModelAttribute("lang")
    public String lang(HttpServletRequest request) {
        return locale(request).getLanguage();
    }

    @ModelAttribute("dir")
    public String dir(HttpServletRequest request) {
        return LocaleConfig.isRightToLeft(locale(request)) ? "rtl" : "ltr";
    }

    private Locale locale(HttpServletRequest request) {
        Locale locale = RequestContextUtils.getLocale(request);
        return locale != null ? locale : LocaleConfig.FRENCH;
    }
}
