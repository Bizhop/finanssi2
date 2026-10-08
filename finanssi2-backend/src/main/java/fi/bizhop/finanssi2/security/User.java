package fi.bizhop.finanssi2.security;

import com.google.firebase.auth.FirebaseToken;

public record User(String uid, String email, String name, String photoUrl, boolean emailVerified, String firebaseUid) {
    public User(String uid, String email, String name, String photoUrl, boolean emailVerified) {
        this(uid, email, name, photoUrl, emailVerified, uid);
    }
    public User(String uid, String email, String name, String photoUrl) {
        this(uid, email, name, photoUrl, false, uid);
    }

    public static User fromToken(FirebaseToken token) {
        return new User(token.getUid(), token.getEmail(), token.getName(), token.getPicture(),
                token.isEmailVerified());
    }
}
