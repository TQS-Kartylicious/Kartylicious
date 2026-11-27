package pt.ua.kartylicious.faults;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FaultController {
  private final FaultService faults;

  public FaultController(FaultService faults) {
    this.faults = faults;
  }

  @PostMapping("/fault-reports")
  @PreAuthorize("hasRole('END_USER')")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, String> report(@RequestBody ReportRequest request, @AuthenticationPrincipal Jwt jwt) {
    faults.report(request.kartId(), UUID.fromString(jwt.getSubject()), request.category(), request.description());
    return Map.of("message", "Report submitted");
  }

  @GetMapping("/fault-reports")
  @PreAuthorize("hasRole('OPERATOR')")
  public List<FaultReport> reports() {
    return faults.reports();
  }

  @GetMapping("/maintenance-tickets")
  @PreAuthorize("hasRole('OPERATOR')")
  public List<MaintenanceTicket> tickets() {
    return faults.tickets();
  }

  @ExceptionHandler(InvalidFaultReportException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, String> invalid(InvalidFaultReportException exception) {
    return Map.of("message", exception.getMessage());
  }

  @ExceptionHandler(FaultKartNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, String> notFound(FaultKartNotFoundException exception) {
    return Map.of("message", exception.getMessage());
  }

  public record ReportRequest(String kartId, FaultReport.Category category, String description) {}
}
