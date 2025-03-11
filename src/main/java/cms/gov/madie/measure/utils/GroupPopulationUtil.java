package cms.gov.madie.measure.utils;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.PopulationType;
import gov.cms.madie.models.measure.Stratification;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroupPopulationUtil {
  private GroupPopulationUtil() {}

  public static void setGroupAndPopulationsDisplayIds(Measure measure, Group group) {
    String groupNumber = String.valueOf(getGroupNumber(group, measure.getGroups()));

    // set group display id
    group.setDisplayId("Group_" + groupNumber);

    // set population display id
    setPopulationDisplayIds(group, groupNumber);

    // set measure observation display id
    setObservationDisplayIds(group, groupNumber);

    // set stratification display id
    setStratificationDisplayIds(group, groupNumber);
  }

  static int getGroupNumber(Group group, List<Group> groups) {
    int groupNumber = 0;
    for (int i = 0; i < groups.size(); i++) {
      Group currentGroup = groups.get(i);
      if (currentGroup.getId().equals(group.getId())) {
        groupNumber = i + 1;
      }
    }
    return groupNumber;
  }

  static String getPopulationDisplayId(
      Population population, String groupNumber, boolean multipleIps, int index) {
    String newPopDisplayId = population.getName().getDisplay().replace(" ", "") + "_" + groupNumber;
    if (multipleIps && (index == 0 || index == 1)) {
      newPopDisplayId = newPopDisplayId + "_" + (index + 1);
    }
    return newPopDisplayId;
  }

  static void setPopulationDisplayIds(Group group, String groupNumber) {
    if (!CollectionUtils.isEmpty(group.getPopulations())) {
      boolean hasMultipleIps =
          group.getPopulations().stream()
                  .filter(pop -> pop.getName().equals(PopulationType.INITIAL_POPULATION))
                  .count()
              > 1;

      for (int index = 0; index < group.getPopulations().size(); index++) {
        Population population = group.getPopulations().get(index);
        String popDisplayId =
            getPopulationDisplayId(population, groupNumber, hasMultipleIps, index);
        population.setDisplayId(popDisplayId);
      }
    }
  }

  static void setObservationDisplayIds(Group group, String groupNumber) {
    if (!CollectionUtils.isEmpty(group.getMeasureObservations())) {

      for (int index = 0; index < group.getMeasureObservations().size(); index++) {
        MeasureObservation observation = group.getMeasureObservations().get(index);
        String obsDisplayId =
            getObservationDisplayId(groupNumber, group.getMeasureObservations().size() > 1, index);
        observation.setDisplayId(obsDisplayId);
      }
    }
  }

  static String getObservationDisplayId(String groupNumber, boolean multipleObs, int index) {
    String newObsDisplayId = "MeasureObservation_" + groupNumber;
    if (multipleObs) {
      newObsDisplayId = newObsDisplayId + "_" + (index + 1);
    }
    return newObsDisplayId;
  }

  static void setStratificationDisplayIds(Group group, String groupNumber) {
    if (!CollectionUtils.isEmpty(group.getStratifications())) {
      for (int index = 0; index < group.getStratifications().size(); index++) {
        Stratification stratification = group.getStratifications().get(index);
        String obsDisplayId =
            getStratificationDisplayId(groupNumber, group.getStratifications().size() > 1, index);
        stratification.setDisplayId(obsDisplayId);
      }
    }
  }

  static String getStratificationDisplayId(String groupNumber, boolean multipleStrats, int index) {
    String newStratsDisplayId = "Stratification_" + groupNumber;
    if (multipleStrats) {
      newStratsDisplayId = newStratsDisplayId + "_" + (index + 1);
    }
    return newStratsDisplayId;
  }
}
