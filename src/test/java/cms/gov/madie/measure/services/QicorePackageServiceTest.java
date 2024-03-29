package cms.gov.madie.measure.services;

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
    String measurePackage = "measure package";
    when(bundleService.getMeasureExport(any(Measure.class), anyString()))
        .thenReturn(measurePackage.getBytes());
    byte[] rawPackage = qicorePackageService.getMeasurePackage(new Measure(), "token");
    assertThat(new String(rawPackage), is(equalTo(measurePackage)));
  }

  @Test
  void testGetQRDA() {
    byte[] qrda = qicorePackageService.getQRDA(new Measure(), "token");
    assertThat(new String(qrda), is(equalTo("")));
  }
}
