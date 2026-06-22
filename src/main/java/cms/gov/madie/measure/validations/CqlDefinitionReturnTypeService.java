package cms.gov.madie.measure.validations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import gov.cms.madie.models.measure.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import cms.gov.madie.measure.exceptions.InvalidFhirGroupException;
import cms.gov.madie.measure.exceptions.InvalidGroupException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeException;
import cms.gov.madie.measure.exceptions.InvalidReturnTypeForQdmException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CqlDefinitionReturnTypeService {

  /**
   * Validate cql definition return types of a group against population basis. Group can have cql
   * definitions for populations, stratifications and observations
   */
  public void validateCqlDefinitionReturnTypes(Group group, String elmJson)
      throws JacksonException {
    if (group.getScoring().equals(MeasureScoring.COMPOSITE.toString())) {
      return;
    }
    Map<String, String> cqlDefinitionReturnTypes = getCqlDefinitionReturnTypes(elmJson);
    if (cqlDefinitionReturnTypes.isEmpty()) {
      throw new IllegalArgumentException("No definitions found.");
    }
    if (StringUtils.isBlank(group.getPopulationBasis())
        || CollectionUtils.isEmpty(group.getMeasureGroupTypes())) {
      throw new InvalidFhirGroupException();
    }

    List<Population> populations = group.getPopulations();
    String populationBasis = group.getPopulationBasis().replaceAll("\\s", "");
    if (CollectionUtils.isNotEmpty(populations)) {
      populations.forEach(
          population -> {
            if (StringUtils.isNotBlank(population.getDefinition())) {
              String returnType = cqlDefinitionReturnTypes.get(population.getDefinition());
              if (!StringUtils.equalsIgnoreCase(returnType, populationBasis)) {
                throw new InvalidReturnTypeException(population.getName().getDisplay());
              }
            }
          });
    }

    List<Stratification> stratifications = group.getStratifications();
    if (CollectionUtils.isNotEmpty(stratifications)) {
      stratifications.forEach(
          stratification -> {
            if (StringUtils.isNotBlank(stratification.getCqlDefinition())) {
              String returnType = cqlDefinitionReturnTypes.get(stratification.getCqlDefinition());
              if (!StringUtils.equalsIgnoreCase(returnType, populationBasis)) {
                throw new InvalidReturnTypeException("Stratification(s)");
              }
            }
          });
    }
  }

  /**
   * This method can handle any type that inherits from DefDescPair and is intended to compare the
   * pair with the CQL Definitions provided in the elmJson.
   *
   * @param sde
   * @param elmJson
   * @return true if the def is contained in the elmJson, false otherwise
   */
  public boolean isDefineInElm(DefDescPair sde, String elmJson) {
    boolean result = false;
    try {
      Map<String, String> cqlDefinitionReturnTypes = getCqlDefinitionReturnTypes(elmJson);
      result = cqlDefinitionReturnTypes.containsKey(sde.getDefinition());
    } catch (JacksonException e) {
      log.error("Error reading elmJson", e);
      result = false;
    }

    return result;
  }

  /**
   * This method generates the map of cql definitions & their return types.
   *
   * @param elmJson
   * @return
   * @throws JacksonException
   */
  private Map<String, String> getCqlDefinitionReturnTypes(String elmJson) throws JacksonException {
    Map<String, String> returnTypes = new HashMap<>();
    if (StringUtils.isEmpty(elmJson)) {
      return returnTypes;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(elmJson);
    ArrayNode allDefinitions = (ArrayNode) rootNode.get("library").get("statements").get("def");
    for (JsonNode node : allDefinitions) {
      if (node.get("resultTypeName") != null) {
        String dataType = node.get("resultTypeName").asText();
        returnTypes.put(node.get("name").asText(), dataType.split("}")[1]);
      } else if (node.get("resultTypeSpecifier") != null) {
        for (JsonNode current : node.get("resultTypeSpecifier").values()) {
          if (current.has("type") && current.get("type").asText().equals("NamedTypeSpecifier")) {
            returnTypes.put(node.get("name").asText(), current.get("name").asText().split("}")[1]);
          }
        }
      }
      returnTypes.putIfAbsent(node.get("name").asText(), "NA");
    }
    return returnTypes;
  }

  public String validateCqlDefinitionReturnTypesForQdm(
      Group group, String elmJson, boolean patientBased) throws JacksonException {
    Map<String, String> cqlDefinitionReturnTypes = getCqlDefinitionReturnTypes(elmJson);
    if (cqlDefinitionReturnTypes.isEmpty()) {
      throw new IllegalArgumentException("No definitions found.");
    }
    List<Population> populations = group.getPopulations();
    if (CollectionUtils.isNotEmpty(populations)) {
      HashSet<String> returnValues = new HashSet<String>();
      populations.forEach(
          population -> {
            if (StringUtils.isNotBlank(population.getDefinition())) {
              String returnType = cqlDefinitionReturnTypes.get(population.getDefinition());
              if (patientBased) {
                if (!StringUtils.equalsIgnoreCase(returnType, "boolean")) {
                  throw new InvalidReturnTypeForQdmException(
                      "For Patient-based Measures, selected definitions must return a Boolean.");
                }
              } else {
                returnValues.add(returnType);
                if (StringUtils.equalsIgnoreCase(returnType, "boolean")) {
                  throw new InvalidReturnTypeForQdmException(
                      "For Episode-based Measures, selected definitions "
                          + "must return a list of the same type (Non-Boolean).");
                }
              }
            }
          });
      validateQDMStratifications(group, cqlDefinitionReturnTypes, patientBased, returnValues);

      if (returnValues.size() > 1) {
        throw new InvalidReturnTypeForQdmException(
            "For Episode-based Measures, "
                + "selected definitions must return a list of the same type  (Non-Boolean).");
      } else if (returnValues.size() == 1) {
        return returnValues.stream().findFirst().get();
      }
    } else {
      throw new InvalidGroupException("Populations are required for a Group.");
    }
    return null;
  }

  private void validateQDMStratifications(
      Group group,
      Map<String, String> cqlDefinitionReturnTypes,
      boolean patientBased,
      HashSet<String> returnValues) {
    List<Stratification> stratifications = group.getStratifications();
    if (CollectionUtils.isNotEmpty(stratifications)) {
      stratifications.forEach(
          stratification -> {
            if (StringUtils.isNotBlank(stratification.getCqlDefinition())) {
              String returnType = cqlDefinitionReturnTypes.get(stratification.getCqlDefinition());
              if (patientBased) {
                if (!StringUtils.equalsIgnoreCase(returnType, "boolean")) {
                  throw new InvalidReturnTypeForQdmException(
                      "For Patient-based Measures, selected definitions must return a Boolean.");
                }
              } else {
                returnValues.add(returnType);
                if (StringUtils.equalsIgnoreCase(returnType, "boolean")) {
                  throw new InvalidReturnTypeForQdmException(
                      "For Episode-based Measures, selected definitions "
                          + "must return a list of the same type (Non-Boolean).");
                }
              }
            }
          });
    }
  }
}
