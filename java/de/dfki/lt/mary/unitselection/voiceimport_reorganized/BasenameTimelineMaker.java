/**
 * Portions Copyright 2006 DFKI GmbH.
 * Portions Copyright 2001 Sun Microsystems, Inc.
 * Portions Copyright 1999-2001 Language Technologies Institute, 
 * Carnegie Mellon University.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * Permission is hereby granted, free of charge, to use and distribute
 * this software and its documentation without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of this work, and to
 * permit persons to whom this work is furnished to do so, subject to
 * the following conditions:
 * 
 * 1. The code must retain the above copyright notice, this list of
 *    conditions and the following disclaimer.
 * 2. Any modifications must be clearly marked as such.
 * 3. Original authors' names are not deleted.
 * 4. The authors' names are not used to endorse or promote products
 *    derived from this software without specific prior written
 *    permission.
 *
 * DFKI GMBH AND THE CONTRIBUTORS TO THIS WORK DISCLAIM ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS, IN NO EVENT SHALL DFKI GMBH NOR THE
 * CONTRIBUTORS BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL
 * DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR
 * PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS
 * ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 */
package de.dfki.lt.mary.unitselection.voiceimport_reorganized;

import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.StringWriter;
import java.util.Properties;

import de.dfki.lt.mary.unitselection.Datagram;
import de.dfki.lt.mary.unitselection.LPCDatagram;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.General;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.MaryHeader;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.DatabaseLayout;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.TimelineIO;

/**
 * The BasenameTimelineMaker class takes a database root directory and a list of basenames,
 * and associates the basenames with absolute times in a timeline in Mary format.
 * 
 * @author sacha
 */
public class BasenameTimelineMaker implements VoiceImportComponent
{ 

    protected DatabaseLayout db = null;
    protected BasenameList bnl = null;
    
    public BasenameTimelineMaker( DatabaseLayout setdb, BasenameList setbnl ) {
        this.db = setdb;
        this.bnl = setbnl;
    }
    
    /**
     *  Reads and concatenates a list of LPC EST tracks into one single timeline file.
     *
     */
    public boolean compute()
    {
        System.out.println("---- Making a timeline for the base names\n\n");
        System.out.println("Base directory: " + db.rootDirName() + "\n");
        
        /* Export the basename list into an array of strings */
        String[] baseNameArray = bnl.getListAsArray();
        System.out.println("Processing [" + baseNameArray.length + "] utterances.\n");
        
        /* Prepare the output directory for the timelines if it does not exist */
        File timelineDir = new File( db.timelineDirName() );
        if ( !timelineDir.exists() ) {
            timelineDir.mkdir();
            System.out.println("Created output directory [" + db.timelineDirName() + "] to store the timelines." );
        }
        
        try{
            /* 1) Determine the reference sampling rate as being the sample rate of the first encountered
             *    wav file */
            WavReader wav = new WavReader( db.wavDirName() + baseNameArray[0] + db.wavExt() );
            int globSampleRate = wav.getSampleRate();
            System.out.println("---- Detected a global sample rate of: [" + globSampleRate + "] Hz." );
            
            System.out.println("---- Folding the basenames according to the pitchmarks tics..." );
            
            /* 2) Open the destination timeline file */
            
            /* Make the file name */
            String bnTimelineName = db.basenameTimelineFileName() ;
            System.out.println( "Will create the basename timeline in file [" + bnTimelineName + "]." );
            
            /* Processing header: */
            String processingHeader = "\n";
            
            /* Instantiate the TimelineWriter: */
            TimelineWriter bnTimeline = new TimelineWriter( bnTimelineName, processingHeader, globSampleRate, 30.0 );
            
            /* 3) Write the datagrams and feed the index */
            
            float totalDuration = 0.0f;  // Accumulator for the total timeline duration
            long totalTime = 0l;
            int numDatagrams = 0;
            
            /* For each EST pitchmarks track file: */
            ESTTrackReader pmFile = null;
            int duration = 0;
            for ( int i = 0; i < baseNameArray.length; i++ ) {
                /* - open+load */
                pmFile = new ESTTrackReader( db.pitchmarksDirName() + "/" + baseNameArray[i] + db.pitchmarksExt() );
                wav = new WavReader( db.wavDirName() + baseNameArray[i] + db.wavExt() );
                totalDuration += pmFile.getTimeSpan();
                duration = (int)( (double)pmFile.getTimeSpan() * (double)(globSampleRate) );
                // System.out.println( baseNameArray[i] + " -> [" + frameSize + "] samples." );
                System.out.println( baseNameArray[i] + " -> pm file says [" + duration + "] samples, wav file says ["+ wav.getNumSamples() + "] samples." );
                bnTimeline.feed( new Datagram( duration, baseNameArray[i].getBytes("UTF-8") ) , globSampleRate );
                totalTime += duration;
                numDatagrams++;
            }
            
            System.out.println("---- Done." );
            
            /* 7) Print some stats and close the file */
            System.out.println( "---- Basename timeline result:");
            System.out.println( "Number of files scanned: " + baseNameArray.length );
            System.out.println( "Total speech duration: [" + totalTime + "] samples / [" + ((float)(totalTime) / (float)(globSampleRate)) + "] seconds." );
            System.out.println( "(Speech duration approximated from EST Track float times: [" + totalDuration + "] seconds.)" );
            System.out.println( "Number of frames: [" + numDatagrams + "]." );
            System.out.println( "Size of the index: [" + bnTimeline.idx.getNumIdx() + "]." );
            System.out.println( "---- Basename timeline done.");
            
            bnTimeline.close();
        }
        catch ( SecurityException e ) {
            System.err.println( "Error: you don't have write access to the target database directory." );
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.println(e);
        }
        
        return( true );
    }

}
