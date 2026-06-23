package cms.gov.madie.measure.services;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.Measure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompositeRelationshipService {

  private final MeasureRepository measureRepository;
  private final ActionLogService actionLogService;

  public void syncComponents(
      List<Component> previousComponents,
      List<Component> newComponents,
      Measure compositeMeasure,
      String username) {

    Set<String> previousIds =
        previousComponents.stream().map(Component::getMeasureId).collect(Collectors.toSet());
    Set<String> newIds =
        newComponents == null
            ? Set.of()
            : newComponents.stream().map(Component::getMeasureId).collect(Collectors.toSet());

    Set<String> addedIds =
        newIds.stream().filter(id -> !previousIds.contains(id)).collect(Collectors.toSet());
    Set<String> removedIds =
        previousIds.stream().filter(id -> !newIds.contains(id)).collect(Collectors.toSet());

    Set<String> allChangedIds = new HashSet<>(addedIds);
    allChangedIds.addAll(removedIds);

    String compositeMeasureId = compositeMeasure.getId();
    String compositeName = compositeMeasure.getMeasureName();

    // Fetch all added and removed components
    Map<String, Measure> componentById =
        measureRepository.findAllById(allChangedIds).stream()
            .collect(Collectors.toMap(Measure::getId, m -> m));

    // Validate that all component measures exist first
    allChangedIds.forEach(
        id -> {
          if (!componentById.containsKey(id)) {
            throw new ResourceNotFoundException("Measure", id);
          }
        });

    // Update all components in memory before writing to db
    addedIds.forEach(
        id -> {
          Measure component = componentById.get(id);
          List<String> ids =
              new ArrayList<>(
                  Objects.requireNonNullElseGet(
                      component.getCompositeMeasureIds(), ArrayList::new));
          ids.add(compositeMeasureId);
          component.setCompositeMeasureIds(ids);
        });

    removedIds.forEach(
        id -> {
          Measure component = componentById.get(id);
          List<String> ids =
              new ArrayList<>(
                  Objects.requireNonNullElseGet(
                      component.getCompositeMeasureIds(), ArrayList::new));
          ids.remove(compositeMeasureId);
          component.setCompositeMeasureIds(ids);
        });

    measureRepository.saveAll(componentById.values());

    // Log after all writes succeed
    addedIds.forEach(
        id -> {
          actionLogService.logAction(
              compositeMeasureId,
              Measure.class,
              ActionType.COMPONENT_ADDED,
              username,
              "Added Component measure " + componentById.get(id).getMeasureName());
          actionLogService.logAction(
              id,
              Measure.class,
              ActionType.ADDED_TO_COMPOSITE,
              username,
              "Added to Composite measure " + compositeName);
        });

    removedIds.forEach(
        id -> {
          actionLogService.logAction(
              compositeMeasureId,
              Measure.class,
              ActionType.COMPONENT_REMOVED,
              username,
              "Removed Component measure " + componentById.get(id).getMeasureName());
          actionLogService.logAction(
              id,
              Measure.class,
              ActionType.REMOVED_FROM_COMPOSITE,
              username,
              "Removed from Composite measure " + compositeName);
        });
  }
}
