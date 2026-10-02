package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;

public record User(String uid, String email, String name, String photoUrl, boolean emailVerified) {
    public User(String uid, String email, String name, String photoUrl) {
        this(uid, email, name, photoUrl, false);
    }

    public static User fromToken(FirebaseToken token) {
        return new User(token.getUid(), token.getEmail(), token.getName(), token.getPicture(),
                token.isEmailVerified());
    }
}
