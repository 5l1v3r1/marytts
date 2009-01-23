/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.signalproc.analysis;

import java.io.IOException;

import marytts.util.io.MaryRandomAccessFile;

/**
 * Generic file header for binary acoustic feature files
 * 
 * @author Oytun T&uumlrk
 */
public class FeatureFileHeader {
    public int numfrm; //Total number of frames
    public int dimension; //Feature vector dimension (total)
    public float winsize; //Analysis window size in seconds
    public float skipsize; //Analysis skip size in seconds
    public int samplingRate; //Sampling rate in Hz
    
    public FeatureFileHeader()
    {
        numfrm = 0;
        dimension = 0;
        winsize = 0.020f;
        skipsize = 0.010f;
        samplingRate = 0;
    }
    
    public FeatureFileHeader(FeatureFileHeader existingHeader)
    {
        numfrm = existingHeader.numfrm;
        dimension = existingHeader.dimension;
        winsize = existingHeader.winsize;
        skipsize = existingHeader.skipsize;
        samplingRate = existingHeader.samplingRate;
    }
    
    public FeatureFileHeader(String featureFile)
    {
        try {
            readHeader(featureFile);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public boolean isIdenticalAnalysisParams(FeatureFileHeader hdr)
    {   
        if (this.numfrm!=hdr.numfrm)
            return false;
        if (this.dimension!=hdr.dimension)
            return false;
        if (this.winsize!=hdr.winsize)
            return false;
        if (this.skipsize!=hdr.skipsize)
            return false;
        if (this.samplingRate!=hdr.samplingRate)
            return false;
        else
            return true;
    }
    
    public void readHeader(String file) throws IOException
    {
        readHeader(file, false);
    }
    
    public MaryRandomAccessFile readHeader(String file, boolean bLeaveStreamOpen) throws IOException
    {
        MaryRandomAccessFile stream = new MaryRandomAccessFile(file, "rw");

        if (stream!=null)
            readHeader(stream, bLeaveStreamOpen);
        
        return stream;
    }
    
    public void readHeader(MaryRandomAccessFile stream) throws IOException
    {
        readHeader(stream, true);
    }
    
    //Baseline version does nothing!
    //It is the derived class´ responsibility to do the reading and closing the file handle
    public void readHeader(MaryRandomAccessFile stream, boolean bLeaveStreamOpen) throws IOException
    {
        numfrm = stream.readInt();
        dimension = stream.readInt();
        winsize = stream.readFloat();
        skipsize = stream.readFloat();
        samplingRate = stream.readInt();
        
        if (!bLeaveStreamOpen)
        {
            stream.close();
            stream = null;
        }
    }
    
    public void writeHeader(String file) throws IOException
    {
        writeHeader(file, false);
    }
    
    //This version returns the file output stream for further use, i.e. if you want to write additional information
    // in the file use this version
    public MaryRandomAccessFile writeHeader(String file, boolean bLeaveStreamOpen) throws IOException
    {
        MaryRandomAccessFile stream = new MaryRandomAccessFile(file, "rw");

        if (stream!=null)
        {
            writeHeader(stream);
            
            if (!bLeaveStreamOpen)
            {
                stream.close();
                stream = null;
            }
        }
        
        return stream;
    }
    
    //Baseline version does nothing!
    //It is the derived class´ responsibility to do the writing and closing the file handle
    public void writeHeader(MaryRandomAccessFile ler) throws IOException
    {   
        ler.writeInt(numfrm);
        ler.writeInt(dimension);
        ler.writeFloat(winsize);
        ler.writeFloat(skipsize);
        ler.writeInt(samplingRate);
    }
}

