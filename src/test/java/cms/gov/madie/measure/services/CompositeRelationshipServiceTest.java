package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.measure.Component;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CompositeRelationshipServiceTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private ActionLogService actionLogService;

  @InjectMocks private CompositeRelationshipService compositeRelationshipService;

  @Test
  void testAddComponentAddsCompositeMeasureId() {
    String componentMeasureId = "component-measure-id";
    Measure componentMeasure =
        Measure.builder().id(componentMeasureId).measureName("Component Measure").build();

    Measure compositeMeasure =
        Measure.builder()
            .id("composite-measure-id")
            .measureName("Composite Measure")
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .build();

    when(measureRepository.findAllById(anyCollection())).thenReturn(List.of(componentMeasure));

    compositeRelationshipService.syncComponents(
        new ArrayList<>(),
        List.of(Component.builder().measureId(componentMeasureId).build()),
        compositeMeasure,
        "test.user");

    verify(measureRepository).saveAll(anyCollection());
    List<String> savedIds = componentMeasure.getCompositeMeasureIds();
    assertEquals(1, savedIds.size());
    assertTrue(savedIds.contains(compositeMeasure.getId()));

    verify(actionLogService)
        .logAction(
            eq(compositeMeasure.getId()),
            eq(Measure.class),
            eq(ActionType.COMPONENT_ADDED),
            eq("test.user"),
            eq("Added Component measure Component Measure"));
    verify(actionLogService)
        .logAction(
            eq(componentMeasureId),
            eq(Measure.class),
            eq(ActionType.ADDED_TO_COMPOSITE),
            eq("test.user"),
            eq("Added to Composite measure Composite Measure"));
  }

  @Test
  void testAddComponentAlreadyInAnotherCompositeAppendsMeasureId() {
    String componentMeasureId = "component-measure-id";
    Measure componentMeasure =
        Measure.builder()
            .id(componentMeasureId)
            .measureName("Component Measure")
            .compositeMeasureIds(new ArrayList<>(List.of("other-measure-id")))
            .build();

    Measure compositeMeasure =
        Measure.builder()
            .id("composite-measure-id")
            .measureName("Composite Measure")
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .build();

    when(measureRepository.findAllById(anyCollection())).thenReturn(List.of(componentMeasure));

    compositeRelationshipService.syncComponents(
        new ArrayList<>(),
        List.of(Component.builder().measureId(componentMeasureId).build()),
        compositeMeasure,
        "test.user");

    verify(measureRepository).saveAll(anyCollection());
    List<String> savedIds = componentMeasure.getCompositeMeasureIds();
    assertEquals(2, savedIds.size());
    assertTrue(savedIds.contains("other-measure-id"));
    assertTrue(savedIds.contains(compositeMeasure.getId()));

    verify(actionLogService)
        .logAction(
            eq(compositeMeasure.getId()),
            eq(Measure.class),
            eq(ActionType.COMPONENT_ADDED),
            eq("test.user"),
            eq("Added Component measure Component Measure"));
    verify(actionLogService)
        .logAction(
            eq(componentMeasureId),
            eq(Measure.class),
            eq(ActionType.ADDED_TO_COMPOSITE),
            eq("test.user"),
            eq("Added to Composite measure Composite Measure"));
  }

  @Test
  void testAddComponentThrowsWhenComponentNotFound() {
    String componentMeasureId = "component-measure-id";

    Measure compositeMeasure =
        Measure.builder()
            .id("composite-measure-id")
            .measureName("Composite Measure")
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .build();

    when(measureRepository.findAllById(anyCollection())).thenReturn(List.of());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            compositeRelationshipService.syncComponents(
                new ArrayList<>(),
                List.of(Component.builder().measureId(componentMeasureId).build()),
                compositeMeasure,
                "test.user"));
  }

  @Test
  void testRemoveComponentNotInOtherCompositesRemovesMeasureId() {
    String componentMeasureId = "component-measure-id";
    Measure componentMeasure =
        Measure.builder()
            .id(componentMeasureId)
            .measureName("Component Measure")
            .compositeMeasureIds(new ArrayList<>(List.of("composite-measure-id")))
            .build();

    Measure compositeMeasure =
        Measure.builder()
            .id("composite-measure-id")
            .measureName("Composite Measure")
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .build();

    when(measureRepository.findAllById(anyCollection())).thenReturn(List.of(componentMeasure));

    compositeRelationshipService.syncComponents(
        List.of(Component.builder().measureId(componentMeasureId).build()),
        new ArrayList<>(),
        compositeMeasure,
        "test.user");

    verify(measureRepository).saveAll(anyCollection());
    assertTrue(componentMeasure.getCompositeMeasureIds().isEmpty());

    verify(actionLogService)
        .logAction(
            eq(compositeMeasure.getId()),
            eq(Measure.class),
            eq(ActionType.COMPONENT_REMOVED),
            eq("test.user"),
            eq("Removed Component measure Component Measure"));
    verify(actionLogService)
        .logAction(
            eq(componentMeasureId),
            eq(Measure.class),
            eq(ActionType.REMOVED_FROM_COMPOSITE),
            eq("test.user"),
            eq("Removed from Composite measure Composite Measure"));
  }

  @Test
  void testRemoveComponentStillInOtherCompositeRetainsMeasureId() {
    String componentMeasureId = "component-measure-id";
    Measure componentMeasure =
        Measure.builder()
            .id(componentMeasureId)
            .measureName("Component Measure")
            .compositeMeasureIds(
                new ArrayList<>(List.of("composite-measure-id", "other-measure-id")))
            .build();

    Measure compositeMeasure =
        Measure.builder()
            .id("composite-measure-id")
            .measureName("Composite Measure")
            .measureMetaData(MeasureMetaData.builder().draft(true).composite(true).build())
            .build();

    when(measureRepository.findAllById(anyCollection())).thenReturn(List.of(componentMeasure));

    compositeRelationshipService.syncComponents(
        List.of(Component.builder().measureId(componentMeasureId).build()),
        new ArrayList<>(),
        compositeMeasure,
        "test.user");

    verify(measureRepository).saveAll(anyCollection());
    List<String> savedIds = componentMeasure.getCompositeMeasureIds();
    assertEquals(1, savedIds.size());
    assertFalse(savedIds.contains("composite-measure-id"));
    assertTrue(savedIds.contains("other-measure-id"));

    verify(actionLogService)
        .logAction(
            eq(compositeMeasure.getId()),
            eq(Measure.class),
            eq(ActionType.COMPONENT_REMOVED),
            eq("test.user"),
            eq("Removed Component measure Component Measure"));
    verify(actionLogService)
        .logAction(
            eq(componentMeasureId),
            eq(Measure.class),
            eq(ActionType.REMOVED_FROM_COMPOSITE),
            eq("test.user"),
            eq("Removed from Composite measure Composite Measure"));
  }
}
