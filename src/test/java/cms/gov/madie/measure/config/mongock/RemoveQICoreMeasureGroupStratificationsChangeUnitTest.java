package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.Stratification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class RemoveQICoreMeasureGroupStratificationsChangeUnitTest {

  @Mock private MeasureRepository measureRepository;

  @InjectMocks
  private RemoveQICoreMeasureGroupStratificationsChangeUnit
      removeQICoreMeasureGroupStratificationsChangeUnit;

  private List<Measure> mockMeasures;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);

    // Create mock measures for QI-Core v4.1.1 and v6.0.0
    Stratification stratification =
        Stratification.builder().id(UUID.randomUUID().toString()).build();

    Group groupWithStratifications =
        Group.builder().stratifications(List.of(stratification)).build();

    Measure measureWithStratifications =
        Measure.builder()
            .id(UUID.randomUUID().toString())
            .model(ModelType.QI_CORE.getValue())
            .groups(List.of(groupWithStratifications))
            .active(true)
            .build();

    Measure measureWithEmptyStratifications =
        Measure.builder()
            .id(UUID.randomUUID().toString())
            .model(ModelType.QI_CORE_6_0_0.getValue())
            .groups(List.of(groupWithStratifications))
            .active(true)
            .build();

    mockMeasures = new ArrayList<>();
    mockMeasures.add(measureWithStratifications);
    mockMeasures.add(measureWithEmptyStratifications);
  }

  @Test
  public void testRemoveQICoreMeasureGroupStratifications() throws Exception {
    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()))
        .thenReturn(Collections.singletonList(mockMeasures.get(0)));
    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(Collections.singletonList(mockMeasures.get(1)));

    removeQICoreMeasureGroupStratificationsChangeUnit.removeQICoreMeasureGroupStratifications(
        measureRepository);

    verify(measureRepository, times(2)).save(any(Measure.class));
    assertEquals(2, removeQICoreMeasureGroupStratificationsChangeUnit.getTempMeasures().size());
    removeQICoreMeasureGroupStratificationsChangeUnit
        .getTempMeasures()
        .forEach(
            measure ->
                measure
                    .getGroups()
                    .forEach(group -> assertEquals(0, group.getStratifications().size())));
  }

  @Test
  public void testRemoveQICoreMeasureGroupStratificationsNoChange() throws Exception {
    Measure measureWithoutGroups =
        Measure.builder()
            .id(UUID.randomUUID().toString())
            .model(ModelType.QI_CORE.getValue())
            .groups(new ArrayList<>())
            .active(true)
            .build();

    when(measureRepository.findAllByModel(ModelType.QI_CORE.getValue()))
        .thenReturn(Collections.singletonList(measureWithoutGroups));
    when(measureRepository.findAllByModel(ModelType.QI_CORE_6_0_0.getValue()))
        .thenReturn(Collections.emptyList());

    removeQICoreMeasureGroupStratificationsChangeUnit.removeQICoreMeasureGroupStratifications(
        measureRepository);

    verify(measureRepository, never()).save(any(Measure.class));
    assertEquals(0, removeQICoreMeasureGroupStratificationsChangeUnit.getTempMeasures().size());
  }

  @Test
  public void testRollbackExecution() throws Exception {
    // Prepare mock measures for rollback
    when(measureRepository.findAllByModel(anyString())).thenReturn(mockMeasures);

    removeQICoreMeasureGroupStratificationsChangeUnit.setTempMeasures(mockMeasures);

    removeQICoreMeasureGroupStratificationsChangeUnit.rollbackExecution(measureRepository);

    verify(measureRepository, times(2)).save(any(Measure.class));
  }
}
