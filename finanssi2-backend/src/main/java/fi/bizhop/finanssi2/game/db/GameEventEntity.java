package fi.bizhop.finanssi2.game.db;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_events", indexes = @Index(name = "game_events_game_seq_idx", columnList = "game_id, seq", unique = true))
public class GameEventEntity {
    @Id
    String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, referencedColumnName = "id")
    GameEntity game;
    @Column(name = "game_id", insertable = false, updatable = false, length = 36)
    String gameId;
    @Column(nullable = false)
    int seq;
    @Column(nullable = false)
    long time;
    @Column(nullable = false)
    String type;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    JsonNode event;
}
