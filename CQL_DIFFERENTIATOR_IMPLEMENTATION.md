# CQL Differentiator Implementation Summary

## Overview
Converted the JavaScript differentiator logic to a Java service that can be used via REST API to compare CQL content between two measures. The implementation facilitates meaningful diffs by normalizing and reordering code blocks based on similarity.

## What Was Implemented

### 1. Dependencies Added
- **Apache Commons Text** (`commons-text:1.11.0`) - Provides Levenshtein edit distance calculation
  - Added to `pom.xml`

### 2. DTOs Created

#### `CqlFileComparison.java`
- Represents a single file comparison
- Fields:
  - `oldFileName` - Filename from old measure (or "not found" for new files)
  - `newFileName` - Filename from new measure
  - `oldText` - Normalized text content from old measure
  - `newText` - Normalized and reordered text content from new measure

#### `CqlDiffResult.java`
- Top-level result object returned by the API
- Fields:
  - `comparisons` - List of CqlFileComparison objects
  - `oldMeasureId` - ID of the old measure
  - `newMeasureId` - ID of the new measure

### 3. Service Created

#### `CqlDifferentiatorService.java`
Core service implementing the differentiator logic with the following key methods:

**Public API:**
- `compareLibraries(oldLibraries, newLibraries, autoReorder)` - Main entry point for comparing CQL content

**Text Processing:**
- `normalizeText()` - Removes carriage returns (`\r`) and converts tabs to 2 spaces
- `reorderNewLibrary()` - Reorders new CQL to match old structure by splitting on "context Patient\n\n" delimiter

**Similarity Matching:**
- `mapByEditDistance()` - Maps old strings to new strings using Levenshtein distance
  - For CQL define statements: uses weighted sum (50% label match + 50% full statement match)
  - For other content: uses simple edit distance
  - Uses greedy matching algorithm to avoid reusing matched strings
  
- `calculateDistance()` - Computes distance between two strings with special handling for CQL define statements

**Library Mapping:**
- `createLibraryMap()` - Maps old filenames to new filenames based on similarity
  - Handles cases where new measure has more files (creates "NA-{index}" entries)
  - Filters out MACOSX temp files

**Reconstruction:**
- `rebuildFromMapping()` - Rebuilds text maintaining old order and appending unmatched new content

### 4. REST Endpoint Added

#### `GET /measures/{oldMeasureId}/compare/{newMeasureId}`

**Parameters:**
- `oldMeasureId` (path) - ID of the old measure to compare from
- `newMeasureId` (path) - ID of the new measure to compare to  
- `autoReorder` (query, optional, default: true) - Whether to auto-reorder new CQL

**Returns:**
- `CqlDiffResult` - Contains normalized/reordered text ready for diff display

**Behavior:**
1. Fetches both measures from database
2. Validates user has access to both measures
3. Extracts CQL content (using `cqlLibraryName.cql` as filename)
4. Calls `CqlDifferentiatorService.compareLibraries()`
5. Returns result with normalized and reordered text

**Security:**
- Uses existing Spring Security Principal authentication
- Leverages `MeasureService.findMeasureById()` which includes ACL checks

### 5. Tests Created

#### `CqlDifferentiatorServiceTest.java`
Comprehensive unit tests covering:
- ✅ Simple CQL comparison with reordering
- ✅ Comparison without reordering
- ✅ Handling new files without matches in old measure
- ✅ Empty libraries
- ✅ Normalization of tabs and carriage returns

All tests passing (5/5).

## Key Features

### 1. Text Normalization
- Removes carriage returns for consistency
- Converts tabs to 2 spaces (handles 2019/2020 format differences)

### 2. Intelligent Reordering
- Splits CQL by "context Patient\n\n" delimiter
- Splits body into paragraphs (separated by "\n\n")
- Uses Levenshtein distance to match similar blocks
- Reorders new measure to align with old measure structure
- Appends unmatched new content at the end

### 3. Smart Define Statement Matching
For CQL define statements like `define "Population":`, the algorithm:
1. Extracts the define label (e.g., "Population")
2. Calculates distance of both the label and full statement
3. If labels match exactly → distance = 0 (perfect match)
4. Otherwise → weighted sum: 50% label + 50% full statement
5. This ensures define statements with same names match even if content differs

### 4. File Name Mapping
- Maps old filenames to new filenames based on content similarity
- Handles different numbers of files between measures
- Creates "NA-{index}" entries for new files without matches
- Display shows "not found" instead of "NA-{index}" in results

## Usage Example

```bash
# Compare two measures
GET /api/measures/measure123/compare/measure456?autoReorder=true

# Response:
{
  "comparisons": [
    {
      "oldFileName": "MyMeasure.cql",
      "newFileName": "MyMeasure.cql",
      "oldText": "library MyMeasure version '1.0.0'\n\ncontext Patient\n\n...",
      "newText": "library MyMeasure version '2.0.0'\n\ncontext Patient\n\n..."
    }
  ],
  "oldMeasureId": "measure123",
  "newMeasureId": "measure456"
}
```

The frontend can then use the returned `oldText` and `newText` to generate a visual diff, with the `newText` being reordered to minimize spurious differences.

## Technical Notes

### Differences from JavaScript Version
1. **No ZIP handling** - Works with pre-extracted text content only (from MongoDB)
2. **Synchronous** - No async/Promise complications like the JS version had
3. **Type safety** - Strong typing with DTOs and service interfaces
4. **Better testing** - Comprehensive unit test coverage
5. **Spring integration** - Uses existing security and service layer

### Future Enhancements (Optional)
1. Support multiple CQL libraries per measure (if measures start having multiple .cql files)
2. Add caching for frequently compared measures
3. Support different diff formats (unified, side-by-side)
4. Add metrics/logging for monitoring usage
5. Support comparison of more than 2 measures at once

## Files Modified/Created

### New Files:
- `src/main/java/cms/gov/madie/measure/dto/CqlFileComparison.java`
- `src/main/java/cms/gov/madie/measure/dto/CqlDiffResult.java`
- `src/main/java/cms/gov/madie/measure/services/CqlDifferentiatorService.java`
- `src/test/java/cms/gov/madie/measure/services/CqlDifferentiatorServiceTest.java`

### Modified Files:
- `pom.xml` - Added Apache Commons Text dependency
- `src/main/java/cms/gov/madie/measure/resources/MeasureController.java` - Added CqlDifferentiatorService injection and compareMeasures endpoint

## Build Status
✅ Compilation successful
✅ All tests passing (5/5)
✅ No checkstyle violations

