package fi.bizhop.finanssi2.db;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_users", uniqueConstraints = {
        @UniqueConstraint(name = "application_users_firebase_uid_key", columnNames = "firebase_uid"),
        @UniqueConstraint(name = "application_users_email_key", columnNames = "email")
})
public class ApplicationUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(name = "firebase_uid", nullable = false, updatable = false)
    String firebaseUid;
    @Column(nullable = false)
    String email;
    @Column(nullable = false, length = 50)
    String displayName;
    String providerPhotoUrl;
    @Column(columnDefinition = "text")
    String customAvatar;
    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp Instant updatedAt;
    @Version long version;

    protected ApplicationUser() {}
    public ApplicationUser(String firebaseUid, String email, String displayName, String providerPhotoUrl) {
        this.firebaseUid = firebaseUid; this.email = email; this.displayName = displayName;
        this.providerPhotoUrl = providerPhotoUrl;
    }
    public UUID getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getProviderPhotoUrl() { return providerPhotoUrl; }
    public String getCustomAvatar() { return customAvatar; }
    public long getVersion() { return version; }
    public String effectiveAvatar() { return customAvatar != null ? customAvatar : providerPhotoUrl; }
    public void updateIdentity(String email, String providerPhotoUrl) { this.email = email; if (this.providerPhotoUrl == null) this.providerPhotoUrl = providerPhotoUrl; }
}
