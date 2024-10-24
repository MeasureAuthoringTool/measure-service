package cms.gov.madie.measure.services;

import cms.gov.madie.measure.config.QdmServiceConfig;
import cms.gov.madie.measure.dto.PackageDto;
import cms.gov.madie.measure.dto.qrda.QrdaRequestDTO;
import cms.gov.madie.measure.exceptions.HQMFServiceException;
import cms.gov.madie.measure.exceptions.InternalServerException;
import cms.gov.madie.measure.repositories.ExportRepository;
import gov.cms.madie.models.cqm.CqmMeasure;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.models.measure.Measure;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class QdmPackageService implements PackageService {
  private final QdmServiceConfig qdmServiceConfig;
  private final RestTemplate qdmServiceRestTemplate;
  private final ExportRepository repository;

  @Override
  public PackageDto getMeasurePackage(Measure measure, String accessToken) {
    if (!measure.getMeasureMetaData().isDraft()) {
      Optional<Export> savedExport = repository.findByMeasureId(measure.getId());
      if (savedExport.isPresent()) {
        log.info("returning persisted export for measure [{}]", measure.getId());
        return PackageDto.builder()
            .fromStorage(true)
            .exportPackage(savedExport.get().getPackageData())
            .build();
      }
    }
    URI uri = URI.create(qdmServiceConfig.getBaseUrl() + qdmServiceConfig.getCreatePackageUrn());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<Measure> entity = new HttpEntity<>(measure, headers);
    try {
      log.info("Requesting measure package for measure [{}] from QDM service", measure.getId());
      byte[] exportPackage =
          qdmServiceRestTemplate.exchange(uri, HttpMethod.PUT, entity, byte[].class).getBody();

      return PackageDto.builder().exportPackage(exportPackage).fromStorage(false).build();

    } catch (HttpClientErrorException ex) {
      String errorMessage = ex.getResponseBodyAsString();

      log.error("Error from QDM service for measure [{}]: {}", measure.getId(), errorMessage);
      if (ex.getMessage().contains("HQMF")) {
        throw new HQMFServiceException();
      }
      throw new InternalServerException("QDM service error: " + errorMessage);

    } catch (RestClientException ex) {
      log.error(
          "An error occurred while creating package for QDM measure: {}. "
              + "Please check QDM service logs for more information.",
          measure.getId(),
          ex);

      throw new InternalServerException(
          "An unexpected error occurred while creating a measure package." + ex.getMessage());
    }
  }

  @Override
  public byte[] getQRDA(QrdaRequestDTO qrdaRequestDTO, String accessToken) {
    URI uri = URI.create(qdmServiceConfig.getBaseUrl() + qdmServiceConfig.getCreateQrdaUrn());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<QrdaRequestDTO> entity = new HttpEntity<>(qrdaRequestDTO, headers);
    try {
      log.info(
          "requesting QRDA for measure [{}] from qdm service", qrdaRequestDTO.getMeasure().getId());
      return qdmServiceRestTemplate.exchange(uri, HttpMethod.PUT, entity, byte[].class).getBody();
    } catch (RestClientException ex) {
      log.error(
          "An error occurred while creating QRDA for QDM measure: "
              + qrdaRequestDTO.getMeasure().getId()
              + ", please check qdm service logs for more information",
          ex);
      throw new InternalServerException("An error occurred while creating a QRDA.");
    }
  }

  public CqmMeasure convertCqm(Measure measure, String accessToken) {
    URI uri =
        URI.create(qdmServiceConfig.getBaseUrl() + qdmServiceConfig.getRetrieveCqmMeasureUrn());
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, accessToken);
    headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
    headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    HttpEntity<Measure> entity = new HttpEntity<>(measure, headers);
    try {
      log.info("requesting CqmConversion for measure [{}] from qdm service", measure.getId());
      ResponseEntity<CqmMeasure> result =
          qdmServiceRestTemplate.exchange(uri, HttpMethod.PUT, entity, CqmMeasure.class);
      if (result.getStatusCode().equals(HttpStatus.OK)) {
        return result.getBody();
      } else {

        log.error(
            "An error occurred while converting QdmMeasure to CqmMeasure: "
                + measure.getId()
                + ", please check qdm service logs for more information");
        throw new InternalServerException("An error occurred while converting CqmMeasure.");
      }
    } catch (RestClientException ex) {
      log.error(
          "An error occurred while converting QdmMeasure to CqmMeasure: "
              + measure.getId()
              + ", please check qdm service logs for more information",
          ex);
      throw new InternalServerException("An error occurred while converting CqmMeasure.");
    }
  }
}
