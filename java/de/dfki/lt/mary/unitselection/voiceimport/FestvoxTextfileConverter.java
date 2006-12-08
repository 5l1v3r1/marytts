package de.dfki.lt.mary.unitselection.voiceimport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.StringTokenizer;

/**
 * A class which converts a text file in festvox format
 * into a one-file-per-utterance format in a given directory.
 * @author schroed
 *
 */
public class FestvoxTextfileConverter implements VoiceImportComponent
{
    protected File textFile;
    protected File textDir;
    
    protected DatabaseLayout db = null;
    protected BasenameList bnl = null;
    
    /**/
    public FestvoxTextfileConverter( DatabaseLayout setdb, BasenameList setbnl ) throws IOException
    {
        this.db = setdb;
        this.bnl = setbnl;
    
        
    }
    
    /**/
    public boolean compute() throws IOException
    {
        //check if transcription file exists
        textFile = new File( db.baseTxtFileName() );
        if (!textFile.exists()) throw new IOException( "No such file: " + textFile );
        textDir = new File( db.txtDirName() );
        if (!textDir.exists()) textDir.mkdir();
        
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), "UTF-8"));
        String line;
        BasenameList checkList = new BasenameList();
        while ((line = in.readLine()) != null) {
            line = line.substring(line.indexOf("(")+1, line.lastIndexOf(")"));
            StringTokenizer st = new StringTokenizer(line);
            String basename = st.nextToken();
            /* If the basename list asks to process this file, then write the text file */
            if ( bnl.contains( basename ) ) {
                checkList.add( basename );
                PrintWriter out = new PrintWriter( new OutputStreamWriter(new FileOutputStream(new File( textDir, basename + db.txtExt() )), "UTF-8" ));
                String text = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                out.print(text);
                out.flush();
                out.close();
            }
            
        }
        /* Check if all the basenames requested in the basename list were present in the text file */
        BasenameList diffList = bnl.duplicate();
        diffList.remove( checkList );
        if ( diffList.getLength() != 0 ) {
            System.out.println( "WARNING: the following utterances have not been found in the file [" + db.baseTxtFileName() + "]:" );
            for ( int i = 0; i < diffList.getLength(); i++ ) {
                System.out.println( diffList.getName(i) );
            }
            System.out.println( "They will be removed from the base utterance list." );
            bnl.remove( diffList );
            return( false );
        }
        else return (true );
    }
    
    /**
     * Provide the progress of computation, in percent, or -1 if
     * that feature is not implemented.
     * @return -1 if not implemented, or an integer between 0 and 100.
     */
    public int getProgress()
    {
        return -1;
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException 
    {
        new FestvoxTextfileConverter( null, null ).compute();
    }

}
