package pt.ua.kartylicious.faults;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceTicket(
    UUID id, UUID reportId, String kartId, String priority, String status, Instant openedAt) {}
