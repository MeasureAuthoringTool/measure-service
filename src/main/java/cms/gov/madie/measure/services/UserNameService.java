package cms.gov.madie.measure.services;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.utils.UserDisplayNameUtils;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNameService {

  private final UserServiceClient userServiceClient;

  public String getUserDisplayName(String harpId) {
    if (StringUtils.isBlank(harpId)) {
      return harpId;
    }
    return UserDisplayNameUtils.toDisplayName(
        harpId, userServiceClient.getBulkUserDetails(List.of(harpId)));
  }

  public Map<String, String> getUsersDisplayNames(List<String> harpIds) {
    List<String> ids =
        harpIds == null
            ? List.of()
            : harpIds.stream().filter(StringUtils::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return UserDisplayNameUtils.toDisplayNamesByHarpId(
        ids, userServiceClient.getBulkUserDetails(ids));
  }
}
