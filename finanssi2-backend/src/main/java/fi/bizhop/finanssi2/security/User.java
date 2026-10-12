package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;
import org.jspecify.annotations.Nullable;

public record User(String userId, @Nullable String email, @Nullable String name, @Nullable String photoUrl, boolean emailVerified, String firebaseUid) {
    public User(String uid, @Nullable String email, @Nullable String name, @Nullable String photoUrl, boolean emailVerified) {
        this(uid, email, name, photoUrl, emailVerified, uid);
    }
    public User(String uid, @Nullable String email, @Nullable String name, @Nullable String photoUrl) {
        this(uid, email, name, photoUrl, false, uid);
    }

    public static User fromToken(FirebaseToken token) {
        return new User(token.getUid(), token.getEmail(), token.getName(), token.getPicture(),
                token.isEmailVerified());
    }
}
