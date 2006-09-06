package de.dfki.lt.mary.unitselection.voiceimport_reorganized;


import java.io.IOException;
import java.io.EOFException;
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.util.Vector;


/**
 * The TimelineReader class provides an interface to read regularly or variably spaced
 * datagrams from a Timeline data file in Mary format.
 * 
 * @author sacha
 *
 */
public class TimelineReader extends TimelineIO {
    
    /****************/
    /* CONSTRUCTORS */
    /****************/
    
    /**
     * Constructor from an externally open random access file
     * 
     * @param raf The random access file to read the timeline from.
     */
    public TimelineReader( RandomAccessFile raf ) throws IOException {
        loadHeaderAndIndex();
    }
    
    /**
     * Constructor from a file name
     * 
     * @param fileName The file to read the timeline from
     */
    public TimelineReader( String fileName ) {
        try {
            /* Open the file */
            raf = new RandomAccessFile( fileName, "r" );
            /* Load the header and indexes */
            loadHeaderAndIndex();
        }
        catch ( FileNotFoundException e ) {
            throw new Error("Timeline file [" + fileName + "] was not found." );
        }
        catch ( SecurityException e ) {
            throw new Error("You do not have read access to the file [" + fileName + "]." );
        }
        catch ( IOException e ) {
            throw new Error("IO Exception caught when closing file [" + fileName + "]: " + e.getMessage() );
        }
    }
    
    /*****************/
    /* I/O METHODS   */
    /*****************/
    
    /*******************/
    /* MISC. METHODS   */
    /*******************/
    private long scaleTime( int reqSampleRate, long targetTimeInSamples ) {
        return( (long)Math.round( (double)(sampleRate) / (double)(reqSampleRate) * (double)(targetTimeInSamples) ) );
    }
    
    /******************/
    /* DATA ACCESSORS */
    /******************/
    
    /**
     * Skip the upcoming datagram.
     * 
     * @return true if the end of the datagram zone was encountered; false otherwise.
     * 
     * @throws IOException
     */
    public boolean skipNextDatagram() throws IOException {
        
        long datagramDuration = 0;
        int datagramSize = 0;
        
        /* If the end of the datagram zone is reached, gracefully refuse to skip */
        if ( getBytePointer() == timeIdxBytePos ) return( true );
        /* else: */
        try {
            datagramDuration = raf.readLong();
            datagramSize = raf.readInt();
        }
        /* Detect a possible EOF encounter */
        catch ( EOFException e ) {
            throw new IOException( "While skipping a datagram, EOF was met before the time index position: "
                    + "you may be dealing with a corrupted timeline file." );
        }
        
        /* Skip the data field. */
        int nBytes = raf.skipBytes( datagramSize );
        
        /* If EOF has been encountered before the expected datagram size: */
        if ( nBytes < datagramSize ) {
            throw new IOException( "Failed to skip an expected datagram: "
                    + "you may be dealing with a corrupted timeline file." );
        }
        
        /* If the read was successful, update the time pointer */
        timePtr += datagramDuration;
        
        /* Return the number of skipped bytes; in the VARIABLE case, this includes skipping the size&duration fields. */
        return( false );
    }
    
    /**
     * Read and return the upcoming datagram
     * 
     * @return the current datagram, or null if EOF was encountered; internally updates the time pointer.
     * 
     * @throws IOException
     */
    public Datagram getNextDatagram() throws IOException {
        
        Datagram d = null;
        
        /* If the end of the datagram zone is reached, gracefully refuse to read */
        if ( getBytePointer() == timeIdxBytePos ) return( null );
        /* Else, pop the datagram out of the file */
        try {
            d = new Datagram( raf );
        }
        /* Detect a possible EOF encounter */
        catch ( EOFException e ) {
            throw new IOException( "While reading a datagram, EOF was met before the time index position: "
                    + "you may be dealing with a corrupted timeline file." );
        }
        
        /* If the read was successful, update the time pointer */
        timePtr += d.duration;
        
        return( d );
    }
    
    
    /**
     * Set the file pointer to the beginning of the datagram zone, and the time pointer to 0.
     *
     */
    public void rewind() throws IOException {
        setBytePointer( datagramsBytePos );
        setTimePointer( 0 );
    }
    
    
    /**
     * Return an array of datagrams
     * 
     * @param numDatagrams the number of datagrams to read
     * 
     * @return an array of datagrams; internally updates the time pointer
     * 
     * @throws IOException
     */
    public Datagram[] getNextDatagrams( int numDatagrams ) throws IOException {
        Datagram[] buff = new Datagram[numDatagrams];
        for ( int i = 0; i < numDatagrams; i++ ) {
            buff[i] = getNextDatagram();
        }
        return( buff );
    }
    
    
    /**
     * Hop the datagrams until the one which begins or contains the desired time
     * (time is in samples; the sample rate is assumed to be that of the timeline).
     * 
     * @param targetTimeInSamples the time location to reach
     * 
     * @return true if the requested time comes before the current time location in the timeline
     *         (this is an error case, because one can only hop forward), false otherwise
     * 
     * @throws IOException
     */
    private boolean hopToTime( long targetTimeInSamples ) throws IOException {
        /* If the requested time is before the current time location, we cannot hop backwards to reach it;
         * send an error case. */
        if ( targetTimeInSamples < getTimePointer() ) {
            return( true );
        }
        
        /* If the current time position is the requested time [implicitely, if (targetTimeInSample == getTimePointer())],
         * do nothing, you are already at the right position */
        
        /* Else hop: */
        if ( targetTimeInSamples > getTimePointer() ) {
            long byteBefore = 0;
            long timeBefore = 0;
            /* Hop until the datagram which comes just after the requested time */
            while ( getTimePointer() <= targetTimeInSamples ) { // Stop after the requested time, we will step back
                // to the correct time in case of equality
                timeBefore = getTimePointer();
                byteBefore = getBytePointer();
                skipNextDatagram();
                // System.out.println( "Et hop: ( " + byteBefore + " , " + timeBefore + " ) TO ( " + getBytePointer() + " , " + getTimePointer() + " )" );
            }
            /* Do one step back so that the pointed datagram contains the requested time */
            setBytePointer( byteBefore );
            setTimePointer( timeBefore );
            // System.out.println( "Hopping finish: ( " + getBytePointer() + " , " + getTimePointer() + " )" );
        }
        /* Return "false", which means "no error" */
        return( false );
    }
    
    
    /**
     * Hop the datagrams until the desired time.
     * 
     * @param reqSampleRate the sample rate for the time specification
     * @param targetTimeInSamples the desired target time, in samples relative to the above sample rate
     * 
     * @throws IOException
     */
    private boolean hopToTime( int reqSampleRate, long targetTimeInSamples ) throws IOException {
        /* Resample the requested time location, in case the sample times are different between
         * the request and the timeline */
        long resampledTime = scaleTime(reqSampleRate,targetTimeInSamples);
        /* Then call the regular hopToTime() function */
        return( hopToTime( resampledTime ) );
    }
    
    
    /**
     * Hop the datagrams until the time closest to the desired time
     * (time is in samples; the sample rate is assumed to be that of the timeline).
     * 
     * @param targetTimeInSamples the time location to reach
     * 
     * @return true if the requested time comes before the current time location in the timeline
     *         (this is an error case, because one can only hop forward), false otherwise
     * 
     * @throws IOException
     */
    private boolean hopToClosest( long targetTimeInSamples ) throws IOException {
        /* If the requested time is before the current time location, we cannot hop backwards to reach it;
         * send an error case. */
        if ( targetTimeInSamples < getTimePointer() ) {
            return( true );
        }
        
        /* If the current time position is the requested time [implicitely, if (targetTimeInSample == getTimePointer())],
         * do nothing, you are already at the right position. */
        
        /* Else hop: */
        if ( targetTimeInSamples > getTimePointer() ) {
            long byteBefore = 0;
            long timeBefore = getTimePointer();
            /* - first, browse the file and detect the crossing of the requested time: */
            while ( getTimePointer() < targetTimeInSamples ) { // Stop on or after the requested time
                timeBefore = getTimePointer();
                byteBefore = getBytePointer();
                skipNextDatagram();
            }
            /* - then, refine the decision by choosing the closest datagram: */
            if ( (targetTimeInSamples - timeBefore) < (getTimePointer() - targetTimeInSamples) ) {
                /* If the time difference with the datagram before is smaller than the time difference
                 * with the datagram after, then step one datagram back. */
                setBytePointer( getBytePointer() - byteBefore );
                setTimePointer( timeBefore );
            }
        }
        /* Return "false", which means "no error" */
        return( false );
    }
    
    
    /**
     * Go to the datagram which contains the requested time location,
     * across the whole timeline (as opposed to within a local time in a file)
     * 
     * @param targetTimeInSamples the requested time location, in samples relative to reqSampleRate
     * @param reqSampleRate the sample rate for the requested time
     * 
     * @throws IOException
     */
    public void gotoTime( long targetTimeInSamples, int reqSampleRate ) throws IOException {
        
        /* Resample the requested time location, in case the sample times are different between
         * the request and the timeline */
        long scaledTargetTime = scaleTime(reqSampleRate,targetTimeInSamples);
        /* Seek for the time index which comes just before the requested time */
        IdxField idxFieldBefore = idx.getIdxFieldBefore( scaledTargetTime );
        // System.out.println( "IDXFIELDBEF = ( " + idxFieldBefore.bytePtr + " , " + idxFieldBefore.timePtr + " )" );
        /* Then jump to the indexed datagram */
        setTimePointer( idxFieldBefore.timePtr );
        setBytePointer( idxFieldBefore.bytePtr );
        /* Then hop until the closest datagram: */
        if ( hopToTime( scaledTargetTime ) ) {
            throw new RuntimeException( "Trying to hop to a time location before the current time position."
                    + " Can't hop backwards. (This should never happen!)" );
        }
        // System.out.println( "Position after hopping: ( " + getBytePointer() + " , " + getTimePointer() + " )" );
        
    }
    
    /**
     * Get the datagrams spanning a particular time range from a particular time location
     * 
     * @param targetTimeInSamples the requested position, in samples
     * @param timeSpanInSamples the requested time span, in samples
     * @param reqSampleRate the sample rate for the requested times
     * 
     * @return an array of datagrams
     */
    public Datagram[] getDatagrams( long targetTimeInSamples, long timeSpanInSamples, int reqSampleRate ) throws IOException {
        /* We are going to store the datagrams first in a vector, because we don't know how many datagrams we will
         * end up with, and vectors are easier to grow in size than arrays. */
        Vector v = new Vector( 32, 32 );
        /* Let's go to the requested time... */
        gotoTime( targetTimeInSamples, reqSampleRate );
        /* ... and read datagrams across the requested timeSpan: */
        long endTimeInSamples = targetTimeInSamples + timeSpanInSamples;
        endTimeInSamples = scaleTime(reqSampleRate,endTimeInSamples);
        while( getTimePointer() < endTimeInSamples ) {
            v.add( getNextDatagram() );
        }
        
        /* Cast the vector into an array of datagrams (an array of byte arrays),
         * and return it */
        return( (Datagram[])( v.toArray( new Datagram[0] ) ) );
    }
    
}