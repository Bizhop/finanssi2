package fi.bizhop.finanssi2.security;

import java.util.Locale;

/** Email addresses are compared trimmed and lower-cased everywhere */
public final class Emails {
    private Emails() {}

    /** The address trimmed and lower-cased; null when there is none */
    public static String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
