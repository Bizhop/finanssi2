package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.GameState;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "games", indexes = @Index(name = "games_status_created_idx", columnList = "status, created_at"))
public class GameEntity {
    @Id
    UUID id;
    @Version
    Long version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    GameStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    GameMode mode;
    @Column(nullable = false)
    String creator;
    @Column(name = "created_at", nullable = false)
    long createdAt;
    @Column(name = "last_event_seq", nullable = false)
    int lastEventSeq;
    @Column(name = "save_nonce", nullable = false, length = 36)
    String saveNonce;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    JsonNode state;
}
