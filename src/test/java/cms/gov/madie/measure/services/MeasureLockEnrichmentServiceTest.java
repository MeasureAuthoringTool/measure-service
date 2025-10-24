package cms.gov.madie.measure.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cms.gov.madie.measure.locks.MeasureLock;
import cms.gov.madie.measure.repositories.MeasureLockRepository;
import gov.cms.madie.models.measure.Measure;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasureLockEnrichmentServiceTest {

  @Mock private MeasureLockRepository measureLockRepository;
  @Mock private TestCaseLockService testCaseLockService;

  @InjectMocks private MeasureLockEnrichmentService service;

  private Measure measure;
  private MeasureLock lock;
  private final String measureId = "measure-123";
  private final String currentUser = "current.user";
  private final String otherUser = "other.user";

  @BeforeEach
  void setUp() {
    measure = Measure.builder().id(measureId).measureName("Test Measure").build();

    lock =
        MeasureLock.builder()
            .id("lock-1")
            .measureId(measureId)
            .lockedBy(otherUser)
            .lockedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(900))
            .build();
  }

  @Test
  void testEnrichMeasureWithLockInfo_MeasureLockedByOtherUser() {
    when(measureLockRepository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));
    when(testCaseLockService.isAnyTestCaseLockedByOthers(measureId, currentUser)).thenReturn(false);

    service.enrichMeasureWithLockInfo(measure, currentUser);

    assertNotNull(measure.getMeasureLock());
    assertEquals(otherUser, measure.getMeasureLock().getLockedBy());
    assertEquals(measureId, measure.getMeasureLock().getMeasureId());
    assertFalse(measure.isHasLockedTestCases());
  }

  @Test
  void testEnrichMeasureWithLockInfo_MeasureLockedByCurrentUser() {
    lock.setLockedBy(currentUser);
    when(measureLockRepository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));
    when(testCaseLockService.isAnyTestCaseLockedByOthers(measureId, currentUser)).thenReturn(false);

    service.enrichMeasureWithLockInfo(measure, currentUser);

    // Lock should not be set when current user owns the lock
    assertNull(measure.getMeasureLock());
    assertFalse(measure.isHasLockedTestCases());
  }

  @Test
  void testEnrichMeasureWithLockInfo_NoLock() {
    when(measureLockRepository.findByMeasureId(measureId)).thenReturn(Optional.empty());
    when(testCaseLockService.isAnyTestCaseLockedByOthers(measureId, currentUser)).thenReturn(false);

    service.enrichMeasureWithLockInfo(measure, currentUser);

    assertNull(measure.getMeasureLock());
    assertFalse(measure.isHasLockedTestCases());
  }

  @Test
  void testEnrichMeasureWithLockInfo_TestCasesLockedByOthers() {
    when(measureLockRepository.findByMeasureId(measureId)).thenReturn(Optional.empty());
    when(testCaseLockService.isAnyTestCaseLockedByOthers(measureId, currentUser)).thenReturn(true);

    service.enrichMeasureWithLockInfo(measure, currentUser);

    assertNull(measure.getMeasureLock());
    assertTrue(measure.isHasLockedTestCases());
  }

  @Test
  void testEnrichMeasureWithLockInfo_BothMeasureAndTestCasesLocked() {
    when(measureLockRepository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));
    when(testCaseLockService.isAnyTestCaseLockedByOthers(measureId, currentUser)).thenReturn(true);

    service.enrichMeasureWithLockInfo(measure, currentUser);

    assertNotNull(measure.getMeasureLock());
    assertEquals(otherUser, measure.getMeasureLock().getLockedBy());
    assertTrue(measure.isHasLockedTestCases());
  }

  @Test
  void testEnrichMeasureWithLockInfo_NullMeasure() {
    // Should not throw exception
    assertDoesNotThrow(() -> service.enrichMeasureWithLockInfo(null, currentUser));

    // Verify no repository calls were made
    verify(measureLockRepository, never()).findByMeasureId(anyString());
    verify(testCaseLockService, never()).isAnyTestCaseLockedByOthers(anyString(), anyString());
  }
}
