package cms.gov.madie.measure.services;

import cms.gov.madie.measure.dto.PackageDto;
import gov.cms.madie.models.measure.Measure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QicorePackageServiceTest {
  @Mock private BundleService bundleService;
  @InjectMocks private QicorePackageService qicorePackageService;

  @Test
  void getMeasurePackage() {
    String measurePackageStr = "measure package";
    PackageDto packageDto = PackageDto.builder()
        .fromStorage(false)
        .exportPackage(measurePackageStr.getBytes())
        .build();
    when(bundleService.getMeasureExport(any(Measure.class), anyString()))
        .thenReturn(packageDto);
    PackageDto measurePackage = qicorePackageService.getMeasurePackage(new Measure(), "token");
    byte[] rawPackage = measurePackage.getExportPackage();
    assertThat(new String(rawPackage), is(equalTo(measurePackageStr)));
  }

  @Test
  void testGetQRDA() {
    byte[] qrda = qicorePackageService.getQRDA(new Measure(), "token");
    assertThat(new String(qrda), is(equalTo("")));
  }
}
