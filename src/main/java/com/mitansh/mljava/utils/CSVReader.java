package com.mitansh.mljava.utils;

import com.mitansh.mljava.data.Dataset;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads CSV files and builds a Dataset.
 * Does NOT perform preprocessing or transformation.
 */
public class CSVReader {

    public Dataset read(String csvPath) throws IOException {
        String name = extractFileName(csvPath);
        List<String> lines = readAllLines(csvPath);
        if (lines.isEmpty()) {
            return new Dataset(name, List.of(), List.of());
        }

        List<String> headers = parseCSVLine(lines.get(0));
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            rows.add(parseCSVLine(lines.get(i)).toArray(new String[0]));
        }
        return new Dataset(name, headers, rows);
    }

    public void previewCSV(String csvPath) {
        try {
            Dataset dataset = read(csvPath);
            printPreview(dataset);
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
    }

    // -------------------------------------------------------
    // File helpers
    // -------------------------------------------------------

    private List<String> readAllLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Properly parses a CSV line, respecting quoted fields that may contain commas.
     * Returns a List<String> of fields.
     */
    private List<String> parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Toggle quote state; two consecutive quotes "" are escaped and
                // should be treated as a single quote inside the field.
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++; // skip next quote
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (c == ',' && !insideQuotes) {
                // End of field
                fields.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        // Last field
        fields.add(currentField.toString().trim());

        return fields;
    }

    private String extractFileName(String path) {
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    // -------------------------------------------------------
    // Preview rendering
    // -------------------------------------------------------

    private void printPreview(Dataset dataset) {
        printPreviewHeader(dataset);
        printColumnInfo(dataset);
        printDataRows(dataset);
    }

    private void printPreviewHeader(Dataset dataset) {
        System.out.println("----------------------------------------------------");
        System.out.println("Dataset Preview");
        System.out.println("----------------------------------------------------");
        System.out.println("Dataset Name:");
        System.out.println(dataset.getName());
        System.out.println();
        System.out.println("Total Columns:");
        System.out.println(dataset.getColumnCount());
        System.out.println();
        System.out.println("Column Names:");
        List<String> headers = dataset.getHeaders();
        for (int i = 0; i < headers.size(); i++) {
            System.out.println(i + " -> " + headers.get(i));
        }
        System.out.println();
        System.out.println("----------------------------------------------------");
        System.out.println("First 5 Rows");
    }

    private void printColumnInfo(Dataset dataset) {
        List<String> headersList = dataset.getHeaders();
        List<String[]> rows = dataset.getRows();
        int[] colWidths = computeColumnWidths(headersList, rows);
        printRow(headersList.toArray(new String[0]), colWidths);
    }

    private void printDataRows(Dataset dataset) {
        List<String[]> rows = dataset.getRows();
        List<String> headersList = dataset.getHeaders();
        int[] colWidths = computeColumnWidths(headersList, rows);

        int previewLimit = Math.min(5, rows.size());
        for (int i = 0; i < previewLimit; i++) {
            printRow(rows.get(i), colWidths);
        }
        if (rows.size() > 5) {
            System.out.println("... (" + (rows.size() - 5) + " more rows)");
        }
    }

    private int[] computeColumnWidths(List<String> headers, List<String[]> rows) {
        int cols = headers.size();
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = headers.get(c).length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < row.length && c < cols; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        return widths;
    }

    private void printRow(String[] cells, int[] colWidths) {
        StringBuilder sb = new StringBuilder();
        int safeCols = Math.min(cells.length, colWidths.length);
        for (int i = 0; i < safeCols; i++) {
            if (i > 0)
                sb.append("   ");
            sb.append(padRight(cells[i], colWidths[i]));
        }
        System.out.println(sb);
    }

    private String padRight(String s, int width) {
        if (s.length() >= width)
            return s;
        return s + " ".repeat(width - s.length());
    }
}