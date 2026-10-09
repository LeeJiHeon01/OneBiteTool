package com.example.aicook;

import com.example.aicook.measure.AmountParser;
import com.example.aicook.measure.MeasureConverter;
import com.example.aicook.reference.ReferenceData;
import com.example.aicook.reference.ReferenceRepository;
import com.example.aicook.reference.Units;
import com.example.aicook.seed.SeedSync;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Units units() {
        return Units.load();
    }

    @Bean
    public AmountParser amountParser(Units units) {
        return new AmountParser(units.vagueWords());
    }

    @Bean
    public MeasureConverter measureConverter(Units units) {
        return new MeasureConverter(units);
    }

    /**
     * Reference data is loaded once: Flyway has created the tables, the seed files are synced
     * into them, and the result is read back. A broken seed file stops the server from starting.
     */
    @Bean
    public ReferenceData referenceData(SeedSync seedSync, ReferenceRepository repository, Units units) {
        seedSync.run();
        return repository.load(units);
    }
}
