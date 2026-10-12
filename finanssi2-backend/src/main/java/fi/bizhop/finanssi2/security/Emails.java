package fi.bizhop.finanssi2.security;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Email addresses are compared trimmed and lower-cased everywhere */
public final class Emails {
    private Emails() {}

    /** The address trimmed and lower-cased; null when there is none */
    public static @Nullable String normalize(@Nullable String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
