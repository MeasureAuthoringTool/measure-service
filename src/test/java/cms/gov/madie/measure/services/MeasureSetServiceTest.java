package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.exceptions.HarpIdMismatchException;
import cms.gov.madie.measure.exceptions.InvalidIdException;
import cms.gov.madie.measure.exceptions.InvalidRequestException;
import cms.gov.madie.measure.exceptions.ResourceNotFoundException;
import cms.gov.madie.measure.repositories.GeneratorRepository;
import cms.gov.madie.measure.repositories.MeasureRepository;
import cms.gov.madie.measure.repositories.MeasureSetRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeasureSetServiceTest {

  @InjectMocks private MeasureSetService measureSetService;
  @Mock MeasureRepository measureRepository;
  @Mock MeasureSetRepository measureSetRepository;
  @Mock GeneratorRepository generatorRepository;
  @Mock private ActionLogService actionLogService;
  MeasureSet measureSet;

  private final String MEASURE_SET_ID = "measureSet1";

  @BeforeEach
  public void setUp() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    measureSet =
        MeasureSet.builder()
            .measureSetId("msid-2")
            .owner("user-1")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec);
                  }
                })
            .build();
  }

  @Test
  public void testCreateMeasureSet() {
    when(measureSetRepository.existsByMeasureSetId("msid-2")).thenReturn(false);
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(measureSet);
    measureSetService.createMeasureSet("user-1", "msid-xyz-p12r-12ert", "msid-2", null);

    verify(measureSetRepository, times(1)).existsByMeasureSetId("msid-2");
    verify(measureSetRepository, times(1)).save(any(MeasureSet.class));
    verify(actionLogService, times(1))
        .logMeasureSetAction(
            measureSet.getMeasureSetId(), MeasureSet.class, ActionType.CREATED, "user-1");
  }

  @Test
  public void testNotCreateMeasureSetWhenMeasureSetIdExists() {
    when(measureSetRepository.existsByMeasureSetId("msid-2")).thenReturn(true);
    measureSetService.createMeasureSet("user-1", "msid-xyz-p12r-12ert", "msid-2", "2");
    verify(measureSetRepository, times(1)).existsByMeasureSetId("msid-2");
    verify(measureSetRepository, times(0)).save(measureSet);
  }

  @Test
  public void testGrantOperationWithNoAclInMeasureSet() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john_1");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();
    MeasureSet updatedMeasureSet =
        MeasureSet.builder().measureSetId("1").owner("john_1").acls(List.of(aclSpec)).build();

    measureSet.setAcls(null);
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));

    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet measureSet = measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(measureSet.getMeasureSetId(), is(equalTo(updatedMeasureSet.getMeasureSetId())));
    assertThat(measureSet.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    assertThat(measureSet.getAcls().size(), is(equalTo(1)));

    verify(actionLogService, times(1))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.SHARED, "userName", aclSpec.getUserId());
  }

  @Test
  public void testGrantOperationAsFirstNewAcl() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john_1");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();
    MeasureSet updatedMeasureSet =
        MeasureSet.builder().measureSetId("1").owner("john_1").acls(List.of(aclSpec)).build();
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet measureSet = measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(measureSet.getMeasureSetId(), is(equalTo(updatedMeasureSet.getMeasureSetId())));
    assertThat(measureSet.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    assertThat(measureSet.getAcls().size(), is(equalTo(1)));

    verify(actionLogService, times(1))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.SHARED, "userName", aclSpec.getUserId());
  }

  @Test
  public void testGrantOperationAsSecondNewAcl() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("john");
    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("jane");
    aclSpec2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec2)).action(AclOperation.AclAction.GRANT).build();
    MeasureSet updatedMeasureSet =
        MeasureSet.builder()
            .measureSetId("1")
            .owner("john")
            .acls(List.of(aclSpec1, aclSpec2))
            .build();
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet measureSet = measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(measureSet.getMeasureSetId(), is(equalTo(updatedMeasureSet.getMeasureSetId())));
    assertThat(measureSet.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    assertThat(measureSet.getAcls().size(), is(equalTo(2)));

    verify(actionLogService, times(1))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.SHARED, "userName", aclSpec2.getUserId());
  }

  @Test
  public void testGrantOperationWithExistingAclSpecificationWithoutShareWithRole() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("john");
    aclSpec1.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec1)).action(AclOperation.AclAction.GRANT).build();

    MeasureSet updatedMeasureSet =
        MeasureSet.builder()
            .measureSetId("1")
            .owner("john")
            .acls(
                List.of(
                    AclSpecification.builder()
                        .userId("john")
                        .roles(Set.of(RoleEnum.SHARED_WITH))
                        .build()))
            .build();

    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(new HashSet<>());
    measureSet.setAcls(List.of(aclSpec));

    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet measureSet = measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(measureSet.getMeasureSetId(), is(equalTo(updatedMeasureSet.getMeasureSetId())));
    assertThat(measureSet.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    assertThat(measureSet.getAcls().size(), is(equalTo(1)));

    verify(actionLogService, times(1))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.SHARED, "userName", aclSpec.getUserId());
  }

  @Test
  public void testGrantOperationWithExistingAclSpecificationWithShareWithRole() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("john");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });
    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("john");
    aclSpec2.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec2)).action(AclOperation.AclAction.GRANT).build();
    MeasureSet updatedMeasureSet =
        MeasureSet.builder().measureSetId("1").owner("john").acls(List.of(aclSpec1)).build();
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet measureSet = measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(measureSet.getMeasureSetId(), is(equalTo(updatedMeasureSet.getMeasureSetId())));
    assertThat(measureSet.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    assertThat(measureSet.getAcls().size(), is(equalTo(1)));
    assertThat(measureSet.getAcls().get(0).getUserId(), is(equalTo(aclSpec2.getUserId())));

    verify(actionLogService, times(0))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.SHARED, "userName", aclSpec2.getUserId());
  }

  @Test
  public void testRevokeOperation() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.REVOKE).build();
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(measureSet);

    MeasureSet updatedMeasureSet =
        measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(updatedMeasureSet.getMeasureSetId(), is(equalTo(measureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(measureSet.getOwner())));
    assertThat(updatedMeasureSet.getAcls().size(), is(equalTo(0)));

    verify(actionLogService, times(1))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.UNSHARED, "userName", aclSpec.getUserId());
  }

  @Test
  public void testRevokeOperationWithNoAclSpecificationInMeasureSet() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("jane");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.REVOKE).build();
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(measureSet);

    MeasureSet updatedMeasureSet =
        measureSetService.updateMeasureSetAcls("1", aclOperation, "userName");
    assertThat(updatedMeasureSet.getMeasureSetId(), is(equalTo(measureSet.getMeasureSetId())));
    assertThat(updatedMeasureSet.getOwner(), is(equalTo(measureSet.getOwner())));
    assertThat(updatedMeasureSet.getAcls().size(), is(equalTo(1)));

    verify(actionLogService, times(0))
        .logShareAccessControlAction(
            "1", MeasureSet.class, ActionType.UNSHARED, "userName", aclSpec.getUserId());
  }

  @Test
  public void testUpdateMeasureSetAclsWhenMeasureSetNotFound() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john_1");
    aclSpec.setRoles(Set.of(RoleEnum.SHARED_WITH));
    AclOperation aclOperation =
        AclOperation.builder().acls(List.of(aclSpec)).action(AclOperation.AclAction.GRANT).build();

    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.updateMeasureSetAcls("1", aclOperation, "userName"));
    assertEquals(
        "User userName called updateMeasureSetAcls with AclOperation AclOperation(acls=[AclSpecification(userId=john_1, roles=[SHARED_WITH])], action=GRANT) but failed because no measure set exists with measure set ID 1",
        ex.getMessage());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testUpdateOwnership() {
    MeasureSet updatedMeasureSet = measureSet;
    updatedMeasureSet.setOwner("testUser");
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(updatedMeasureSet);

    MeasureSet result = measureSetService.updateOwnership("1", "testUser");
    assertThat(result.getId(), is(equalTo(updatedMeasureSet.getId())));
    assertThat(result.getOwner(), is(equalTo(updatedMeasureSet.getOwner())));
    verify(actionLogService, times(1))
        .logMeasureSetAction("1", MeasureSet.class, ActionType.UPDATED, "apiKey");
  }

  @Test
  public void testUpdateOwnershipWhenMeasureSetNotFound() {
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.updateOwnership("1", "testUser"));
    assertTrue(ex.getMessage().contains("measure set may not exist."));
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
    verify(actionLogService, times(0))
        .logMeasureSetAction("1", MeasureSet.class, ActionType.UPDATED, "apiKey");
  }

  @Test
  public void testCreateCmsId() {
    final MeasureSet measureSet1 =
        MeasureSet.builder().measureSetId("msid-2").cmsId(2).owner("user-1").build();

    when(measureSetRepository.findByMeasureSetId(anyString()))
        .thenReturn(Optional.ofNullable(measureSet));
    when(generatorRepository.findAndModify("cms_id")).thenReturn(2);
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(measureSet1);

    MeasureSet result = measureSetService.createAndUpdateCmsId("measureSetId", "testUser");
    assertThat(result.getCmsId(), is(equalTo(2)));
    assertThat(result.getId(), is(equalTo(measureSet1.getId())));
    verify(actionLogService, times(1))
        .logMeasureSetAction(
            measureSet.getMeasureSetId(), MeasureSet.class, ActionType.CREATED, "testUser");
  }

  @Test
  public void testCreateCmsIdWhenMeasureSetIdIsNotValid() {
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.createAndUpdateCmsId("measureSetId", "testUser"));
    assertTrue(
        ex.getMessage()
            .contains("No measure set exists for measure with measure set id measureSetId"));
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testCreateCmsIdWhenCmsIdAlreadyExistsInMeasureSet() {
    measureSet.setCmsId(6);
    when(measureSetRepository.findByMeasureSetId(anyString()))
        .thenReturn(Optional.ofNullable(measureSet));

    Exception ex =
        assertThrows(
            InvalidRequestException.class,
            () -> measureSetService.createAndUpdateCmsId("measureSetId", "testUser"));
    assertTrue(
        ex.getMessage()
            .contains(
                "CMS ID already exists. Once a CMS Identifier has been generated it may not be modified or removed for any draft or version of a measure."));
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsId() {
    Integer cmsId = 1;
    Measure measure =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId1").build();

    List<Measure> measures = Collections.singletonList(measure);
    MeasureSet measureSet = MeasureSet.builder().measureSetId("1").cmsId(1).owner("owner1").build();
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureRepository.findAllByMeasureSetIdAndActive(anyString(), anyBoolean()))
        .thenReturn(measures);
    when(measureSetRepository.save(any(MeasureSet.class))).thenReturn(measureSet);

    String responseBody = measureSetService.deleteCmsId(measureId, cmsId, measureSet.getOwner());

    assertEquals(
        responseBody,
        String.format(
            "CMS id of %s was deleted successfully from measure set with measure set id of %s",
            cmsId, measure.getMeasureSetId()));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
    verify(measureSetRepository, times(1)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdWhenMeasureWithMeasureIdIsNotFound() {
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.deleteCmsId(measureId, 1, anyString()));

    assertTrue(
        ex.getMessage()
            .contains(String.format("No measure exists with measure id of %s", measureId)));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdHarpIdMismatchException() {
    String harpId = "owner2";
    Measure measure =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId").build();

    MeasureSet measureSet = MeasureSet.builder().measureSetId("1").cmsId(2).owner("owner1").build();
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));

    Exception ex =
        assertThrows(
            HarpIdMismatchException.class,
            () -> measureSetService.deleteCmsId(measureId, anyInt(), harpId));

    assertTrue(
        ex.getMessage()
            .contains(
                String.format(
                    "Response could not be completed because the HARP id of %s passed in does not match the owner of the measure with the measure id of %s. The owner of the measure is %s",
                    harpId, measure.getId(), measureSet.getOwner())));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdWhenMeasureSetIsNotFound() {
    Measure measure =
        Measure.builder()
            .model(ModelType.QI_CORE.getValue())
            .measureSetId("measureSetId")
            .measureSet(MeasureSet.builder().owner("owner1").build())
            .build();

    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.empty());

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.deleteCmsId(measureId, 1, measure.getMeasureSet().getOwner()));

    assertTrue(
        ex.getMessage()
            .contains(
                String.format(
                    "No measure set exists for measure with measure set id of %s",
                    measure.getMeasureSetId())));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdWhenCmsIdIsNotFoundInMeasureSet() {
    Integer cmsId = 1;
    Measure measure =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId").build();

    MeasureSet measureSet = MeasureSet.builder().measureSetId("1").owner("owner1").build();
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));

    Exception ex =
        assertThrows(
            ResourceNotFoundException.class,
            () -> measureSetService.deleteCmsId(measureId, cmsId, measureSet.getOwner()));

    assertTrue(
        ex.getMessage()
            .contains(
                String.format(
                    "No CMS id of %s exists to be deleted within measure set with measure set id of %s",
                    cmsId, measure.getMeasureSetId())));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdWhenCmsIdToDeleteDoesNotMatchCmsIdInMeasureSet() {
    Integer cmsId = 1;
    Measure measure =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId").build();

    MeasureSet measureSet = MeasureSet.builder().measureSetId("1").cmsId(2).owner("owner1").build();
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure));
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));

    Exception ex =
        assertThrows(
            InvalidIdException.class,
            () -> measureSetService.deleteCmsId(measureId, cmsId, measureSet.getOwner()));

    assertTrue(
        ex.getMessage()
            .contains(
                String.format(
                    "CMS id of %s passed in does not match CMS id of %s within measure set with measure set id of %s",
                    cmsId, measureSet.getCmsId(), measure.getMeasureSetId())));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testDeleteCmsIdWhenMeasureHasMultipleVersions() {
    Integer cmsId = 1;
    Measure measure1 =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId1").build();

    Measure measure2 =
        Measure.builder().model(ModelType.QI_CORE.getValue()).measureSetId("measureSetId2").build();

    List<Measure> measures = Arrays.asList(measure1, measure2);
    MeasureSet measureSet = MeasureSet.builder().measureSetId("1").cmsId(1).owner("owner1").build();
    String measureId = "measureId";

    when(measureRepository.findById(anyString())).thenReturn(Optional.of(measure1));

    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    when(measureRepository.findAllByMeasureSetIdAndActive(anyString(), anyBoolean()))
        .thenReturn(measures);

    Exception ex =
        assertThrows(
            InvalidRequestException.class,
            () -> measureSetService.deleteCmsId(measureId, cmsId, measureSet.getOwner()));

    assertTrue(
        ex.getMessage()
            .contains(
                String.format(
                    String.format(
                        "Measure set with measure set id of %s contains more than 1 measure. Cannot delete CMS id when measure set has more than 1 version of measure.",
                        measure1.getMeasureSetId()))));
    verify(measureRepository, times(1)).findById(anyString());
    verify(measureSetRepository, times(1)).findByMeasureSetId(anyString());
    verify(measureRepository, times(1)).findAllByMeasureSetIdAndActive(anyString(), anyBoolean());
    verify(measureSetRepository, times(0)).save(any(MeasureSet.class));
  }

  @Test
  public void testFindByMeasureSetId() {
    when(measureSetRepository.findByMeasureSetId(anyString())).thenReturn(Optional.of(measureSet));
    assertEquals(measureSet, measureSetService.findByMeasureSetId("set-id-123"));
  }

  @Test
  void testGetMeasuresByMeasureSetIdDelegatesToRepository() {
    MeasureSearchCriteria criteria = new MeasureSearchCriteria();
    criteria.setSearchField("test");
    MeasureListDTO mockedMeasureListDTO =
        MeasureListDTO.builder().id("m1").measureName("Test Measure").build();
    List<MeasureListDTO> expectedList = List.of(mockedMeasureListDTO);

    when(measureSetRepository.findMeasuresByMeasureSetId(MEASURE_SET_ID, true, criteria))
        .thenReturn(expectedList);

    List<MeasureListDTO> actualList =
        measureSetService.getMeasuresByMeasureSetId(MEASURE_SET_ID, true, criteria);

    assertNotNull(actualList);
    assertEquals(1, actualList.size());
    assertEquals("Test Measure", actualList.get(0).getMeasureName());

    verify(measureSetRepository).findMeasuresByMeasureSetId(MEASURE_SET_ID, true, criteria);
  }

  @Test
  void testGetRecentMeasuresByMeasureSetIdReturnsMeasuresInOrder() {
    List<String> measureSetIds = List.of("set1", "set2");

    Measure measure1B = Measure.builder().id("m1b").measureName("Measure 1B").build();
    Measure measure2A = Measure.builder().id("m2a").measureName("Measure 2A").build();

    MeasureListDTO mockedMeasureListDTO1 =
        MeasureListDTO.builder().id("m1a").measureName("Measure 1A").build();
    MeasureListDTO mockedMeasureListDTO2 =
        MeasureListDTO.builder().id("m1b").measureName("Measure 1B").build();
    MeasureListDTO mockedMeasureListDTO3 =
        MeasureListDTO.builder().id("m2a").measureName("Measure 2A").build();

    List<MeasureListDTO> set1Measures = new ArrayList<>();
    set1Measures.add(mockedMeasureListDTO1);
    set1Measures.add(mockedMeasureListDTO2);

    List<MeasureListDTO> set2Measures = new ArrayList<>();
    set2Measures.add(mockedMeasureListDTO3);

    when(measureSetRepository.findMeasuresByMeasureSetId("set1", false, null))
        .thenReturn(set1Measures);
    when(measureSetRepository.findMeasuresByMeasureSetId("set2", false, null))
        .thenReturn(set2Measures);

    when(measureRepository.findById("m1b")).thenReturn(Optional.of(measure1B));
    when(measureRepository.findById("m2a")).thenReturn(Optional.of(measure2A));

    List<Measure> recentMeasures = measureSetService.getRecentMeasuresByMeasureSetId(measureSetIds);

    assertNotNull(recentMeasures);
    assertEquals(2, recentMeasures.size());

    assertTrue(
        recentMeasures.stream()
            .anyMatch(m -> "m1b".equals(m.getId()) && "Measure 1B".equals(m.getMeasureName())));
    assertTrue(
        recentMeasures.stream()
            .anyMatch(m -> "m2a".equals(m.getId()) && "Measure 2A".equals(m.getMeasureName())));

    verify(measureSetRepository).findMeasuresByMeasureSetId("set1", false, null);
    verify(measureSetRepository).findMeasuresByMeasureSetId("set2", false, null);
    verify(measureRepository).findById("m1b");
    verify(measureRepository).findById("m2a");
  }

  @Test
  void testGetRecentMeasuresByMeasureSetIdHandlesEmptyMeasureLists() {
    List<String> measureSetIds = List.of("set1");

    when(measureSetRepository.findMeasuresByMeasureSetId("set1", false, null))
        .thenReturn(List.of());

    List<Measure> recentMeasures = measureSetService.getRecentMeasuresByMeasureSetId(measureSetIds);

    assertNotNull(recentMeasures);
    assertTrue(recentMeasures.isEmpty());

    verify(measureSetRepository).findMeasuresByMeasureSetId("set1", false, null);
    verifyNoInteractions(measureRepository);
  }
}
