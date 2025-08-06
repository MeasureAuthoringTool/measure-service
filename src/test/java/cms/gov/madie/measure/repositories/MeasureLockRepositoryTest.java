package cms.gov.madie.measure.repositories;

import cms.gov.madie.measure.dto.MeasureLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MeasureLockRepositoryTest {

  private MeasureLockRepository repository;

  private final String measureId = "measure-123";
  private final String harpId = "user-456";

  @BeforeEach
  void setup() {
    repository = mock(MeasureLockRepository.class);
  }

  @Test
  void testFindByMeasureId() {
    MeasureLock lock =
        MeasureLock.builder().measureId(measureId).lockedBy(harpId).lockedAt(Instant.now()).build();

    when(repository.findByMeasureId(measureId)).thenReturn(Optional.of(lock));

    Optional<MeasureLock> found = repository.findByMeasureId(measureId);
    assertThat(found).isPresent();
    assertThat(found.get().getMeasureId()).isEqualTo(measureId);
    assertThat(found.get().getLockedBy()).isEqualTo(harpId);
  }
}
