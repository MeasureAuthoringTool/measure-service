package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.clients.UserServiceClient;
import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.UserMeasuresDTO;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class UserMeasureExportServiceTest {

  @Mock private MongoTemplate mongoTemplate;
  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private UserMeasureExportService service;

  @Captor private ArgumentCaptor<List<String>> ownerIdsCaptor;
  @Captor private ArgumentCaptor<Aggregation> aggregationCaptor;

  // ---- fixtures -----------------------------------------------------------

  private void stubAggregate(List<MeasureListDTO> results) {
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(Measure.class), eq(MeasureListDTO.class)))
        .thenReturn(new AggregationResults<>(results, new Document()));
  }

  private MeasureListDTO measure(
      String setId, String name, String owner, AclSpecification... acls) {
    MeasureSet measureSet =
        MeasureSet.builder()
            .measureSetId(setId)
            .owner(owner)
            .acls(acls.length == 0 ? new ArrayList<>() : new ArrayList<>(List.of(acls)))
            .build();
    return MeasureListDTO.builder()
        .measureSetId(setId)
        .measureName(name)
        .measureSet(measureSet)
        .build();
  }

  private MeasureListDTO measureWithoutSet(String name) {
    return MeasureListDTO.builder().measureName(name).build();
  }

  private AclSpecification sharedWith(String userId) {
    return AclSpecification.builder().userId(userId).roles(Set.of(RoleEnum.SHARED_WITH)).build();
  }

  private UserDetailsDto userDetails(String harpId, String firstName, String lastName) {
    return UserDetailsDto.builder().harpId(harpId).firstName(firstName).lastName(lastName).build();
  }

  // ---- tests --------------------------------------------------------------

  @Test
  void assemblesOwnedAndSharedMeasuresForAllUsersWhenHarpIdsNull() {
    MeasureListDTO owned = measure("set-1", "Measure One", "OwnerA", sharedWith("UserB"));
    MeasureListDTO other = measure("set-2", "Measure Two", "OwnerB");
    stubAggregate(List.of(owned, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(
            Map.of(
                "ownera", userDetails("ownera", "Alice", "Owner"),
                "ownerb", userDetails("ownerb", "Bob", "Owner")));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    assertEquals(3, result.size());

    // owner buckets
    assertSame(owned, result.get("ownera").getOwnedMeasures().get(0));
    assertTrue(result.get("ownera").getSharedMeasures().isEmpty());
    assertSame(other, result.get("ownerb").getOwnedMeasures().get(0));

    // shared bucket derived from the acl
    assertSame(owned, result.get("userb").getSharedMeasures().get(0));
    assertTrue(result.get("userb").getOwnedMeasures().isEmpty());

    // display names resolved from the single user-service lookup
    assertEquals("Alice Owner", owned.getOwnerDisplayName());
    assertEquals("Bob Owner", other.getOwnerDisplayName());

    // owner ids are lower-cased and de-duplicated before the lookup
    verify(userServiceClient).getBulkUserDetails(ownerIdsCaptor.capture());
    assertEquals(2, ownerIdsCaptor.getValue().size());
    assertTrue(ownerIdsCaptor.getValue().containsAll(List.of("ownera", "ownerb")));
  }

  @Test
  void filtersToRequestedHarpIdsIgnoringCase() {
    MeasureListDTO owned = measure("set-1", "Measure One", "OwnerA", sharedWith("UserB"));
    MeasureListDTO other = measure("set-2", "Measure Two", "OwnerB");
    stubAggregate(List.of(owned, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(List.of("ownerA"));

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownera"));
    assertEquals(1, result.get("ownera").getOwnedMeasures().size());
    assertSame(owned, result.get("ownera").getOwnedMeasures().get(0));
    assertTrue(result.get("ownera").getSharedMeasures().isEmpty());
    // the other owner and the shared user are not in the requested set
    assertFalse(result.containsKey("ownerb"));
    assertFalse(result.containsKey("userb"));
  }

  @Test
  void includesUserReachedOnlyThroughSharedAcl() {
    MeasureListDTO owned = measure("set-1", "Measure One", "OwnerA", sharedWith("UserB"));
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(List.of("UserB"));

    assertEquals(1, result.size());
    UserMeasuresDTO userB = result.get("userb");
    assertNotNull(userB);
    assertTrue(userB.getOwnedMeasures().isEmpty());
    assertEquals(1, userB.getSharedMeasures().size());
    assertSame(owned, userB.getSharedMeasures().get(0));
  }

  @Test
  void skipsMeasuresWithNullMeasureSet() {
    MeasureListDTO orphan = measureWithoutSet("Orphan");
    MeasureListDTO other = measure("set-2", "Measure Two", "OwnerB");
    stubAggregate(List.of(orphan, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownerb", userDetails("ownerb", "Bob", "Owner")));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownerb"));
    // the measure without a measureSet contributes no owner id to the lookup
    verify(userServiceClient).getBulkUserDetails(List.of("ownerb"));
  }

  @Test
  void fallsBackToHarpIdWhenOwnerDetailsMissing() {
    MeasureListDTO owned = measure("set-1", "Measure One", "OwnerA");
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of());

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    assertEquals("ownera", owned.getOwnerDisplayName());
    assertSame(owned, result.get("ownera").getOwnedMeasures().get(0));
  }

  @Test
  void fallsBackToHarpIdWhenResolvedFullNameIsBlank() {
    MeasureListDTO owned = measure("set-1", "Measure One", "OwnerA");
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", " ", " ")));

    service.getMeasuresForUsers(null);

    assertEquals("ownera", owned.getOwnerDisplayName());
  }

  @Test
  void doesNotCallUserServiceWhenNoMeasureHasOwner() {
    MeasureListDTO shared = measure("set-1", "Measure One", "", sharedWith("UserB"));
    stubAggregate(List.of(shared));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    verify(userServiceClient, never()).getBulkUserDetails(any());
    assertEquals(1, result.size());
    assertEquals(1, result.get("userb").getSharedMeasures().size());
    assertTrue(result.get("userb").getOwnedMeasures().isEmpty());
  }

  @Test
  void ignoresAclsWithoutSharedWithRoleOrBlankUserId() {
    AclSpecification notShared = AclSpecification.builder().userId("UserC").roles(Set.of()).build();
    AclSpecification nullRoles = AclSpecification.builder().userId("UserD").roles(null).build();
    AclSpecification blankUser =
        AclSpecification.builder().userId(" ").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    MeasureListDTO owned =
        measure("set-1", "Measure One", "OwnerA", notShared, nullRoles, blankUser);
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownera"));
    assertFalse(result.containsKey("userc"));
    assertFalse(result.containsKey("userd"));
  }

  @Test
  void returnsEmptyMapWhenNoMeasuresFound() {
    stubAggregate(List.of());

    Map<String, UserMeasuresDTO> result = service.getMeasuresForUsers(null);

    assertTrue(result.isEmpty());
    verify(userServiceClient, never()).getBulkUserDetails(any());
  }

  @Test
  void buildsLatestPerFamilyAggregationPipeline() {
    stubAggregate(List.of());

    service.getMeasuresForUsers(null);

    verify(mongoTemplate)
        .aggregate(aggregationCaptor.capture(), eq(Measure.class), eq(MeasureListDTO.class));
    // match -> project -> lookup -> unwind -> sort -> group -> replaceRoot
    assertEquals(7, aggregationCaptor.getValue().getPipeline().getOperations().size());
  }
}
