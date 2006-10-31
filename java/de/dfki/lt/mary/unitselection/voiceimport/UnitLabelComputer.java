package de.dfki.lt.mary.unitselection.voiceimport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Compute unit labels from phone labels.
 * @author schroed
 *
 */
public class UnitLabelComputer implements VoiceImportComponent
{
    protected File phonelabelDir;
    protected File unitlabelDir;
    protected String pauseSymbol;
    
    protected DatabaseLayout db = null;
    protected BasenameList bnl = null;
    
    /**/
    public UnitLabelComputer( DatabaseLayout setdb, BasenameList setbnl ) throws IOException
    {
        this.db = setdb;
        this.bnl = setbnl;

        phonelabelDir = new File( db.labDirName() );
        if (!phonelabelDir.exists()) throw new IOException("No such directory: "+ phonelabelDir);
        unitlabelDir = new File( db.unitLabDirName() );
        if (!unitlabelDir.exists()) unitlabelDir.mkdir();
        pauseSymbol = System.getProperty( "pause.symbol", "pau" );

    }
    
    /**/
    public boolean compute() throws IOException
    {
        System.out.println( "Computing unit labels for " + bnl.getLength() + " files." );
        System.out.println( "From phonetic label files: " + db.labDirName() + "*" + db.labExt() );
        System.out.println( "To       unit label files: " + db.unitLabDirName() + "*" + db.unitLabExt() );
        for (int i=0; i<bnl.getLength(); i++) {
            File labFile = new File( db.labDirName() + bnl.getName(i) + db.labExt() );
            if ( !labFile.exists() ) {
                System.out.println( "Utterance [" + bnl.getName(i) + "] does not have a phonetic label file." );
                System.out.println( "Removing this utterance from the base utterance list." );
                bnl.remove( bnl.getName(i) );
            }
            else {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream( labFile ), "UTF-8"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File( db.unitLabDirName() + bnl.getName(i) + db.unitLabExt() )), "UTF-8"));
                // Merge adjacent pauses into one: In a sequence of pauses,
                // only remember the last one.
                String pauseLine = null;
                String line;
                List header = new ArrayList();
                boolean readingHeader = true;
                List phoneLabels = new ArrayList();
                while ((line = in.readLine())!= null) {
                    if (readingHeader) {
                        header.add(line);
                        if (line.trim().equals("#")) {
                            // found end of header
                            readingHeader = false;
                        }
                    } else {
                        // Not reading header
                        // Verify if this is a pause unit
                        String phone = getPhone(line);
                        if (pauseSymbol.equals(phone)) {
                            pauseLine = line; // remember only the latest pause line
                        } else { // a non-pause symbol
                            if (pauseLine != null) {
                                phoneLabels.add(pauseLine);
                                pauseLine = null;
                            }
                            phoneLabels.add(line);
                        }
                    }
                }
                if (pauseLine != null) {
                    phoneLabels.add(pauseLine);
                }
                String[] phoneLabelLines = (String[]) phoneLabels.toArray(new String[0]);
                String[] unitLabelLines = toUnitLabels(phoneLabelLines);
                for (int h=0, hMax = header.size(); h<hMax; h++) {
                    out.println(header.get(h));
                }
                for (int u=0; u<unitLabelLines.length; u++) {
                    out.println(unitLabelLines[u]);
                }
                out.flush();
                out.close();
                System.out.println( "    " + bnl.getName(i) );
            }
        }
        System.out.println("Finished computing unit labels");
        return true;
    }
    
    /**/
    private String getPhone(String line)
    {
        StringTokenizer st = new StringTokenizer(line.trim());
        // The third token in each line is the label
        if (st.hasMoreTokens()) st.nextToken();
        if (st.hasMoreTokens()) st.nextToken();
        if (st.hasMoreTokens()) return st.nextToken();
        return null;
    }
    
    /**
     * Convert phone labels to unit labels. This base implementation
     * returns the phone labels; subclasses may want to override that
     * behaviour.
     * @param phoneLabels the phone labels, one phone per line, with each
     * line containing three fields: 1. the end time of the current phone,
     * in seconds, since the beginning of the file; 2. a number to be ignored;
     * 3. the phone symbol.
     * @return an array of lines, in the same format as the phoneLabels input
     * array, but with unit symbols instead of phone symbols. This array may
     * or may not have the same number of lines as phoneLabels.
     */
    protected String[] toUnitLabels(String[] phoneLabels)
    {
        return phoneLabels; 
    }
    
    public static void main(String[] args) throws IOException
    {
        new UnitLabelComputer( null, null ).compute();
    }

}
