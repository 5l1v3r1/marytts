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
package marytts.tools.voiceimport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Vector;

import marytts.unitselection.data.Datagram;
import marytts.util.data.MaryHeader;
import marytts.util.data.TimelineIO;
import marytts.util.data.TimelineIO.IdxField;


/**
 * The TimelineWriter class provides an interface to create or update
 * a Timeline data file in Mary format, and to feed new datagrams
 * to the timeline file.
 * 
 * @author sacha, marc
 *
 */
public class TimelineWriter extends TimelineIO {

    /****************/
    /* DATA FIELDS  */
    /****************/
    private int idxInterval;
    private long datagramZoneBytePos;
    private Vector<IdxField> indexData;
    private long prevBytePos;
    private long prevTimePos;
    
    
    /****************/
    /* CONSTRUCTORS */
    /****************/
    
    /**
     * Constructor to create a timeline.
     * 
     * @param fileName The file to read the timeline from.
     * @param procHdrString the string to use as a processing header.
     * @param reqSampleRate the sample rate requested to measure time in this timeline.
     * @param setIdxIntervalInSeconds the interval between two index entries, in seconds
     */
    public TimelineWriter( String fileName, String procHdrString, int reqSampleRate, double setIdxIntervalInSeconds ) {
        
        /* Check the arguments */
        if ( reqSampleRate <= 0 ) {
            throw new RuntimeException( "The sample rate [" + reqSampleRate + "] can't be negative or null when creating a timeline." );
        }
        if ( setIdxIntervalInSeconds <= 0.0 ) {
            throw new RuntimeException( "The index interval [" + setIdxIntervalInSeconds + "] can't be negative or null when creating a timeline." );
        }
        
        /* Open the file */
        try {
            File fid = new File( fileName );
            /* Check if the file exists and should be deleted first. */
            if ( fid.exists() ) fid.delete();
            /* open */
            raf = new RandomAccessFile( fid, "rw" );
        }
        catch ( FileNotFoundException e ) {
            throw new Error("Timeline file [" + fileName + "] was not found." );
        }
        catch ( SecurityException e ) {
            throw new Error("You do not have read access to the file [" + fileName + "]." );
        }
        
        /* Make a new header */
        try {
            /* Make a new Mary header and write it */
            maryHdr = new MaryHeader( MaryHeader.TIMELINE );
            maryHdr.writeTo( raf );
            
            /* Make a new processing header and write it */
            procHdr = new ProcHeader( procHdrString );
            procHdr.dump( raf );
            
            /* Make/write the data header */
            sampleRate = reqSampleRate;
            raf.writeInt( sampleRate );
            
            numDatagrams = 0;
            raf.writeLong( numDatagrams );
            
            /* Write the positions, with fake ones for the idx and basenames */
            datagramsBytePos = getBytePointer() + 16; // +16: account for the 2 upcoming long fields datagramsBytePos and timeIdxBytePos
            raf.writeLong( datagramsBytePos );
            timeIdxBytePos = 0;
            raf.writeLong( 0l );
            
            // Remember important facts for index creation
            idxInterval = (int)Math.round( setIdxIntervalInSeconds * (double)sampleRate );
            datagramZoneBytePos = datagramsBytePos;
            indexData = new Vector<IdxField>();
            prevBytePos = datagramsBytePos;
            prevTimePos = 0;
            
            /* Now we can output the datagrams. */
            
        }
        catch ( IOException e ) {
            throw new RuntimeException( "IOException caught when constructing a timeline writer on file [" + fileName + "]: ", e );
        }
    }
    
    

    
    /*******************/
    /* MISC. METHODS   */
    /*******************/
    
    /**
     * Output the internally maintained indexes and close the file.
     * 
     * @throws IOException
     */
    public void close() throws IOException {
        
        /* Correct the number of datagrams */
        setBytePointer( datagramsBytePos - 24l );
        raf.writeLong( numDatagrams );
        
        /* Go to the end of the file and output the time index */
        timeIdxBytePos = raf.length();
        setBytePointer( timeIdxBytePos );
        idx = new Index(idxInterval, indexData);
        idx.dump( raf );
        
        /* Register the index positions */
        setBytePointer( datagramsBytePos - 8l );
        raf.writeLong( timeIdxBytePos );
        
        /* Finally, close the random access file */
        raf.close();
    }
    
    
    
    /**
     * Feeds a file position (in bytes) and a time position (in samples) from a timeline,
     * and determines if a new index field is to be added.
     * 
     * @return the number of index fields after the feed.
     */
    private void feedIndex( long bytePosition, long timePosition ) {
        /* Get the time associated with the yet to come index field */
        long nextIdxTime = indexData.size() * (long)idxInterval;
        /* If the current time position passes the next possible index field,
         * register the PREVIOUS datagram position in the new index field */
        while ( nextIdxTime < timePosition ) {
//            System.out.println( "Hitting a new index at position\t[" + bytePosition + "," + timePosition + "]." );
//            System.out.println( "The crossed index is [" + nextIdxTime + "]." );
//            System.out.println( "The registered (previous) position is\t[" + prevBytePos + "," + prevTimePos + "]." );
//            IdxField testField = (IdxField)field.elementAt(currentNumIdx-1);
//            System.out.println( "The previously indexed position was\t[" + testField.bytePtr + "," + testField.timePtr + "]." );
            
            indexData.add( new IdxField(prevBytePos,prevTimePos) );
            nextIdxTime += idxInterval;
        }
        
        /* Memorize the observed datagram position */
        prevBytePos = bytePosition;
        prevTimePos = timePosition;
    }

    
    /**
     * Write one datagram to the timeline.
     * 
     * @param d the datagram to write.
     * @param reqSampleRate the sample rate at which the datagram duration is expressed.
     * 
     * @throws IOException
     */
    public void feed( Datagram d, int reqSampleRate ) throws IOException {
//        System.out.println( "Feeding datagram [ " + d.data.length + " , " + d.duration + " ] at pos ( "
//                + getBytePointer() + " , " + getTimePointer() + " )" );
        /* Filter the datagram through the index (to automatically add an index field if needed) */
        feedIndex( getBytePointer(), getTimePointer() );
        /* Check if the datagram needs resampling */
        if ( reqSampleRate != sampleRate ) d.setDuration(scaleTime(reqSampleRate,d.getDuration()));
        /* Then write the datagram on disk */
        d.write( raf ); // This implicitely advances the bytePointer
        /* Then advance various other pointers */
        setTimePointer( getTimePointer() + d.getDuration() );
        numDatagrams++;
//        System.out.println( "Reached pos ( " + getBytePointer() + " , " + getTimePointer() + " )" );
        
    }

    /**
     * Write a series of datagrams to the timeline.
     * 
     * @param newDatagrams an array of datagrams.
     * @param reqSampleTime the sample rate at which the datagram durations are expressed.
     * 
     * @throws IOException
     */
    public void feed( Datagram[] dArray, int reqSampleTime ) throws IOException {
        for ( int i = 0; i < dArray.length; i++ ) {
            feed( dArray[i], reqSampleTime );
        }
    }

}
