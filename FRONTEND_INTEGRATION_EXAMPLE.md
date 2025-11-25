# Frontend Integration Example

## Using the CQL Differentiator API

### Basic Usage

```javascript
// Fetch comparison between two measures
async function compareMeasures(oldMeasureId, newMeasureId, autoReorder = true) {
  const response = await fetch(
    `/api/measures/${oldMeasureId}/compare/${newMeasureId}?autoReorder=${autoReorder}`,
    {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      }
    }
  );
  
  if (!response.ok) {
    throw new Error(`Failed to compare measures: ${response.statusText}`);
  }
  
  return await response.json();
}

// Example usage
const result = await compareMeasures('measure123', 'measure456');

console.log('Comparison result:', result);
// {
//   comparisons: [
//     {
//       oldFileName: "MyMeasure.cql",
//       newFileName: "MyMeasure.cql", 
//       oldText: "library MyMeasure version '1.0.0'...",
//       newText: "library MyMeasure version '2.0.0'..."
//     }
//   ],
//   oldMeasureId: "measure123",
//   newMeasureId: "measure456"
// }
```

### Integration with Diff Library

```javascript
import * as Diff from 'diff';

async function displayMeasureDiff(oldMeasureId, newMeasureId) {
  // Get normalized/reordered comparison
  const result = await compareMeasures(oldMeasureId, newMeasureId);
  
  // For each file comparison
  result.comparisons.forEach(comparison => {
    console.log(`\nComparing: ${comparison.oldFileName} -> ${comparison.newFileName}`);
    
    // Generate unified diff
    const diff = Diff.createPatch(
      comparison.newFileName,
      comparison.oldText,
      comparison.newText,
      'Old Version',
      'New Version'
    );
    
    console.log(diff);
    
    // Or generate line-by-line diff for UI
    const lineDiff = Diff.diffLines(comparison.oldText, comparison.newText);
    
    lineDiff.forEach(part => {
      const color = part.added ? 'green' : 
                    part.removed ? 'red' : 'grey';
      const prefix = part.added ? '+ ' : 
                     part.removed ? '- ' : '  ';
      
      console.log(`%c${prefix}${part.value}`, `color: ${color}`);
    });
  });
}

displayMeasureDiff('measure123', 'measure456');
```

### React Component Example

```jsx
import React, { useState, useEffect } from 'react';
import { diffLines } from 'diff';

function MeasureComparison({ oldMeasureId, newMeasureId }) {
  const [comparison, setComparison] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  useEffect(() => {
    async function loadComparison() {
      try {
        setLoading(true);
        const result = await fetch(
          `/api/measures/${oldMeasureId}/compare/${newMeasureId}`,
          {
            headers: {
              'Authorization': `Bearer ${accessToken}`,
            }
          }
        );
        
        if (!result.ok) throw new Error('Failed to load comparison');
        
        const data = await result.json();
        setComparison(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    
    loadComparison();
  }, [oldMeasureId, newMeasureId]);
  
  if (loading) return <div>Loading comparison...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!comparison) return null;
  
  return (
    <div className="measure-comparison">
      {comparison.comparisons.map((comp, idx) => (
        <div key={idx} className="file-comparison">
          <h3>
            {comp.oldFileName} → {comp.newFileName}
          </h3>
          
          <div className="diff-view">
            {diffLines(comp.oldText, comp.newText).map((part, i) => (
              <div
                key={i}
                className={`diff-line ${
                  part.added ? 'added' : 
                  part.removed ? 'removed' : 
                  'unchanged'
                }`}
              >
                <pre>{part.value}</pre>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export default MeasureComparison;
```

### CSS for Diff Display

```css
.measure-comparison {
  font-family: monospace;
  padding: 20px;
}

.file-comparison {
  margin-bottom: 40px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.file-comparison h3 {
  background: #f5f5f5;
  padding: 10px;
  margin: 0;
  border-bottom: 1px solid #ddd;
}

.diff-view {
  background: white;
}

.diff-line {
  padding: 2px 10px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.diff-line pre {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.diff-line.added {
  background-color: #e6ffe6;
  color: #006600;
}

.diff-line.removed {
  background-color: #ffe6e6;
  color: #cc0000;
}

.diff-line.unchanged {
  background-color: white;
  color: #333;
}

/* Alternative: Side-by-side view */
.side-by-side {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.old-version,
.new-version {
  border: 1px solid #ddd;
  padding: 10px;
  overflow-x: auto;
}

.old-version {
  background: #fff5f5;
}

.new-version {
  background: #f5fff5;
}
```

### Advanced: Using diff2html Library

```javascript
import Diff2Html from 'diff2html';
import 'diff2html/bundles/css/diff2html.min.css';

async function renderDiff(oldMeasureId, newMeasureId, containerId) {
  // Get comparison from API
  const result = await compareMeasures(oldMeasureId, newMeasureId);
  
  // For each file comparison
  result.comparisons.forEach(comparison => {
    // Create unified diff format
    const unifiedDiff = Diff.createPatch(
      comparison.newFileName,
      comparison.oldText,
      comparison.newText,
      comparison.oldFileName,
      comparison.newFileName
    );
    
    // Render with diff2html
    const diffHtml = Diff2Html.html(unifiedDiff, {
      drawFileList: true,
      matching: 'lines',
      outputFormat: 'side-by-side'
    });
    
    document.getElementById(containerId).innerHTML = diffHtml;
  });
}

// Usage
renderDiff('measure123', 'measure456', 'diff-container');
```

## API Response Structure

```typescript
interface CqlDiffResult {
  comparisons: CqlFileComparison[];
  oldMeasureId: string;
  newMeasureId: string;
}

interface CqlFileComparison {
  oldFileName: string;  // "not found" if new file
  newFileName: string;
  oldText: string;       // Normalized text
  newText: string;       // Normalized and reordered text
}
```

## Notes

1. **Auto-reordering is enabled by default** - This provides the most meaningful diffs
2. **Text is already normalized** - No need to handle `\r` or `\t` in frontend
3. **New text is reordered** - CQL blocks are arranged to match old structure
4. **Access control is enforced** - User must have access to both measures
5. **Works with any diff library** - Output is plain text, compatible with any diff tool

## Recommended Libraries

- **diff** - Simple, lightweight, works great for line-based diffs
- **diff2html** - Beautiful HTML rendering with side-by-side view
- **react-diff-viewer** - React component with syntax highlighting
- **monaco-diff-editor** - Full VS Code diff editor in browser

