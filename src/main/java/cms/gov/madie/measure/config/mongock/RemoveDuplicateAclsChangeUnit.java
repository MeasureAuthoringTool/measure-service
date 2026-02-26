package cms.gov.madie.measure.config.mongock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.measure.MeasureSet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "remove_duplicate_acls", order = "1", author = "madie_dev")
public class RemoveDuplicateAclsChangeUnit {
  List<MeasureSet> copyOfAllMeasureSets = null;

  @Execution
  public void removeDuplicateAcls(MeasureSetRepository measureSetRepository) {
    log.info("Entering removeDuplicateAcls()");
    List<MeasureSet> allMeasureSets = measureSetRepository.findAll();
    if (CollectionUtils.isNotEmpty(allMeasureSets)) {
      copyOfAllMeasureSets = new ArrayList<>(allMeasureSets);
      log.info("copyOfAllMeasureSets size = " + copyOfAllMeasureSets.size());
      for (MeasureSet measureSet : allMeasureSets) {
        List<AclSpecification> acls = measureSet.getAcls();
        if (CollectionUtils.isNotEmpty(acls)) {
          measureSet.setAcls(removeDuplicatesWithSharedWith(acls));
          measureSetRepository.save(measureSet);
        }
      }
    }
  }

  private List<AclSpecification> removeDuplicatesWithSharedWith(List<AclSpecification> aclList) {
    Map<String, AclSpecification> map = new HashMap<>();
    for (AclSpecification acl : aclList) {
      if (acl.getRoles() != null && acl.getRoles().contains(RoleEnum.SHARED_WITH)) {
        String lowerUserId = acl.getUserId().toLowerCase();
        acl.setUserId(lowerUserId); // ensure the userId is saved as lowercase
        map.put(lowerUserId, acl);
      }
    }
    return new ArrayList<>(map.values());
  }

  @RollbackExecution
  public void rollbackExecution(MeasureSetRepository measureSetRepository) {
    log.info("Entering rollbackExecution()");
    if (CollectionUtils.isNotEmpty(copyOfAllMeasureSets)) {
      log.info("roll back " + copyOfAllMeasureSets.size() + " measure sets.");
      measureSetRepository.saveAll(copyOfAllMeasureSets);
    }
  }
}
