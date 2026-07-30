package com.mitansh.mljava.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable container for raw CSV data.
 * Every preprocessing step will return a new Dataset.
 */
public class Dataset {

    private final String name;
    private final List<String> headers;
    private final List<String[]> rows;

    public Dataset(String name, List<String> headers, List<String[]> rows) {
        this.name = name;
        this.headers = new ArrayList<>(headers);
        this.rows = new ArrayList<>(rows);
    }

    public String getName() {
        return name;
    }

    public List<String> getHeaders() {
        return new ArrayList<>(headers);
    }

    public List<String[]> getRows() {
        return new ArrayList<>(rows);
    }

    public int getColumnCount() {
        return headers.size();
    }

    public int getRowCount() {
        return rows.size();
    }
}