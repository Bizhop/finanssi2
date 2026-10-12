package fi.bizhop.finanssi2.web;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import fi.bizhop.finanssi2.db.ApplicationUserRepository;
import fi.bizhop.finanssi2.security.Emails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@RestController
@RequiredArgsConstructor
public class LinkedGoogleEmailController {
    final ApplicationUserRepository users;
    final ObjectProvider<FirebaseAuth> firebaseAuth;

    @PostMapping("/api/me/verify-linked-google-email")
    public void verifyLinkedGoogleEmail(@RequestAttribute("firebaseToken") FirebaseToken token) {
        var email = Emails.normalize(token.getEmail());
        if (email == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A verified Google email is required");
        var profile = users.findByFirebaseUid(token.getUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Account profile not found"));
        if (!email.equals(Emails.normalize(profile.getEmail())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account email does not match");

        try {
            var firebase = firebaseAuth.getObject();
            var account = firebase.getUser(token.getUid());
            var googleEmail = Arrays.stream(account.getProviderData())
                    .filter(provider -> provider.getProviderId().equals("google.com"))
                    .map(provider -> Emails.normalize(provider.getEmail()))
                    .filter(value -> value != null && value.equals(email))
                    .findFirst();
            if (googleEmail.isEmpty())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A matching Google sign-in must be linked first");
            if (!account.isEmailVerified()) firebase.updateUser(account.updateRequest().setEmailVerified(true));
        } catch (FirebaseAuthException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to verify the linked Google email", failure);
        }
    }
}
