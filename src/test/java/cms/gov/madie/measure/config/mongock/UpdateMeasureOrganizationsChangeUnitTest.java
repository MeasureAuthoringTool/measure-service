package cms.gov.madie.measure.config.mongock;

import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.OrganizationRepository;
import gov.cms.madie.models.common.Organization;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class UpdateMeasureOrganizationsChangeUnitTest {

  @Mock private MeasureRepository measureRepository;
  @Mock private OrganizationRepository organizationRepository;
  @InjectMocks private UpdateMeasureOrganizationsChangeUnit changeUnit;

  @Captor private ArgumentCaptor<Measure> measureArgumentCaptor;

  private Measure measureNullStewardNullDevelopers;
  private Measure measureNullStewardEmptyDevelopers;
  private Measure measureWithMissingStewardEmptyDevelopers;
  private Measure measureWithExistingStewardEmptyDevelopers;
  private Measure measureWithExistingStewardAndSingleMissingDeveloper;
  private Measure measureWithExistingStewardAndSingleExistingDeveloper;
  private Measure measureWithStewardAndMultipleDevelopers;

  public List<Organization> buildOrganizations() {
    return List.of(
        Organization.builder().id("OrgId1").name("SemanticBits").build(),
        Organization.builder().id("OrgId2").name("ICF").build(),
        Organization.builder().id("OrgId3").name("AltoPoint").build());
  }

  @BeforeEach
  void setUp() {
    measureNullStewardNullDevelopers =
        Measure.builder()
            .id("measureNullStewardNullDevelopers")
            .measureName("measureNullStewardNullDevelopers")
            .measureMetaData(MeasureMetaData.builder().steward(null).build())
            .build();
    measureNullStewardNullDevelopers.getMeasureMetaData().setDevelopers(null);

    measureNullStewardEmptyDevelopers =
        Measure.builder()
            .id("measureNullStewardEmptyDevelopers")
            .measureName("measureNullStewardEmptyDevelopers")
            .measureMetaData(MeasureMetaData.builder().steward(null).developers(List.of()).build())
            .build();

    measureWithMissingStewardEmptyDevelopers =
        Measure.builder()
            .id("measureWithMissingStewardEmptyDevelopers")
            .measureName("measureWithMissingStewardEmptyDevelopers")
            .measureMetaData(
                MeasureMetaData.builder()
                    .steward(Organization.builder().name("DELETED_ORG").build())
                    .developers(List.of())
                    .build())
            .build();

    measureWithExistingStewardEmptyDevelopers =
        Measure.builder()
            .id("measureWithExistingStewardEmptyDevelopers")
            .measureName("measureWithExistingStewardEmptyDevelopers")
            .measureMetaData(
                MeasureMetaData.builder()
                    .steward(Organization.builder().name("SemanticBits").build())
                    .developers(List.of())
                    .build())
            .build();

    measureWithExistingStewardAndSingleMissingDeveloper =
        Measure.builder()
            .id("measureWithExistingStewardAndSingleMissingDeveloper")
            .measureName("measureWithExistingStewardAndSingleMissingDeveloper")
            .measureMetaData(
                MeasureMetaData.builder()
                    .steward(Organization.builder().name("SemanticBits").build())
                    .developers(List.of(Organization.builder().name("DELETED_ORG").build()))
                    .build())
            .build();

    measureWithExistingStewardAndSingleExistingDeveloper =
        Measure.builder()
            .id("measureWithExistingStewardAndSingleExistingDeveloper")
            .measureName("measureWithExistingStewardAndSingleExistingDeveloper")
            .measureMetaData(
                MeasureMetaData.builder()
                    .steward(Organization.builder().name("SemanticBits").build())
                    .developers(List.of(Organization.builder().name("ICF").build()))
                    .build())
            .build();

    measureWithStewardAndMultipleDevelopers =
        Measure.builder()
            .id("measureWithStewardAndMultipleDevelopers")
            .measureName("measureWithStewardAndMultipleDevelopers")
            .measureMetaData(
                MeasureMetaData.builder()
                    .steward(Organization.builder().name("SemanticBits").build())
                    .developers(
                        List.of(
                            Organization.builder().name("SemanticBits").build(),
                            Organization.builder().name("DELETED_ORG").build(),
                            Organization.builder().name("ICF").build()))
                    .build())
            .build();
  }

  @Test
  void updateMeasureOrganizationsExitsForEmptyOrganizations() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(List.of());

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verifyNoInteractions(measureRepository);
  }

  @Test
  void updateMeasureOrganizationsExitsForNullOrganizations() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(null);

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verifyNoInteractions(measureRepository);
  }

  @Test
  void updateMeasureOrganizationsHandlesEmptyMeasuresCollection() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());
    when(measureRepository.findAll()).thenReturn(List.of());

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  void updateMeasureOrganizationsHandlesNoOrganizationUpdates() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());
    when(measureRepository.findAll())
        .thenReturn(List.of(measureNullStewardNullDevelopers, measureNullStewardEmptyDevelopers));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verifyNoMoreInteractions(measureRepository);
  }

  @Test
  void updateMeasureOrganizationsHandlesOrganizationUpdatesForStewardMissing() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());
    when(measureRepository.findAll()).thenReturn(List.of(measureWithMissingStewardEmptyDevelopers));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(nullValue()));
  }

  @Test
  void updateMeasureOrganizationsHandlesOrganizationUpdatesForStewardExisting() throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());
    when(measureRepository.findAll())
        .thenReturn(List.of(measureWithExistingStewardEmptyDevelopers));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(notNullValue()));
    assertThat(
        updatedMeasure.getMeasureMetaData().getSteward(), is(equalTo(buildOrganizations().get(0))));
  }

  @Test
  void
      updateMeasureOrganizationsHandlesOrganizationUpdatesForMeasureWithExistingStewardAndSingleMissingDeveloper()
          throws Exception {
    // given
    when(organizationRepository.findAll()).thenReturn(buildOrganizations());
    when(measureRepository.findAll())
        .thenReturn(List.of(measureWithExistingStewardAndSingleMissingDeveloper));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(notNullValue()));
    assertThat(
        updatedMeasure.getMeasureMetaData().getSteward(), is(equalTo(buildOrganizations().get(0))));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers().isEmpty(), is(true));
  }

  @Test
  void
      updateMeasureOrganizationsHandlesOrganizationUpdatesForMeasureWithExistingStewardAndSingleExistingDeveloper()
          throws Exception {
    // given
    List<Organization> organizations = buildOrganizations();
    when(organizationRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll())
        .thenReturn(List.of(measureWithExistingStewardAndSingleExistingDeveloper));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(equalTo(organizations.get(0))));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers().isEmpty(), is(false));
    assertThat(
        updatedMeasure.getMeasureMetaData().getDevelopers().get(0),
        is(equalTo(organizations.get(1))));
  }

  @Test
  void
      updateMeasureOrganizationsHandlesOrganizationUpdatesForMeasureWithStewardAndMultipleDevelopers()
          throws Exception {
    // given
    List<Organization> organizations = buildOrganizations();
    when(organizationRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll()).thenReturn(List.of(measureWithStewardAndMultipleDevelopers));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(1)).save(measureArgumentCaptor.capture());
    Measure updatedMeasure = measureArgumentCaptor.getValue();
    assertThat(updatedMeasure, is(notNullValue()));
    assertThat(
        updatedMeasure.getMeasureName(),
        is(equalTo(measureWithStewardAndMultipleDevelopers.getMeasureName())));
    assertThat(updatedMeasure.getMeasureMetaData(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getSteward(), is(equalTo(organizations.get(0))));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers(), is(notNullValue()));
    assertThat(updatedMeasure.getMeasureMetaData().getDevelopers().size(), is(equalTo(2)));
    assertThat(
        updatedMeasure.getMeasureMetaData().getDevelopers().get(0),
        is(equalTo(organizations.get(0))));
    assertThat(
        updatedMeasure.getMeasureMetaData().getDevelopers().get(1),
        is(equalTo(organizations.get(1))));
  }

  @Test
  void updateMeasureOrganizationsHandlesMultipleMeasures() throws Exception {
    // given
    List<Organization> organizations = buildOrganizations();
    when(organizationRepository.findAll()).thenReturn(organizations);
    when(measureRepository.findAll())
        .thenReturn(
            List.of(
                measureNullStewardNullDevelopers,
                measureWithExistingStewardAndSingleExistingDeveloper,
                measureWithMissingStewardEmptyDevelopers,
                measureNullStewardEmptyDevelopers,
                measureWithStewardAndMultipleDevelopers));

    // when
    changeUnit.updateMeasureOrganizations(measureRepository, organizationRepository);

    // then
    verify(measureRepository, times(1)).findAll();
    verify(measureRepository, times(3)).save(measureArgumentCaptor.capture());
    List<Measure> allValues = measureArgumentCaptor.getAllValues();
    assertThat(allValues.size(), is(equalTo(3)));
    assertThat(
        allValues.get(0).getMeasureName(),
        is(equalTo(measureWithExistingStewardAndSingleExistingDeveloper.getMeasureName())));
    assertThat(
        allValues.get(1).getMeasureName(),
        is(equalTo(measureWithMissingStewardEmptyDevelopers.getMeasureName())));
    assertThat(
        allValues.get(2).getMeasureName(),
        is(equalTo(measureWithStewardAndMultipleDevelopers.getMeasureName())));
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
