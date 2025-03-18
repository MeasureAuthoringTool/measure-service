package cms.gov.madie.measure.config.mongock;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.repositories.EndorsementRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.common.EndorserOrganization;
import gov.cms.madie.models.measure.Endorsement;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateEndorsementsChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private EndorsementRepository endorsementRepository;
  @InjectMocks private UpdateEndorsementsChangeUnit changeUnit;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private Measure measureNullEndorsements;
  private Measure measureEmptyEndorsements;
  private Measure measureWithSingleMissingEndorsement;
  private Measure measureWithSingleExistingEndorsement;
  private Measure measureWithMultipleEndorsements;

  public List<EndorserOrganization> buildEndorserOrganizations() {
    return List.of(
        EndorserOrganization.builder().id("EndId1").endorserOrganization("-").build(),
        EndorserOrganization.builder()
            .id("EndId2")
            .endorserOrganization("CMS Consensus Based Entity")
            .build());
  }

  @BeforeEach
  void setUp() {
    measureNullEndorsements =
        Measure.builder()
            .id("measureNullEndorsements")
            .measureName("measureNullEndorsements")
            .measureMetaData(MeasureMetaData.builder().endorsements(null).build())
            .build();

    measureEmptyEndorsements =
        Measure.builder()
            .id("measureEmptyEndorsements")
            .measureName("measureEmptyEndorsements")
            .measureMetaData(MeasureMetaData.builder().endorsements(List.of()).build())
            .build();

    measureWithSingleMissingEndorsement =
        Measure.builder()
            .id("measureWithSingleMissingEndorsement")
            .measureName("measureWithSingleMissingEndorsement")
            .measureMetaData(
                MeasureMetaData.builder()
                    .endorsements(List.of(Endorsement.builder().endorser("NQF").build()))
                    .build())
            .build();

    measureWithSingleExistingEndorsement =
        Measure.builder()
            .id("measureWithSingleMissingEndorsement")
            .measureName("measureWithSingleMissingEndorsement")
            .measureMetaData(
                MeasureMetaData.builder()
                    .endorsements(
                        List.of(
                            Endorsement.builder().endorser("CMS Consensus Based Entity").build()))
                    .build())
            .build();

    measureWithMultipleEndorsements =
        Measure.builder()
            .id("measureWithSingleMissingEndorsement")
            .measureName("measureWithSingleMissingEndorsement")
            .measureMetaData(
                MeasureMetaData.builder()
                    .endorsements(
                        List.of(
                            Endorsement.builder().endorser("CMS Consensus Based Entity").build(),
                            Endorsement.builder().endorser("NQF").build()))
                    .build())
            .build();
  }

  @Test
  void updateEndorsementsExitsForEmptyEndorsements() throws Exception {
    // given
    when(endorsementRepository.findAll()).thenReturn(List.of());

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verifyNoInteractions(measureRepository);
  }

  @Test
  void updateEndorsementsExitsForNullEndorsements() throws Exception {
    // given
    when(endorsementRepository.findAll()).thenReturn(null);

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verifyNoInteractions(measureRepository);
  }

  @Test
  void updateEndorsementsHandlesEmptyMeasuresCollection() throws Exception {
    // given
    when(endorsementRepository.findAll()).thenReturn(buildEndorserOrganizations());
    when(measureRepository.findAll()).thenReturn(List.of());

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  void updateEndorsementsHandlesNoEndorsementUpdates() throws Exception {
    // given
    when(endorsementRepository.findAll()).thenReturn(buildEndorserOrganizations());
    when(measureRepository.findAll())
        .thenReturn(List.of(measureNullEndorsements, measureEmptyEndorsements));

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  void updateEndorsementsHandlesEndorsementUpdatesForMeasureWithSingleMissingEndorsement()
      throws Exception {
    // given
    when(endorsementRepository.findAll()).thenReturn(buildEndorserOrganizations());
    when(measureRepository.findAll()).thenReturn(List.of(measureWithSingleMissingEndorsement));

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getEndorsements(), is(notNullValue()));
    assertThat(
        updatedMeasure.getMeasureMetaData().getEndorsements(),
        not(measureEmptyEndorsements.getMeasureMetaData().getEndorsements()));
  }

  @Test
  void updateEndorsementsHandlesEndorsementUpdatesForMeasureWithSingleExistingEndorsement()
      throws Exception {
    // given
    List<EndorserOrganization> organizations = buildEndorserOrganizations();
    when(endorsementRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll()).thenReturn(List.of(measureWithSingleExistingEndorsement));

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getEndorsements(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getEndorsements().isEmpty(), is(false));
    assertThat(
        updatedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        is(equalTo(organizations.get(1).getEndorserOrganization())));
  }

  @Test
  void updateEndorsementsHandlesEndorsementUpdatesForMeasureWithMultipleEndorsements()
      throws Exception {
    // given
    List<EndorserOrganization> organizations = buildEndorserOrganizations();
    when(endorsementRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll()).thenReturn(List.of(measureWithMultipleEndorsements));

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(
        updatedMeasure.getMeasureName(),
        is(equalTo(measureWithMultipleEndorsements.getMeasureName())));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getEndorsements(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getEndorsements().size(), is(equalTo(2)));
    assertThat(
        updatedMeasure.getMeasureMetaData().getEndorsements().get(0).getEndorser(),
        is(equalTo(organizations.get(1).getEndorserOrganization())));
    assertThat(
        updatedMeasure.getMeasureMetaData().getEndorsements().get(1).getEndorser(),
        is(equalTo(organizations.get(1).getEndorserOrganization())));
  }

  @Test
  void updateEndorsementsHandlesMultipleMeasures() throws Exception {
    // given
    List<EndorserOrganization> organizations = buildEndorserOrganizations();
    when(endorsementRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll())
        .thenReturn(
            List.of(
                measureNullEndorsements,
                measureWithSingleExistingEndorsement,
                measureEmptyEndorsements,
                measureWithSingleMissingEndorsement,
                measureWithMultipleEndorsements));

    // when
    changeUnit.updateEndorsements(measureRepository, endorsementRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(3)).save(measureArgumentCaptor.capture());
    List<Measure> allValues = measureArgumentCaptor.getAllValues();
    assertThat(allValues.size(), is(equalTo(3)));
    assertThat(
        allValues.get(0).getMeasureName(),
        is(equalTo(measureWithSingleExistingEndorsement.getMeasureName())));
    assertThat(
        allValues.get(1).getMeasureName(),
        is(equalTo(measureWithSingleMissingEndorsement.getMeasureName())));
    assertThat(
        allValues.get(2).getMeasureName(),
        is(equalTo(measureWithMultipleEndorsements.getMeasureName())));
  }

  @Test
  void rollbackExecutionDoesNothing() throws Exception {
    // given - nothing
    // when
    changeUnit.rollbackExecution(measureRepository);

    // then
    verifyNoInteractions(measureRepository);
  }
}
