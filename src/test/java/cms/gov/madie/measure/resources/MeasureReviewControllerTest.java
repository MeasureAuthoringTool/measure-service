package cms.gov.madie.measure.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.dto.MeasureSearchCriteria;
import cms.gov.madie.measure.services.MeasureReviewService;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.measure.MeasureReview;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MeasureReviewControllerTest {

  @InjectMocks private MeasureReviewController controller;

  @Mock private MeasureReviewService measureReviewService;

  @Captor private ArgumentCaptor<MeasureReview> reviewCaptor;

  @Captor private ArgumentCaptor<Pageable> pageableCaptor;

  private Principal principal;
  private MeasureReview review;

  @BeforeEach
  void setUp() {
    principal = mock(Principal.class);
    review =
        MeasureReview.builder()
            .id("review-1")
            .measureId("m1")
            .measureSetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("Looks good")
            .build();
  }

  @Test
  void createReviewReturnsCreated() {
    when(principal.getName()).thenReturn("test.user");
    when(measureReviewService.createReview(any(MeasureReview.class), anyString()))
        .thenReturn(review);

    ResponseEntity<MeasureReview> response = controller.createReview("m1", review, principal);

    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("review-1", response.getBody().getId());
    verify(measureReviewService).createReview(reviewCaptor.capture(), anyString());
    assertEquals("m1", reviewCaptor.getValue().getMeasureId());
  }

  @Test
  void createReviewSetsMeasureIdFromPath() {
    when(principal.getName()).thenReturn("test.user");
    MeasureReview payload = MeasureReview.builder().status(ReviewStatus.READY_FOR_REVIEW).build();
    when(measureReviewService.createReview(any(MeasureReview.class), anyString()))
        .thenReturn(review);

    controller.createReview("path-measure", payload, principal);

    verify(measureReviewService).createReview(reviewCaptor.capture(), anyString());
    assertEquals("path-measure", reviewCaptor.getValue().getMeasureId());
  }

  @Test
  void updateReviewReturnsOk() {
    when(principal.getName()).thenReturn("test.user");
    when(measureReviewService.updateReview(anyString(), any(MeasureReview.class), anyString()))
        .thenReturn(review);

    ResponseEntity<MeasureReview> response = controller.updateReview("m1", review, principal);

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("review-1", response.getBody().getId());
    verify(measureReviewService).updateReview("m1", review, "test.user");
  }

  @Test
  void getReviewByMeasureIdReturnsOk() {
    when(measureReviewService.getReviewByMeasureId("m1")).thenReturn(review);

    ResponseEntity<MeasureReview> response = controller.getReviewByMeasureId("m1");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("m1", response.getBody().getMeasureId());
  }

  @Test
  void getReviewsByMeasureSetIdReturnsList() {
    when(measureReviewService.getReviewsByMeasureSetId("set-1")).thenReturn(List.of(review));

    ResponseEntity<List<MeasureReview>> response = controller.getReviewsByMeasureSetId("set-1");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
  }

  @Test
  void searchMeasuresInReviewReturnsMeasures() {
    when(principal.getName()).thenReturn("Test.User");
    MeasureListDTO measureInReview =
        MeasureListDTO.builder().id("m1").measureName("Measure 1").reviewStatus("Ready").build();
    MeasureSearchCriteria searchCriteria =
        MeasureSearchCriteria.builder().searchField("Measure").build();
    when(measureReviewService.getMeasuresInReview(
            eq(searchCriteria),
            eq(List.of(OwnershipType.ALL)),
            any(Pageable.class),
            eq("test.user")))
        .thenReturn(new PageImpl<>(List.of(measureInReview)));

    ResponseEntity<Page<MeasureListDTO>> response =
        controller.searchMeasuresInReview(
            principal, searchCriteria, List.of(OwnershipType.ALL), 10, 0, "lastModifiedAt", "DESC");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getContent().size());
    assertEquals("m1", response.getBody().getContent().get(0).getId());
    verify(measureReviewService)
        .getMeasuresInReview(
            eq(searchCriteria),
            eq(List.of(OwnershipType.ALL)),
            pageableCaptor.capture(),
            eq("test.user"));
    Pageable pageable = pageableCaptor.getValue();
    assertEquals(0, pageable.getPageNumber());
    assertEquals(10, pageable.getPageSize());
    assertEquals(Sort.by(Sort.Direction.DESC, "lastModifiedAt"), pageable.getSort());
  }

  @Test
  void searchMeasuresInReviewForTheAssignedScopeReturnsTheReviewersMeasures() {
    when(principal.getName()).thenReturn("Test.User");
    MeasureListDTO assigned = MeasureListDTO.builder().id("m1").reviewStatus("In Progress").build();
    MeasureSearchCriteria searchCriteria =
        MeasureSearchCriteria.builder().searchField("Measure").build();
    when(measureReviewService.getMeasuresInReview(
            eq(searchCriteria),
            eq(List.of(OwnershipType.OWNED)),
            any(Pageable.class),
            eq("test.user")))
        .thenReturn(new PageImpl<>(List.of(assigned)));

    ResponseEntity<Page<MeasureListDTO>> response =
        controller.searchMeasuresInReview(
            principal,
            searchCriteria,
            List.of(OwnershipType.OWNED),
            10,
            0,
            "lastModifiedAt",
            "DESC");

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getContent().size());
    assertEquals("m1", response.getBody().getContent().get(0).getId());
    verify(measureReviewService)
        .getMeasuresInReview(
            eq(searchCriteria),
            eq(List.of(OwnershipType.OWNED)),
            pageableCaptor.capture(),
            eq("test.user"));
    assertEquals(
        Sort.by(Sort.Direction.DESC, "lastModifiedAt"), pageableCaptor.getValue().getSort());
  }
}
