package ma.bacsurv.web.service;

/**
 * A refusal that has something to name.
 *
 * <p>Most refusals are one fixed sentence — "the name of the filière is
 * required" — and travel as a bare message key. Some are only useful with the
 * particulars in them: told that rooms are taken, an administrator needs to
 * know which rooms and by whom, or they are left comparing two screens.
 *
 * <p>Extends IllegalArgumentException so every existing handler still catches
 * it; the ones that know about this type pass the arguments to the message
 * bundle, and the others degrade to the sentence without them.
 */
public class RefusedException extends IllegalArgumentException {

    private final transient String[] args;

    public RefusedException(String key, String... args) {
        super(key);
        this.args = args.clone();
    }

    public String[] args() {
        return args.clone();
    }
}
