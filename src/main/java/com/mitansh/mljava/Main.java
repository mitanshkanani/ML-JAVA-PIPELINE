package com.mitansh.mljava;

import com.mitansh.mljava.ui.TerminalUI;

public class Main {
    public static void main(String[] args) {
        TerminalUI ui = new TerminalUI();
        String dataset = ui.selectDataset();

        if (dataset == null) {
            System.out.println("No dataset selected. Exiting.");
            return;
        }

        System.out.println("Selected dataset: " + dataset);
    }
}