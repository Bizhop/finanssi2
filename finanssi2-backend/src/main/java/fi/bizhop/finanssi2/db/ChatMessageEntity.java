package fi.bizhop.finanssi2.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import fi.bizhop.finanssi2.game.db.GameEntity;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "chat_messages", indexes = @Index(name = "chat_messages_game_id_id_idx", columnList = "game_id, id"))
public class ChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String username;
    String name;
    @Column(nullable = false)
    String message;
    @Column(nullable = false)
    long timestamp;
    String photoUrl;
    @ManyToOne
    @JoinColumn(name = "game_id", foreignKey = @jakarta.persistence.ForeignKey(name = "chat_messages_game_id_fkey"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    GameEntity game;
}
