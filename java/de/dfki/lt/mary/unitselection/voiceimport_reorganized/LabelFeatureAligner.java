package de.dfki.lt.mary.unitselection.voiceimport_reorganized;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import de.dfki.lt.mary.util.FileUtils;

/**
 * Compare unit label and unit feature files.
 * If they don't align, flag a problem; let the user
 * decide how to fix it -- either by editing the unit label
 * file or by editing a rawmaryxml file and recomputing the
 * features file.
 * @author schroed
 *
 */
public class LabelFeatureAligner implements VoiceImportComponent
{
    protected File unitlabelDir;
    protected File unitfeatureDir;
    protected UnitFeatureComputer featureComputer;
    protected String pauseSymbol;
    
    protected DatabaseLayout db = null;
    protected BasenameList bnl = null;
    
    public LabelFeatureAligner( DatabaseLayout setdb, BasenameList setbnl ) throws IOException
    {
        this.db = setdb;
        this.bnl = setbnl;
    
        this.unitlabelDir = new File(System.getProperty("unitlab.dir", "unitlab"));
        this.unitfeatureDir = new File(System.getProperty("unitfeatures.dir", "unitfeatures"));
        this.featureComputer = new UnitFeatureComputer( db, bnl );
        this.pauseSymbol = System.getProperty("pause.symbol", "pau");
    }
    
    /**
     * Align labels and features. For each .unitlab file in the unit label
     * directory, verify whether the chain of units given is identical to
     * the chain of units in the corresponding unit feature file.
     * For those files that are not perfectly aligned, give the user the
     * opportunity to correct alignment.
     * @return a boolean indicating whether or not the database is fully aligned.
     * @throws IOException
     */
    public boolean compute() throws IOException
    {
        String[] basenames = FileUtils.listBasenames(unitlabelDir, ".unitlab");
        System.out.println("Verifying feature-label alignment for "+basenames.length+" files");
        Map problems = new TreeMap();
        
        for (int i=0; i<basenames.length; i++) {
            String errorMessage = verifyAlignment(basenames[i]);
            System.out.print("    "+basenames[i]);
            if (errorMessage == null) {
                System.out.println(" OK");
            } else {
                problems.put(basenames[i], errorMessage);
                System.out.println(errorMessage);
            }
        }
        System.out.println("Found "+problems.size() + " problems");

        int remainingProblems = 0;
        for (Iterator it = problems.keySet().iterator(); it.hasNext(); ) {
            String basename = (String) it.next();
            String errorMessage;
            boolean tryAgain;
            do {
                System.out.print("    "+basename+": ");
                tryAgain = letUserCorrect(basename, (String)problems.get(basename));
                errorMessage = verifyAlignment(basename);
                if (errorMessage == null) {
                    System.out.println("OK");
                } else {
                    System.out.println(errorMessage);
                    problems.put(basename, errorMessage);
                    if (!tryAgain) remainingProblems++;
                }
            } while (tryAgain && errorMessage != null);
        }
        
        return remainingProblems == 0; // true exactly if all problems have been solved
    }
    
    /**
     * Verify if the feature and label files for basename align OK.
     * @param basename
     * @return null if the alignment was OK, or a String containing an error message.
     * @throws IOException
     */
    protected String verifyAlignment(String basename) throws IOException
    {
        BufferedReader labels = new BufferedReader(new InputStreamReader(new FileInputStream(new File(unitlabelDir, basename+".unitlab")), "UTF-8"));
        BufferedReader features = new BufferedReader(new InputStreamReader(new FileInputStream(new File(unitfeatureDir, basename+".feats")), "UTF-8"));
        String line;
        // Skip label file header:
        while ((line = labels.readLine()) != null) {
            if (line.startsWith("#")) break; // line starting with "#" marks end of header
        }
        // Skip features file header:
        while ((line = features.readLine()) != null) {
            if (line.trim().equals("")) break; // empty line marks end of header
        }

        // Now go through all feature file units
        boolean correct = true;
        while (correct) {
            String labelUnit = getLabelUnit(labels);
            String featureUnit = getFeatureUnit(features);
            // when featureUnit is the empty string, we have found an empty line == end of feature section
            if ("".equals(featureUnit)) break;
            if (!featureUnit.equals(labelUnit)) {
                return "Non-matching units found: feature file '"+featureUnit+"' vs. label file '"+labelUnit+"'";
            }
        }
        return null; // success
    }
    
    private String getLabelUnit(BufferedReader labelReader)
    throws IOException
    {
        String line = labelReader.readLine();
        if (line == null) return null;
        StringTokenizer st = new StringTokenizer(line.trim());
        // The third token in each line is the label
        st.nextToken(); st.nextToken();
        String unit = st.nextToken();
        return unit;
    }
    
    private String getFeatureUnit(BufferedReader featureReader)
    throws IOException
    {
        String line = featureReader.readLine();
        if (line == null) return null;
        if (line.trim().equals("")) return ""; // empty line -- signal end of section
        StringTokenizer st = new StringTokenizer(line.trim());
        // The expect that the first token in each line is the label
        String unit = st.nextToken();
        return unit;
        
    }
    
    protected boolean letUserCorrect(String basename, String errorMessage) throws IOException
    {
        int choice = JOptionPane.showOptionDialog(null,
                "Misalignment problem for "+basename+":\n"+
                errorMessage,
                "Correct alignment for "+basename,
                JOptionPane.YES_NO_CANCEL_OPTION, 
                JOptionPane.QUESTION_MESSAGE, 
                null,
                new String[] {"Edit RAWMARYXML", "Edit unit labels", "Skip"},
                null);
        switch (choice) {
        case 0: 
            editMaryXML(basename);
            break;
        case 1:
            editUnitLabels(basename);
            break;
        default: // case 2 and JOptionPane.CLOSED_OPTION
            return false; // don't verify again.
        }
        return true; // verify again
    }
    
    private void editMaryXML(String basename) throws IOException
    {
        File textDir = featureComputer.getTextDir();
        final File maryxmlFile = new File(textDir, basename+".rawmaryxml");
        if (!maryxmlFile.exists()) {
            // need to create it
            String text = FileUtils.getFileAsString(new File(textDir, basename+".txt"), "UTF-8");
            PrintWriter pw = new PrintWriter(maryxmlFile, "UTF-8");
            pw.println(UnitFeatureComputer.getMaryXMLHeaderWithInitialBoundary(featureComputer.getLocale()));
            pw.println(text);
            pw.println("</maryxml>");
            pw.close();
        }
        boolean edited = new EditFrameShower(maryxmlFile).display();
        if (edited)
            featureComputer.computeFeaturesFor(basename);
    }

    
    private void editUnitLabels(String basename) throws IOException
    {
        new EditFrameShower(new File(unitlabelDir, basename+".unitlab")).display();
    }

    public static void main(String[] args) throws IOException
    {
        boolean isAligned = new LabelFeatureAligner( null, null ).compute();
        System.out.println("The database is "+(isAligned?"":"NOT")+" perfectly aligned");
    }

    public static class EditFrameShower
    {
        protected final File file;
        protected boolean saved;
        public EditFrameShower(File file)
        {
            this.file = file;
            this.saved = false;
        }

        /**
         * Show a frame allowing the user to edit the file.
         * @param file the file to edit
         * @return a boolean indicating whether the file was saved.
         * @throws IOException
         * @throws UnsupportedEncodingException
         * @throws FileNotFoundException
         */
        public boolean display() throws IOException, UnsupportedEncodingException, FileNotFoundException
        {
            final JFrame frame = new JFrame("Edit "+file.getName());
            GridBagLayout gridBagLayout = new GridBagLayout();
            GridBagConstraints gridC = new GridBagConstraints();
            frame.setLayout( gridBagLayout );

            final JEditorPane editPane = new JEditorPane();
            editPane.setPreferredSize(new Dimension(500, 500));
            editPane.read(new InputStreamReader(new FileInputStream(file), "UTF-8"), null);
            JButton saveButton = new JButton("Save & Exit");
            saveButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        PrintWriter pw = new PrintWriter(file);
                        editPane.write(pw);
                        pw.close();
                        frame.setVisible(false);
                        setSaved(true);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            });
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    frame.setVisible(false);
                    setSaved(false);
                }
            });

            gridC.gridx = 0;
            gridC.gridy = 0;
            gridC.fill = GridBagConstraints.HORIZONTAL;
            JScrollPane scrollPane = new JScrollPane(editPane);
            scrollPane.setPreferredSize(editPane.getPreferredSize());
            gridBagLayout.setConstraints( scrollPane, gridC );
            frame.add(scrollPane);
            gridC.gridy = 1;
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout());
            buttonPanel.add(saveButton);
            buttonPanel.add(cancelButton);
            gridBagLayout.setConstraints( buttonPanel, gridC );
            frame.add(buttonPanel);
            frame.pack();
            frame.setVisible(true);
            do {
                try {
                    Thread.sleep(10); // OK, this is ugly, but I don't mind today...
                } catch (InterruptedException e) {}
            } while (frame.isVisible());
            frame.dispose();
            return saved;
        }
        
        protected void setSaved(boolean saved)
        {
            this.saved = saved;
        }

    }
}
