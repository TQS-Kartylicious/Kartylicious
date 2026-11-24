package pt.ua.kartylicious.hubs.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HubTestDataResetter {

  private final JdbcTemplate jdbcTemplate;
  private final HubDatabaseSeeder seeder;

  public HubTestDataResetter(JdbcTemplate jdbcTemplate, HubDatabaseSeeder seeder) {
    this.jdbcTemplate = jdbcTemplate;
    this.seeder = seeder;
  }

  @Transactional
  public void resetHubs() {
    jdbcTemplate.execute("DELETE FROM rides");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM karts");
    jdbcTemplate.execute("DELETE FROM hubs");
    seeder.run();
  }
}