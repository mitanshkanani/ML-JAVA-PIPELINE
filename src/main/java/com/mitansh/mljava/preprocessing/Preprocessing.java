package com.mitansh.mljava.preprocessing;

import com.mitansh.mljava.data.Dataset;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Preprocessing pipeline — Step 1: Column Deletion.
 *
 * Flow:
 *   1. Display total null values in the dataset.
 *   2. Display null values per column.
 *   3. Interactive column selector (arrow keys + Enter to toggle, Proceed to continue).
 *   4. Delete selected columns from the dataset.
 *   5. Save the cleaned dataset as originalname_columndel.csv.
 */
public class Preprocessing {

    private static final String DATA_FOLDER = "data";

    private Terminal terminal;
    private int cursorIndex = 0;
    private int lastRenderedLineCount = 0;

    // -------------------------------------------------------
    // Public API
    // -------------------------------------------------------

    /**
     * Runs the full column-deletion preprocessing step.
     * Returns the cleaned Dataset.
     */
    public Dataset runColumnDeletion(Dataset dataset) {
        // Step 1 & 2: Null value analysis (printed to stdout)
        Map<String, Integer> nullCounts = computeNullCounts(dataset);
        int totalNulls = nullCounts.values().stream().mapToInt(Integer::intValue).sum();
        printNullAnalysis(dataset, nullCounts, totalNulls);

        // Pause so user can read the analysis before column selector appears
        waitForEnter();

        // Step 3: Interactive column selector (null counts shown inline per column)
        List<String> columnsToDelete = selectColumnsToDelete(dataset.getHeaders(), nullCounts);

        // Step 4: Delete selected columns
        Dataset cleaned;
        if (columnsToDelete.isEmpty()) {
            System.out.println("\nNo columns selected for deletion. Dataset unchanged.");
            cleaned = dataset;
        } else {
            cleaned = removeColumns(dataset, columnsToDelete);
            System.out.println("\nDeleted columns: " + columnsToDelete);
            System.out.println("Remaining columns: " + cleaned.getColumnCount());
        }

        // Step 5: Save cleaned dataset
        String outputPath = buildOutputPath(dataset.getName());
        saveCSV(cleaned, outputPath);
        System.out.println("Saved: " + outputPath);

        return cleaned;
    }

    // -------------------------------------------------------
    // Step 1 & 2: Null value analysis
    // -------------------------------------------------------

    /**
     * Counts null/empty values per column.
     * A value is considered null if it is empty, blank, or literally "null" (case-insensitive).
     */
    private Map<String, Integer> computeNullCounts(Dataset dataset) {
        List<String> headers = dataset.getHeaders();
        List<String[]> rows = dataset.getRows();

        // Use LinkedHashMap to preserve column order
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String header : headers) {
            counts.put(header, 0);
        }

        for (String[] row : rows) {
            for (int col = 0; col < headers.size(); col++) {
                String value = (col < row.length) ? row[col] : "";
                if (isNullValue(value)) {
                    counts.merge(headers.get(col), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private boolean isNullValue(String value) {
        if (value == null) return true;
        String trimmed = value.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("na")
                || trimmed.equalsIgnoreCase("n/a") || trimmed.equalsIgnoreCase("nan");
    }

    private void printNullAnalysis(Dataset dataset, Map<String, Integer> nullCounts, int totalNulls) {
        System.out.println();
        System.out.println("======================================");
        System.out.println("  Missing Value Analysis");
        System.out.println("======================================");
        System.out.println();
        System.out.println("  Total Null Values : " + totalNulls);
        System.out.println();

        // Column-wise null counts (like df.isnull().sum())
        int maxHeaderLen = nullCounts.keySet().stream()
                .mapToInt(String::length)
                .max()
                .orElse(10);

        for (Map.Entry<String, Integer> entry : nullCounts.entrySet()) {
            String padded = padRight(entry.getKey(), maxHeaderLen);
            System.out.println("  " + padded + " -> " + entry.getValue());
        }

        System.out.println();
        System.out.println("======================================");
    }

    // -------------------------------------------------------
    // Step 3: Interactive column selection (JLine)
    // -------------------------------------------------------

    /**
     * Shows an interactive terminal screen where the user can
     * toggle columns for deletion using arrow keys + Enter,
     * then press Enter on "Proceed" to continue.
     *
     * Does NOT clear the screen — null analysis stays visible above.
     * Uses ANSI cursor movement to redraw only the selector portion in-place.
     * Shows null counts inline next to each column name.
     */
    private List<String> selectColumnsToDelete(List<String> headers, Map<String, Integer> nullCounts) {
        Set<Integer> selected = new LinkedHashSet<>();
        cursorIndex = 0;
        lastRenderedLineCount = 0;

        // Total items = columns + 1 (the "Proceed" option)
        int totalItems = headers.size() + 1;
        int proceedIndex = headers.size();

        try {
            initializeTerminal();

            while (true) {
                renderColumnSelector(headers, nullCounts, selected, proceedIndex);
                int firstByte = terminal.reader().read();

                if (firstByte == '\r' || firstByte == '\n') {
                    if (cursorIndex == proceedIndex) {
                        // User pressed Enter on Proceed → exit loop
                        break;
                    } else {
                        // Toggle selection on the current column
                        if (selected.contains(cursorIndex)) {
                            selected.remove(cursorIndex);
                        } else {
                            selected.add(cursorIndex);
                        }
                    }
                }

                if (firstByte == 27) { // ESC sequence (arrow keys)
                    int nextByte = terminal.reader().read(200);
                    if (nextByte == -1) continue; // lone ESC, ignore

                    if (nextByte == '[' || nextByte == 'O') {
                        int arrowByte = terminal.reader().read(200);
                        switch (arrowByte) {
                            case 'A': // UP
                                cursorIndex = ((cursorIndex - 1) % totalItems + totalItems) % totalItems;
                                break;
                            case 'B': // DOWN
                                cursorIndex = (cursorIndex + 1) % totalItems;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to run column selection UI.", e);
        } finally {
            closeTerminal();
        }

        // Convert selected indices to column names
        List<String> result = new ArrayList<>();
        for (int idx : selected) {
            result.add(headers.get(idx));
        }
        return result;
    }

    /**
     * Renders the column selector in-place.
     * On re-renders, moves cursor up and overwrites only the selector lines,
     * preserving the null analysis and preview output above.
     */
    private void renderColumnSelector(List<String> headers, Map<String, Integer> nullCounts,
                                       Set<Integer> selected, int proceedIndex) {
        // On re-render, move cursor up to start of previous render and clear downward
        if (lastRenderedLineCount > 0) {
            terminal.writer().print("\033[" + lastRenderedLineCount + "A\r\033[J");
        }

        int lineCount = 0;

        // Section title (simple divider — no full ML-JAVA-PIPELINE header)
        String divider = repeat('─', Math.min(terminal.getWidth(), 60));
        printLine(divider); lineCount++;
        printLine("  Select Columns To Delete"); lineCount++;
        printLine(divider); lineCount++;
        printLine(""); lineCount++;
        printLine("  Press Enter to toggle selection, navigate to Proceed when done."); lineCount++;
        printLine(""); lineCount++;

        // Column list with null counts shown inline
        int maxHeaderLen = headers.stream()
                .mapToInt(String::length)
                .max()
                .orElse(10);

        for (int i = 0; i < headers.size(); i++) {
            boolean isCursor = (i == cursorIndex);
            boolean isSelected = selected.contains(i);

            String checkbox = isSelected ? "[x]" : "[ ]";
            String prefix = isCursor ? "❯ " : "  ";
            int nullCount = nullCounts.getOrDefault(headers.get(i), 0);
            String colName = padRight(headers.get(i), maxHeaderLen);
            String line = "  " + prefix + checkbox + " " + colName + "  (" + nullCount + " nulls)";

            if (isCursor) {
                AttributedString styled = new AttributedString(
                        line,
                        AttributedStyle.DEFAULT
                                .foreground(AttributedStyle.BLACK)
                                .background(AttributedStyle.CYAN));
                printStyledLine(styled);
            } else if (isSelected) {
                AttributedString styled = new AttributedString(
                        line,
                        AttributedStyle.DEFAULT
                                .foreground(AttributedStyle.RED));
                printStyledLine(styled);
            } else {
                printLine(line);
            }
            lineCount++;
        }

        // Proceed option
        printLine(""); lineCount++;
        boolean isCursorOnProceed = (cursorIndex == proceedIndex);
        String proceedLine = isCursorOnProceed ? "  ❯ ▶ Proceed" : "    ▶ Proceed";
        if (isCursorOnProceed) {
            AttributedString styled = new AttributedString(
                    proceedLine,
                    AttributedStyle.DEFAULT
                            .foreground(AttributedStyle.BLACK)
                            .background(AttributedStyle.GREEN));
            printStyledLine(styled);
        } else {
            printLine(proceedLine);
        }
        lineCount++;

        // Footer
        printLine(""); lineCount++;
        printLine(divider); lineCount++;
        printLine("  ↑↓ Navigate    Enter Toggle/Proceed"); lineCount++;

        lastRenderedLineCount = lineCount;
        terminal.flush();
    }

    // -------------------------------------------------------
    // Step 4: Column removal
    // -------------------------------------------------------

    /**
     * Returns a new Dataset with the specified columns removed.
     */
    private Dataset removeColumns(Dataset dataset, List<String> columnsToDelete) {
        List<String> oldHeaders = dataset.getHeaders();
        Set<String> deleteSet = new HashSet<>(columnsToDelete);

        // Find indices to keep
        List<Integer> keepIndices = new ArrayList<>();
        List<String> newHeaders = new ArrayList<>();
        for (int i = 0; i < oldHeaders.size(); i++) {
            if (!deleteSet.contains(oldHeaders.get(i))) {
                keepIndices.add(i);
                newHeaders.add(oldHeaders.get(i));
            }
        }

        // Rebuild rows with only kept columns
        List<String[]> newRows = new ArrayList<>();
        for (String[] oldRow : dataset.getRows()) {
            String[] newRow = new String[keepIndices.size()];
            for (int j = 0; j < keepIndices.size(); j++) {
                int idx = keepIndices.get(j);
                newRow[j] = (idx < oldRow.length) ? oldRow[idx] : "";
            }
            newRows.add(newRow);
        }

        return new Dataset(dataset.getName(), newHeaders, newRows);
    }

    // -------------------------------------------------------
    // Step 5: Save CSV
    // -------------------------------------------------------

    /**
     * Saves the Dataset as a CSV file.
     */
    private void saveCSV(Dataset dataset, String outputPath) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
            // Write header row
            bw.write(String.join(",", dataset.getHeaders()));
            bw.newLine();

            // Write data rows
            for (String[] row : dataset.getRows()) {
                bw.write(joinCSVRow(row));
                bw.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving CSV: " + e.getMessage());
        }
    }

    /**
     * Joins a row into a CSV line, quoting fields that contain commas or quotes.
     */
    private String joinCSVRow(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(",");
            String field = row[i];
            if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
                sb.append("\"").append(field.replace("\"", "\"\"")).append("\"");
            } else {
                sb.append(field);
            }
        }
        return sb.toString();
    }

    /**
     * Builds the output path: data/originalname_columndel.csv
     * e.g. titanic.csv → data/titanic_columndel.csv
     */
    private String buildOutputPath(String originalFileName) {
        String baseName;
        if (originalFileName.toLowerCase().endsWith(".csv")) {
            baseName = originalFileName.substring(0, originalFileName.length() - 4);
        } else {
            baseName = originalFileName;
        }
        return DATA_FOLDER + "/" + baseName + "_columndel.csv";
    }

    // -------------------------------------------------------
    // Pause helper
    // -------------------------------------------------------

    /**
     * Pauses execution until the user presses Enter.
     * Uses Scanner on System.in (normal cooked mode — no JLine terminal open at this point).
     */
    private void waitForEnter() {
        System.out.println();
        System.out.print("  Press Enter to continue...");
        new Scanner(System.in).nextLine();
    }

    // -------------------------------------------------------
    // Terminal helpers (same pattern as TerminalUI)
    // -------------------------------------------------------

    private void initializeTerminal() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build();
        terminal.enterRawMode();
    }

    private void closeTerminal() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void printLine(String text) {
        terminal.writer().println(text);
    }

    private void printStyledLine(AttributedString text) {
        terminal.writer().println(text.toAnsi());
    }

    private String repeat(char c, int count) {
        char[] chars = new char[Math.max(count, 0)];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }
}
