package cms.gov.madie.measure.validations;

import cms.gov.madie.measure.exceptions.InvalidMeasureObservationException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.MeasureObservation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CqlObservationFunctionService {

  public void validateObservationFunctions(Group group, String elmJson) throws JacksonException {
    Map<String, String> observationsToValidPopBasis = mapObservationsToValidPopBasis(elmJson);

    List<MeasureObservation> observations = group.getMeasureObservations();

    if (observationsToValidPopBasis.isEmpty()) {
      if (!CollectionUtils.isEmpty(observations)) {
        throw new InvalidMeasureObservationException(
            "Measure CQL does not have observation definition");
      } else {
        return; // observations are optional for scoring types other than Continuous Variable.
      }
    }

    String groupPopulationBasis = group.getPopulationBasis().replaceAll("\\s", "");

    if (CollectionUtils.isNotEmpty(observations)) {
      observations.forEach(
          observation -> {
            if (StringUtils.isNotBlank(observation.getDefinition())) {
              String observationValidPopBasis =
                  observationsToValidPopBasis.get(observation.getDefinition());
              if (!StringUtils.equalsIgnoreCase(observationValidPopBasis, groupPopulationBasis)) {
                if ("boolean".equalsIgnoreCase(groupPopulationBasis)) {
                  throw new InvalidReturnTypeException(
                      "Selected observation function '%s' can not have parameters",
                      observation.getDefinition());
                }
                throw new InvalidReturnTypeException(
                    "Selected observation function must have exactly one parameter of type '%s'",
                    groupPopulationBasis);
              }
            }
          });
    }
  }

  /**
   * This method generates the map of cql functions & their return types.
   *
   * @param elmJson
   * @return
   * @throws JacksonException
   */
  private Map<String, String> mapObservationsToValidPopBasis(String elmJson)
      throws JacksonException {
    // Determine which Population Basis the MO would be valid against.
    Map<String, String> observationPopBasis = new HashMap<>();
    if (StringUtils.isEmpty(elmJson)) {
      return observationPopBasis;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(elmJson);
    ArrayNode allDefinitions = (ArrayNode) rootNode.get("library").get("statements").get("def");
    for (JsonNode node : allDefinitions) {
      if (node.get("type") != null && "FunctionDef".equals(node.get("type").asText())) {
        int numberOfOperands = node.get("operand").size();
        Iterator<JsonNode> operandIterator = node.get("operand").iterator();
        // Non-Boolean Population Basis require MO's with exactly one parameter matching the Pop
        // Basis.
        if (numberOfOperands == 1) {
          JsonNode currentOperandDetails = operandIterator.next();
          if (currentOperandDetails.get("operandTypeSpecifier").get("name") != null
              && currentOperandDetails
                  .get("operandTypeSpecifier")
                  .get("type")
                  .asText()
                  .equals("NamedTypeSpecifier")) {
            String operandTypeSpecifierName =
                currentOperandDetails.get("operandTypeSpecifier").get("name").asText();
            if (!(operandTypeSpecifierName.split("}")[1].equalsIgnoreCase("Boolean"))) {
              observationPopBasis.put(
                  node.get("name").asText(), operandTypeSpecifierName.split("}")[1]);
            }
          }
          // Boolean Population Basis require MO's with no parameters
        } else if (numberOfOperands < 1) {
          observationPopBasis.put(node.get("name").asText(), "Boolean");
          // Not a valid MO against any Population Basis
        }
        observationPopBasis.putIfAbsent(node.get("name").asText(), "NA");
      }
    }
    return observationPopBasis;
  }

  public void validateObservationFunctionsForQdm(
      Group group, String elmJson, boolean patientBasis, String cqlDefinitionReturnType)
      throws JacksonException {
    Map<String, String> observationsToValidPopBasis = mapObservationsToValidPopBasis(elmJson);

    List<MeasureObservation> observations = group.getMeasureObservations();

    if (observationsToValidPopBasis.isEmpty()) {
      if (!CollectionUtils.isEmpty(observations)) {
        throw new InvalidMeasureObservationException(
            "Measure CQL does not have observation definition");
      } else {
        return; // observations are optional for scoring types other than Continuous Variable.
      }
    }

    if (CollectionUtils.isNotEmpty(observations)) {
      observations.forEach(
          observation -> {
            if (StringUtils.isNotBlank(observation.getDefinition())) {
              String observationValidPopBasis =
                  observationsToValidPopBasis.get(observation.getDefinition());

              if (patientBasis
                  && !StringUtils.equalsIgnoreCase(observationValidPopBasis, "boolean")) {
                throw new InvalidReturnTypeException(
                    "Selected observation function '%s' can not have parameters",
                    observation.getDefinition());
              } else if (!patientBasis
                  && !StringUtils.equalsIgnoreCase(
                      observationValidPopBasis, cqlDefinitionReturnType)) {
                throw new InvalidReturnTypeException(
                    "Selected observation function must have exactly one parameter of type '%s'",
                    String.valueOf(patientBasis));
              }
            }
          });
    }
  }
}
