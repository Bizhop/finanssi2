package fi.bizhop.finanssi2.game.db;

import fi.bizhop.finanssi2.game.engine.BondContinuation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;

@Configuration
public class GameMongoConfig {
    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new LegacyBondContinuationReader()));
    }

    /** Decisions saved before the continuation became an enum have an integer field. */
    @ReadingConverter
    static class LegacyBondContinuationReader implements Converter<Integer, BondContinuation> {
        @Override
        public BondContinuation convert(Integer source) {
            return BondContinuation.fromCode(source);
        }
    }
}
