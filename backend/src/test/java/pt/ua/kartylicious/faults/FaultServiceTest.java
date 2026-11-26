package pt.ua.kartylicious.faults;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import app.getxray.xray.junit.customjunitxml.annotations.Requirement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.kartylicious.faults.port.FaultRepository;
import pt.ua.kartylicious.hubs.Kart;
import pt.ua.kartylicious.hubs.KartStatus;
import pt.ua.kartylicious.hubs.port.HubRepository;

@ExtendWith(MockitoExtension.class)
class FaultServiceTest {
  private static final String KART_ID = "kart-1";
  private static final UUID USER_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2099-01-01T10:00:00Z");

  @Mock private FaultRepository faults;
  @Mock private HubRepository hubs;
  private FaultService service;

  @BeforeEach
  void setUp() {
    service = new FaultService(faults, hubs, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @Requirement("KARTY-39")
  void normalReportPersistsDetailsWithoutFlaggingTheKartOrOpeningATicket() {
    when(hubs.lockKartById(KART_ID)).thenReturn(Optional.of(
        new Kart(KART_ID, "Kart 1", 80, KartStatus.AVAILABLE, true, false)));

    service.report(KART_ID, USER_ID, FaultReport.Category.NORMAL, "  Clean the seat  ");

    var report = ArgumentCaptor.forClass(FaultReport.class);
    verify(faults).saveReport(report.capture());
    assertThat(report.getValue()).isEqualTo(new FaultReport(
        report.getValue().id(), KART_ID, USER_ID, FaultReport.Category.NORMAL, "Clean the seat", NOW));
    verify(hubs).lockKartById(KART_ID);
    verifyNoMoreInteractions(hubs, faults);
  }

  @Test
  @Requirement("KARTY-39")
  void criticalReportFlagsTheKartAndOpensAnUrgentTicketLinkedToTheReport() {
    when(hubs.reportCriticalFault(KART_ID)).thenReturn(true);

    service.report(KART_ID, USER_ID, FaultReport.Category.CRITICAL, "Brake jam");

    var report = ArgumentCaptor.forClass(FaultReport.class);
    var ticket = ArgumentCaptor.forClass(MaintenanceTicket.class);
    verify(hubs).reportCriticalFault(KART_ID);
    verify(faults).saveReport(report.capture());
    verify(faults).saveTicket(ticket.capture());
    assertThat(report.getValue().category()).isEqualTo(FaultReport.Category.CRITICAL);
    assertThat(ticket.getValue()).isEqualTo(new MaintenanceTicket(
        ticket.getValue().id(), report.getValue().id(), KART_ID, "URGENT", "OPEN", NOW));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  @Requirement("KARTY-39")
  void invalidDescriptionsDoNotTouchFleetOrReports(String description) {
    assertThatThrownBy(() -> service.report(KART_ID, USER_ID, FaultReport.Category.CRITICAL, description))
        .isInstanceOf(InvalidFaultReportException.class)
        .hasMessage("Describe the issue");
    verifyNoInteractions(hubs, faults);
  }

  @Test
  @Requirement("KARTY-39")
  void missingCategoryIsRejectedBeforeAnyChanges() {
    assertThatThrownBy(() -> service.report(KART_ID, USER_ID, null, "Brake jam"))
        .isInstanceOf(InvalidFaultReportException.class)
        .hasMessage("Select a category");
    verifyNoInteractions(hubs, faults);
  }

  @Test
  @Requirement("KARTY-39")
  void nonexistentKartDoesNotGenerateAReportOrTicket() {
    assertThatThrownBy(
            () -> service.report(KART_ID, USER_ID, FaultReport.Category.CRITICAL, "Brake jam"))
        .isInstanceOf(FaultKartNotFoundException.class);
    verifyNoInteractions(faults);
  }
}
