package cms.gov.madie.measure.validations;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.CompositeMeasureScoring;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureScoring;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Composite validations shared by measure export and versioning.
 */
@Service
@RequiredArgsConstructor
public class CompositeMeasureValidationService {

  public static final String TWO_COMPONENTS_REQUIRED = "Two component measures must be selected";
  public static final String SCORING_MUST_BE_COMPOSITE = "Measure score must be Composite";
  public static final String COMPOSITE_SCORING_REQUIRED = "Composite measure score is required";
  public static final String COMPOSITE_SCORING_INVALID = "Component measures score is inaccurate";
  public static final String COMPONENT_MEASURE_TYPES_INVALID =
      "The measure type of the component measure(s) is invalid";
  public static final String COMPONENT_POPULATION_BASIS_INVALID =
      "The population basis for the component measure(s) is inaccurate";

  // The component scoring types each composite scoring method allows
  private static final Map<CompositeMeasureScoring, Set<MeasureScoring>> ALLOWED_COMPONENT_SCORING =
      new EnumMap<>(
          Map.of(
              CompositeMeasureScoring.OPPORTUNITY,
              Set.of(MeasureScoring.PROPORTION, MeasureScoring.RATIO),
              CompositeMeasureScoring.ALL_OR_NOTHING,
              Set.of(MeasureScoring.PROPORTION, MeasureScoring.RATIO),
              CompositeMeasureScoring.LINEAR,
              Set.of(
                  MeasureScoring.PROPORTION,
                  MeasureScoring.RATIO,
                  MeasureScoring.CONTINUOUS_VARIABLE)));

  private static final String PATIENT_BASIS = "boolean";

  private final MeasureRepository measureRepository;

  /**
   * Returns the user facing failure messages for a composite measure, empty when the measure is
   * valid or is not composite.
   */
  public List<String> validate(Measure measure) {
    if (measure == null
        || measure.getMeasureMetaData() == null
        || !measure.getMeasureMetaData().isComposite()) {
      return List.of();
    }

    List<String> errors = new ArrayList<>();
    Group compositeGroup = getCompositeGroup(measure);
    List<Component> components =
        compositeGroup == null || compositeGroup.getComponents() == null
            ? List.of()
            : compositeGroup.getComponents();
    String compositeScoring = compositeGroup == null ? null : compositeGroup.getCompositeScoring();
    // one measure can contribute several groups, so ids repeat across components
    List<String> componentMeasureIds =
        components.stream().map(Component::getMeasureId).distinct().toList();
    Set<MeasureScoring> allowedScoringTypes =
        ALLOWED_COMPONENT_SCORING.get(CompositeMeasureScoring.valueOfText(compositeScoring));

    if (compositeGroup != null
        && !MeasureScoring.COMPOSITE.toString().equals(compositeGroup.getScoring())) {
      errors.add(SCORING_MUST_BE_COMPOSITE);
    }

    if (StringUtils.isEmpty(compositeScoring)) {
      errors.add(COMPOSITE_SCORING_REQUIRED);
    } else if (allowedScoringTypes == null) {
      errors.add(COMPOSITE_SCORING_INVALID);
    }

    if (componentMeasureIds.size() < 2) {
      errors.add(TWO_COMPONENTS_REQUIRED);
    }

    // allowed component scoring is derived from the composite scoring method
    if (allowedScoringTypes == null || components.isEmpty()) {
      return errors;
    }

    Map<String, Measure> componentMeasuresById =
        measureRepository
            .findAllById(componentMeasureIds.stream().filter(Objects::nonNull).toList())
            .stream()
            .collect(Collectors.toMap(Measure::getId, Function.identity()));
    List<Group> componentGroups =
        components.stream()
            .map(component -> findComponentGroup(component, componentMeasuresById))
            .toList();

    // an unresolvable component cannot be proven valid
    boolean hasInvalidScoring =
        componentGroups.stream()
            .anyMatch(
                group ->
                    group == null
                        || !allowedScoringTypes.contains(
                            MeasureScoring.valueOfText(group.getScoring())));

    boolean hasInvalidPopulationBasis =
        isPatientBased(compositeGroup)
            && componentGroups.stream()
                .filter(Objects::nonNull)
                .anyMatch(group -> !isPatientBased(group));

    if (hasInvalidScoring) {
      errors.add(COMPONENT_MEASURE_TYPES_INVALID);
    }
    if (hasInvalidPopulationBasis) {
      errors.add(COMPONENT_POPULATION_BASIS_INVALID);
    }

    return errors;
  }

  // Components live on the group whose scoring is COMPOSITE.
  private Group getCompositeGroup(Measure measure) {
    if (CollectionUtils.isEmpty(measure.getGroups())) {
      return null;
    }
    return measure.getGroups().stream()
        .filter(group -> MeasureScoring.COMPOSITE.toString().equals(group.getScoring()))
        .findFirst()
        .orElse(measure.getGroups().get(0));
  }

  private Group findComponentGroup(Component component, Map<String, Measure> measuresById) {
    Measure componentMeasure = measuresById.get(component.getMeasureId());
    if (componentMeasure == null || componentMeasure.getGroups() == null) {
      return null;
    }
    return componentMeasure.getGroups().stream()
        .filter(group -> Objects.equals(group.getId(), component.getGroupId()))
        .findFirst()
        .orElse(null);
  }

  private boolean isPatientBased(Group group) {
    return group != null && PATIENT_BASIS.equalsIgnoreCase(group.getPopulationBasis());
  }
}
