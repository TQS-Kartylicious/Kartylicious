package pt.ua.kartylicious.faults.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Upgrades schemas created before system-generated telemetry reports were supported. */
@Component
public class FaultSchemaInitializer implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  public FaultSchemaInitializer(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    var needsUpgrade = jdbc.queryForObject(
        """
        select exists (select 1 from information_schema.columns
          where table_schema = current_schema() and table_name = 'fault_reports'
          and column_name = 'user_id' and is_nullable = 'NO')
        """,
        Boolean.class);
    if (Boolean.TRUE.equals(needsUpgrade)) {
      jdbc.execute("alter table fault_reports alter column user_id drop not null");
    }
  }
}
