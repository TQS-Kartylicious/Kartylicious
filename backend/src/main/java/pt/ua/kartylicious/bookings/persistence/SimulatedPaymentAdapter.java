package pt.ua.kartylicious.bookings.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kartylicious.bookings.persistence.PaymentAuthorizationEntity.Status;
import pt.ua.kartylicious.bookings.port.PaymentPort;

/** Simulates deposit holds and fare capture in the same database as bookings and rides. */
@Repository
@Transactional
public class SimulatedPaymentAdapter implements PaymentPort {
  private static final BigDecimal MAXIMUM_STORED_AMOUNT = new BigDecimal("9999999999.99");

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final UUID declineUserId;

  public SimulatedPaymentAdapter(
      JdbcTemplate jdbc, Clock clock, @Value("${payment.simulated.decline-user-id:}") String decline) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.declineUserId = decline.isBlank() ? null : UUID.fromString(decline);
  }

  @Override
  public boolean authorizeSecurityDeposit(UUID userId, UUID operationId) {
    if (userId == null || operationId == null) {
      return false;
    }
    if (!userId.equals(declineUserId)) {
      var now = Timestamp.from(clock.instant());
      jdbc.update(
          """
          insert into payment_authorizations (operation_id, user_id, status, created_at, updated_at)
          values (?, ?, 'AUTHORIZED', ?, ?) on conflict (operation_id) do nothing
          """,
          operationId, userId, now, now);
    }
    return hasAuthorizedDeposit(userId, operationId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasAuthorizedDeposit(UUID userId, UUID operationId) {
    return Boolean.TRUE.equals(jdbc.queryForObject(
        """
        select exists (select 1 from payment_authorizations
          where operation_id = ? and user_id = ? and status = 'AUTHORIZED')
        """,
        Boolean.class, operationId, userId));
  }

  @Override
  public void releaseSecurityDeposit(UUID userId, UUID operationId) {
    jdbc.update(
        """
        update payment_authorizations set status = 'RELEASED', updated_at = ?
        where operation_id = ? and user_id = ? and status = 'AUTHORIZED'
        """,
        Timestamp.from(clock.instant()), operationId, userId);
  }

  @Override
  public boolean captureFinalCharge(
      UUID userId, UUID authorizationId, UUID rideId, BigDecimal amount) {
    if (userId == null || authorizationId == null || rideId == null || amount == null
        || amount.signum() < 0 || amount.compareTo(MAXIMUM_STORED_AMOUNT) > 0) {
      return false;
    }
    BigDecimal cents;
    try {
      cents = amount.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      return false;
    }

    var statuses = jdbc.queryForList(
        """
        select status from payment_authorizations
        where operation_id = ? and user_id = ? for update
        """,
        String.class, authorizationId, userId);
    if (statuses.isEmpty() || Status.RELEASED.name().equals(statuses.getFirst())) {
      return false;
    }
    if (Status.SETTLED.name().equals(statuses.getFirst())) {
      return Boolean.TRUE.equals(jdbc.queryForObject(
          """
          select exists (select 1 from payment_settlements
            where ride_id = ? and authorization_id = ? and amount = ?)
          """,
          Boolean.class, rideId, authorizationId, cents));
    }

    var now = Timestamp.from(clock.instant());
    // The unique ride and authorization keys protect even concurrent attempts with different holds.
    if (jdbc.update(
        """
        insert into payment_settlements (ride_id, authorization_id, amount, settled_at)
        values (?, ?, ?, ?) on conflict do nothing
        """,
        rideId, authorizationId, cents, now) == 0) {
      return false;
    }
    jdbc.update(
        "update payment_authorizations set status = 'SETTLED', updated_at = ? where operation_id = ?",
        now, authorizationId);
    return true;
  }
}
