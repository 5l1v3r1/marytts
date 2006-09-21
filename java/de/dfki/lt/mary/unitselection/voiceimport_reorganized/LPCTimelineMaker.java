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
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import de.dfki.lt.mary.unitselection.voiceimport_reorganized.General;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.MaryHeader;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.DatabaseLayout;
import de.dfki.lt.mary.unitselection.voiceimport_reorganized.TimelineIO;

/**
 * The LPCTimelineMaker class takes a database root directory and a list of basenames,
 * and converts the related wav files into a LPC timeline in Mary format.
 * 
 * @author sacha
 */
public class LPCTimelineMaker implements VoiceImportComponent
{ 

    protected DatabaseLayout db = null;
    protected BasenameList bnl = null;
    
    public LPCTimelineMaker( DatabaseLayout setdb, BasenameList setbnl ) {
        this.db = setdb;
        this.bnl = setbnl;
    }
    
    /**
     *  Reads and concatenates a list of LPC EST tracks into one single timeline file.
     *
     */
    public boolean compute()
    {
        System.out.println("---- Importing LPC coefficients\n\n");
        System.out.println("Base directory: " + db.baseName() + "\n");
        
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
            
            /* 2) Scan all the EST LPC Track files for LPC min, LPC max and total LPC-timeline duration */
            
            System.out.println("---- Scanning for LPC min and LPC max..." );
            
            ESTTrackReader lpcFile;    // Structure that holds the LPC track data
            float[] current;             // local [min,max] vector for the current LPC track file
            float lpcMin, lpcMax, lpcRange;       // Global min/max/range values for the LPC coefficients
            float totalDuration = 0.0f;  // Accumulator for the total timeline duration
            long numDatagrams = 0l; // Total number of LPC datagrams in the timeline file
            int numLPC = 0;              // Number of LPC channels, assumed from the first LPC file
            
            /* Initialize with the first file: */
            /* - open and load */
            // System.out.println( baseNameArray[0] );
            lpcFile = new ESTTrackReader( db.lpcDirName() + baseNameArray[0] + db.lpcExt() );
            /* - get the min and the max */
            current = lpcFile.getMinMaxNo1st();
            lpcMin = current[0];
            lpcMax = current[1];
            /* - accumulate the file duration */
            totalDuration += lpcFile.getTimeSpan();
            /* - accumulate the number of datagrams: */
            numDatagrams += lpcFile.getNumFrames();
            /* - get the number of LPC channels: */
            numLPC = lpcFile.getNumChannels() - 1; // -1 => ignore the energy channel.
            System.out.println("Assuming that the number of LPC coefficients is: [" + numLPC + "] coefficients." );
            
            /* Then, browse the remaining files: */
            for ( int i = 1; i < baseNameArray.length; i++ ) {
                /* - open+load */
                // System.out.println( baseNameArray[i] );
                lpcFile = new ESTTrackReader( db.lpcDirName() + baseNameArray[i] + db.lpcExt() );
                /* - get min and max */
                current = lpcFile.getMinMaxNo1st();
                if ( current[0] < lpcMin ) { lpcMin = current[0]; }
                if ( current[1] > lpcMax ) { lpcMax = current[1]; }
                /* - accumulate and approximate of the total speech duration (to build the index) */
                totalDuration += lpcFile.getTimeSpan();
                /* - accumulate the number of datagrams: */
                numDatagrams += lpcFile.getNumFrames();
            }
            lpcRange = lpcMax - lpcMin;
            /* NOTE: accumulating the total LPC timeline duration (which is necessary for dimensioning the index)
             * from the LPC track times is slightly more imprecise than accumulating durations from the residuals,
             * but it avoids another loop through on-disk files. */
            
            System.out.println("LPCMin   = " + lpcMin );
            System.out.println("LPCMax   = " + lpcMax );
            System.out.println("LPCRange = " + lpcRange );
            
            System.out.println("---- Done." );
            
            System.out.println("---- Filtering the EST LPC tracks..." );
            
            /* 3) Open the destination timeline file */
            
            /* Make the file name */
            String lpcTimelineName = db.lpcTimelineFileName() ;
            System.out.println( "Will create the LPC timeline in file [" + lpcTimelineName + "]." );
            
            /* An example of processing header: */
            String cmdLine = "\n$ESTDIR/bin/sig2fv "
            + "-window_type hamming -factor 3 -otype est_binary -preemph 0.95 -coefs lpc -lpc_order 16 "
            + "-pm PITCHMARKFILE.pm -o LPCDIR/LPCFILE.lpc WAVDIR/WAVFILE.wav\n";
            
            /* Instantiate the TimelineWriter: */
            TimelineWriter lpcTimeline = new TimelineWriter( lpcTimelineName, cmdLine, globSampleRate, 30.0 );
            
            
            /* 4) Write the datagrams and feed the index */
            
            long totalTime = 0l;
            
            /* For each EST track file: */
            for ( int i = 0; i < baseNameArray.length; i++ ) {
                /* - open+load */
                System.out.println( baseNameArray[i] );
                lpcFile = new ESTTrackReader( db.lpcDirName() + "/" + baseNameArray[i] + db.lpcExt() );
                wav = new WavReader( db.wavDirName() + baseNameArray[i] + db.wavExt() );
                /* - Reset the frame locations in the local file */
                int frameStart = 0;
                int frameEnd = 0;
                int frameSize = 0;
                /* - For each frame in the LPC file: */
                for ( int f = 0; f < lpcFile.getNumFrames(); f++ ) {
                    
                    /* Locate the corresponding segment in the wave file */
                    frameStart = frameEnd;
                    frameEnd = (int)( lpcFile.getTime( f ) * (float)(globSampleRate) );
                    frameSize = frameEnd - frameStart;
                    
                    /* Quantize the LPC coeffs: */
                    short[] quantizedFrame = General.quantize( lpcFile.getFrame( f ), lpcMin, lpcRange );
                    float[] unQuantizedFrame = General.unQuantize( quantizedFrame, lpcMin, lpcRange );
                    /* Note: for inverse filtering (below), we will use the un-quantized values
                     *       of the LPC coefficients, so that the quantization noise is registered
                     *       into the residual (for better reconstruction of the waveform from
                     *       quantized coeffs).
                     * Warning: in the EST format, the first LPC coefficient is the filter gain,
                     *       which should not be used for the inverse filtering. */
                    
                    /* Start the resulting datagram with the LPC coefficients: */
                    ByteArrayOutputStream byteBuff = new ByteArrayOutputStream();
                    DataOutputStream datagramContents = new DataOutputStream( byteBuff );
                    for ( int k = 1; k < quantizedFrame.length; k++ ) { /* i starts at 1 to skip the gain coefficient */
                        datagramContents.writeShort( quantizedFrame[k] );
                    }
                    
                    
                    /* PERFORM THE INVERSE FILTERING with the quantized LPCs, and write the residual to the datagram: */
                    double r;
                    short[] wave = wav.getSamples();
                    int numRes = frameSize - numLPC;
                    for (int k = 0; k < numRes; k++) {
                        // try {
                        r = (double)( wave[frameStart + k] );
                        /* } catch ( ArrayIndexOutOfBoundsException e ) {
                            System.out.println( "ARGH: " + (frameStart + numLPC + k) );
                            System.out.println( "Wlen: " + wave.length );
                            System.out.println( "FrameEnd: " + frameEnd );
                            System.out.println( "FrameSize: " + frameSize );
                            return;
                        } */
                        for (int j = 0; j < numLPC; j++) {
                            // try {
                            r -= unQuantizedFrame[j] * ((double) wave[frameStart + (numLPC - 1) + (k - j)]);
                            /* } catch ( ArrayIndexOutOfBoundsException e ) {
                                System.out.println( "ARGH: " + (frameStart + numLPC + k) );
                                System.out.println( "Wlen: " + wave.length );
                                return;
                            } */
                        }
                        datagramContents.writeByte( General.shortToUlaw((short) r) );
                    }
                    
                    /* Feed the datagram to the timeline */
                    lpcTimeline.feed( new Datagram( frameSize, byteBuff.toByteArray() ) , globSampleRate );
                    totalTime += frameSize;
                }
                
            }
            
            System.out.println("---- Done." );
            
            /* 7) Print some stats and close the file */
            System.out.println( "---- LPC timeline result:");
            System.out.println( "Number of files scanned: " + baseNameArray.length );
            System.out.println( "Total speech duration: [" + totalTime + "] samples / [" + ((float)(totalTime) / (float)(globSampleRate)) + "] seconds." );
            System.out.println( "(Speech duration approximated from EST Track float times: [" + totalDuration + "] seconds.)" );
            System.out.println( "Number of frames: [" + numDatagrams + "]." );
            System.out.println( "Size of the index: [" + lpcTimeline.idx.getNumIdx() + "]." );
            System.out.println( "---- LPC timeline done.");
            
            lpcTimeline.close();
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
