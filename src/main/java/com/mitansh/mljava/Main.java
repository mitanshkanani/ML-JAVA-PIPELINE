package com.mitansh.mljava;

import com.mitansh.mljava.ui.TerminalUI;
import com.mitansh.mljava.utils.CSVReader;

public class Main {

    public static void main(String[] args) {
        TerminalUI ui = new TerminalUI();
        String selectedDataset = ui.selectDataset();

        if (selectedDataset == null) {
            System.out.println("No dataset selected. Exiting.");
            return;
        }

        // Preview the selected dataset
        CSVReader reader = new CSVReader();
        reader.previewCSV("data/" + selectedDataset);

        // Future: load dataset for ML pipeline
        // Dataset raw = reader.read("data/" + selectedDataset);
        // Preprocessing prep = new Preprocessing();
        // Dataset cleaned = prep.clean(raw);
    }
}