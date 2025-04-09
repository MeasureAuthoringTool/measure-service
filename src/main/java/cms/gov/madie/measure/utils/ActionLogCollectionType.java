package cms.gov.madie.measure.utils;

import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureSet;
import gov.cms.madie.models.measure.TestCase;

import java.util.Arrays;

public enum ActionLogCollectionType {
  MEASURE(Measure.class, "measureActionLog"),
  MEASURESET(MeasureSet.class, "measureSetActionLog"),
  TESTCASE(TestCase.class, "testCaseActionLog");

  Class clazz;
  String collectionName;

  ActionLogCollectionType(Class clazz, String collectionName) {
    this.clazz = clazz;
    this.collectionName = collectionName;
  }

  public String getCollectionName() {
    return collectionName;
  }

  public static String getCollectionNameForClazz(Class clazz) {
    return Arrays.stream(ActionLogCollectionType.values())
        .filter(al -> al.clazz.equals(clazz))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("doesn't work"))
        .collectionName;
  }
}
