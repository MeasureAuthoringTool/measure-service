package cms.gov.madie.measure.utils;

import gov.cms.madie.models.dto.UserDetailsDto;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class UserDisplayNameUtils {

  public static String getFullName(UserDetailsDto userDetails) {
    if (userDetails == null) {
      return "";
    }
    String firstName = userDetails.getFirstName();
    String lastName = userDetails.getLastName();

    if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
      return firstName + " " + lastName;
    } else if (StringUtils.isNotBlank(firstName)) {
      return firstName;
    } else if (StringUtils.isNotBlank(lastName)) {
      return lastName;
    }
    return "";
  }

  public static List<String> toReviewerDisplayNames(
      List<String> reviewerHarpIds, Map<String, UserDetailsDto> userDetailsMap) {
    if (CollectionUtils.isEmpty(reviewerHarpIds)) {
      return null;
    }
    return reviewerHarpIds.stream()
        .filter(StringUtils::isNotBlank)
        .map(
            harpId -> {
              String displayName = getFullName(userDetailsMap.get(harpId));
              return StringUtils.isNotBlank(displayName) ? displayName : harpId;
            })
        .toList();
  }
}
