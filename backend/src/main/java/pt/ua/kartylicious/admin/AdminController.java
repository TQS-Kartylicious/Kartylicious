package pt.ua.kartylicious.admin;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

  private final AdminService adminService;

  public AdminController(AdminService adminService) {
    this.adminService = adminService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AdminRequest request) {
    var user = adminService.createStaff(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Account created successfully.", "user", user));
  }

  @GetMapping
  public List<AdminUserResponse> listUsers() {
    return adminService.listUsers();
  }

  @PatchMapping("/{id}/role")
  public Map<String, Object> changeRole(
      @PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
    var user = adminService.changeRole(id, request.role());
    return Map.of("message", "Role updated.", "user", user);
  }

  @PatchMapping("/{id}/status")
  public Map<String, Object> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
    if (Boolean.TRUE.equals(request.active())) {
      throw new AdminExceptions.InvalidStatusException();
    }
    var user = adminService.deactivate(id);
    return Map.of("message", "Account deactivated.", "user", user);
  }
}
