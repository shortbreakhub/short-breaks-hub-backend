CREATE TABLE itinerary_translation (
                                       id BIGSERIAL PRIMARY KEY,

                                       itinerary_id BIGINT NOT NULL,

                                       locale VARCHAR(10) NOT NULL,

                                       title VARCHAR(500) NOT NULL,

                                       summary TEXT,

                                       price_note TEXT,

                                       highlights JSONB NOT NULL,

                                       schedule JSONB NOT NULL,

                                       planning JSONB NOT NULL,

                                       transport JSONB NOT NULL,

                                       food_recommendation JSONB NOT NULL,

                                       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       CONSTRAINT fk_itinerary_translation_itinerary
                                           FOREIGN KEY (itinerary_id)
                                               REFERENCES itineraries(id)
                                               ON DELETE CASCADE,

                                       CONSTRAINT uk_itinerary_translation_itinerary_locale
                                           UNIQUE (itinerary_id, locale)
);