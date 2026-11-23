package pt.ua.kartylicious.hubs;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.kartylicious.hubs.HubDirectoryService.HubDetails;
import pt.ua.kartylicious.hubs.HubDirectoryService.HubSummary;

@RestController
@RequestMapping("/api/hubs")
@PreAuthorize("hasRole('END_USER')")
public class HubController {

  private final HubDirectoryService hubDirectoryService;

  public HubController(HubDirectoryService hubDirectoryService) {
    this.hubDirectoryService = hubDirectoryService;
  }

  @GetMapping
  public List<HubSummary> listHubs() {
    return hubDirectoryService.listHubs();
  }

  @GetMapping("/{hubId}")
  public HubDetails getHub(@PathVariable String hubId) {
    return hubDirectoryService.getHub(hubId);
  }
}
