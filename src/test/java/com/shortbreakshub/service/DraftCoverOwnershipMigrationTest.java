package com.shortbreakshub.service;

import com.shortbreakshub.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DraftCoverOwnershipMigrationTest extends PostgresTestSupport {
    @Test void v10PreservesLegacyDataAndDoesNotGuessOwnership() throws Exception {
        String database="cover_"+UUID.randomUUID().toString().replace("-", "");
        try(var connection=POSTGRES.getPostgresDatabase().getConnection();var statement=connection.createStatement()){statement.execute("CREATE DATABASE "+database);}
        var source=new DriverManagerDataSource("jdbc:postgresql://localhost:"+POSTGRES.getPort()+"/"+database,"postgres","");
        Flyway.configure().dataSource(source).target("9").load().migrate();var jdbc=new JdbcTemplate(source);
        jdbc.update("insert into users(id,email,password_hash,display_name,currency,email_verified) values (1,'synthetic@example.invalid','synthetic-hash','Synthetic','USD',false)");
        jdbc.update("insert into community_itinerary_draft(id,user_id,country,cover_photo,slug,created_at,updated_at,region,days,title,summary,visibility,estimated_cost) values (1,1,'Synthetic','https://legacy.invalid/cover.png','legacy',now(),now(),'EUROPE',2,'Legacy','Legacy summary','PRIVATE',1)");
        assertEquals(1,Flyway.configure().dataSource(source).load().migrate().migrationsExecuted);
        assertEquals("https://legacy.invalid/cover.png",jdbc.queryForObject("select cover_photo from community_itinerary_draft where id=1",String.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from draft_cover_uploads",Integer.class));
        jdbc.update("insert into draft_cover_uploads(owner_id,asset_id,public_id,secure_url) values (1,'asset','community-draft-covers/server-id','https://synthetic.invalid/cover.png')");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into draft_cover_uploads(owner_id,asset_id,public_id,secure_url) values (999,'other','community-draft-covers/other','https://synthetic.invalid/other.png')"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into draft_cover_uploads(owner_id,asset_id,public_id,secure_url) values (1,'asset','community-draft-covers/other','https://synthetic.invalid/other.png')"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into draft_cover_uploads(owner_id,asset_id,public_id,secure_url) values (1,'other','avatar','https://synthetic.invalid/other.png')"));
        jdbc.execute("drop table draft_cover_uploads");
        assertEquals("https://legacy.invalid/cover.png",jdbc.queryForObject("select cover_photo from community_itinerary_draft where id=1",String.class));
    }
}
