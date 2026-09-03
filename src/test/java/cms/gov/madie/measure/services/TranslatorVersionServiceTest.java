package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.MeasureListDTO;
import cms.gov.madie.measure.repositories.MeasureRepository;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslatorVersionServiceTest {

  @Mock private ElmTranslatorClient elmTranslatorClient;
  @Mock private MeasureRepository measureRepository;

  @InjectMocks private TranslatorVersionService translatorVersionService;

  @Test
  void testEnrichWithTranslatorVersionNullList() {
    translatorVersionService.enrichWithTranslatorVersion(null);
    verifyNoInteractions(elmTranslatorClient, measureRepository);
  }

  @Test
  void testEnrichWithTranslatorVersionEmptyList() {
    translatorVersionService.enrichWithTranslatorVersion(Collections.emptyList());
    verifyNoInteractions(elmTranslatorClient, measureRepository);
  }

  @Test
  void testEnrichWithTranslatorVersionDraftMeasure() {
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(true);

    MeasureListDTO dto = new MeasureListDTO();
    dto.setId("m1");
    dto.setModel("QI-Core v4.1.1");
    dto.setMeasureMetaData(metaData);

    when(elmTranslatorClient.getCqlToElmTranslatorVersion("QI-Core v4.1.1")).thenReturn("3.0.0");

    translatorVersionService.enrichWithTranslatorVersion(List.of(dto));

    assertEquals("3.0.0", dto.getTranslatorVersion());
    verify(elmTranslatorClient).getCqlToElmTranslatorVersion("QI-Core v4.1.1");
    verifyNoInteractions(measureRepository);
  }

  @Test
  void testEnrichWithTranslatorVersionVersionedMeasure() {
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(false);

    MeasureListDTO dto = new MeasureListDTO();
    dto.setId("m2");
    dto.setModel("QDM v5.6");
    dto.setMeasureMetaData(metaData);

    Measure measure = new Measure();
    measure.setElmJson("{ \"library\": { \"annotation\": { \"translatorVersion\": \"2.9.0\" } } }");

    when(measureRepository.findById("m2")).thenReturn(Optional.of(measure));
    when(elmTranslatorClient.getTranslatorVersionFromElmJson(measure.getElmJson()))
        .thenReturn("2.9.0");

    translatorVersionService.enrichWithTranslatorVersion(List.of(dto));

    assertEquals("2.9.0", dto.getTranslatorVersion());
    verify(measureRepository).findById("m2");
    verify(elmTranslatorClient).getTranslatorVersionFromElmJson(measure.getElmJson());
  }

  @Test
  void testDoesNotReplaceTranslatorVersionAlreadyPresent() {
    MeasureListDTO dto = new MeasureListDTO();
    dto.setTranslatorVersion("3.10.0");

    translatorVersionService.enrichWithTranslatorVersion(List.of(dto));

    assertEquals("3.10.0", dto.getTranslatorVersion());
    verifyNoInteractions(elmTranslatorClient, measureRepository);
  }

  @Test
  void testEnrichWithTranslatorVersionVersionedMeasureNotFound() {
    MeasureMetaData metaData = new MeasureMetaData();
    metaData.setDraft(false);

    MeasureListDTO dto = new MeasureListDTO();
    dto.setId("m3");
    dto.setMeasureMetaData(metaData);

    when(measureRepository.findById("m3")).thenReturn(Optional.empty());

    translatorVersionService.enrichWithTranslatorVersion(List.of(dto));

    assertNull(dto.getTranslatorVersion());
    verify(measureRepository).findById("m3");
    verifyNoMoreInteractions(elmTranslatorClient);
  }
}
