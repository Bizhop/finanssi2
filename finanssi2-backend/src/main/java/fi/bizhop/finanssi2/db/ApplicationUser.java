package fi.bizhop.finanssi2.db;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "application_users", uniqueConstraints = {
        @UniqueConstraint(name = "application_users_firebase_uid_key", columnNames = "firebase_uid"),
        @UniqueConstraint(name = "application_users_email_key", columnNames = "email")
})
@SuppressWarnings("NullAway.Init") // JPA fills in the fields after the protected no-arg constructor
public class ApplicationUser {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(name = "firebase_uid", nullable = false, updatable = false)
    String firebaseUid;
    @Column(nullable = false)
    String email;
    @Column(nullable = false, length = 50)
    String displayName;
    @Nullable String providerPhotoUrl;
    @Column(columnDefinition = "text")
    @Nullable String customAvatar;
    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp Instant updatedAt;
    @Version long version;

    protected ApplicationUser() {}
    public ApplicationUser(String firebaseUid, String email, String displayName, @Nullable String providerPhotoUrl) {
        this.firebaseUid = firebaseUid; this.email = email; this.displayName = displayName;
        this.providerPhotoUrl = providerPhotoUrl;
    }
    public UUID getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public @Nullable String getProviderPhotoUrl() { return providerPhotoUrl; }
    public @Nullable String getCustomAvatar() { return customAvatar; }
    public long getVersion() { return version; }
    public void updateDisplayName(String displayName) { this.displayName = displayName; }
    public void setCustomAvatar(@Nullable String customAvatar) { this.customAvatar = customAvatar; }
    public void removeCustomAvatar() { this.customAvatar = null; }
    public @Nullable String effectiveAvatar() { return customAvatar != null ? customAvatar : providerPhotoUrl; }
    public @Nullable String avatarUrl() { return customAvatar != null ? "/api/users/" + id + "/avatar?v=" + version : providerPhotoUrl; }
    public void updateIdentity(String email, @Nullable String providerPhotoUrl) { this.email = email; if (this.providerPhotoUrl == null) this.providerPhotoUrl = providerPhotoUrl; }
}
