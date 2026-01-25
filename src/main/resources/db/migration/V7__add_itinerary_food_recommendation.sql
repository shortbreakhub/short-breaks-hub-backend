CREATE TABLE IF NOT EXISTS public.itinerary_food_recommendation (
                                                id BIGSERIAL PRIMARY KEY,

                                                itinerary_id BIGINT NOT NULL UNIQUE,

                                                must_try JSONB NOT NULL DEFAULT '[]'::jsonb,
                                                areas JSONB NOT NULL DEFAULT '[]'::jsonb,
                                                places JSONB NOT NULL DEFAULT '[]'::jsonb,

                                                updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

                                                CONSTRAINT fk_itinerary_food_recommendation
                                                    FOREIGN KEY (itinerary_id)
                                                        REFERENCES itineraries(id)
                                                        ON DELETE CASCADE
);