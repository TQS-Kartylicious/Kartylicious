package pt.ua.kartylicious.bookings;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('END_USER')")
public class BookingController {
  private final BookingService bookingService;

  public BookingController(BookingService bookingService) {
    this.bookingService = bookingService;
  }

  @PostMapping
  public BookingResponse book(
      @RequestBody BookingRequest request, @AuthenticationPrincipal Jwt jwt) {
    return bookingService.book(request, UUID.fromString(jwt.getSubject()));
  }

  @GetMapping("/active")
  public List<BookingResponse> activeBookings(@AuthenticationPrincipal Jwt jwt) {
    return bookingService.activeBookings(UUID.fromString(jwt.getSubject()));
  }

  @PatchMapping("/{bookingId}")
  public BookingResponse cancel(
      @PathVariable UUID bookingId,
      @RequestBody CancellationRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    if (!BookingStatus.CANCELLED.name().equals(request.status())) {
      throw new BookingValidationException("Booking status must be CANCELLED.");
    }
    return bookingService.cancel(bookingId, UUID.fromString(jwt.getSubject()));
  }

  public record CancellationRequest(String status) {}
}
