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
package marytts.signalproc.sinusoidal.hntm.analysis;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import marytts.signalproc.sinusoidal.BaseSinusoidalSpeechFrame;
import marytts.util.math.ArrayUtils;

/**
 * @author oytun.turk
 *
 */
public class HntmSpeechFrame extends BaseSinusoidalSpeechFrame 
{
    public FrameHarmonicPart h; //Harmonics component (lower frequencies which are less than maximum frequency of voicing)
    public FrameNoisePart n; //Noise component (upper frequencies)
    
    public float f0InHz;
    public float maximumFrequencyOfVoicingInHz; //If 0.0, then the frame is unvoiced
    public float tAnalysisInSeconds; //Difference between middle of analysis current frame and previous analysis frame in seconds
    
    public HntmSpeechFrame()
    {
        this(0.0f);
    }
    
    public HntmSpeechFrame(float f0InHzIn)
    {
        h = new FrameHarmonicPart();
        n = null;
        f0InHz = f0InHzIn;
        maximumFrequencyOfVoicingInHz = 0.0f;
        tAnalysisInSeconds = -1.0f;
    }
    
    public HntmSpeechFrame(HntmSpeechFrame existing)
    {
        h = new FrameHarmonicPart(existing.h);
        
        if (existing.n instanceof FrameNoisePartLpc)
            n = new FrameNoisePartLpc((FrameNoisePartLpc)existing.n);
        else if (existing.n instanceof FrameNoisePartPseudoHarmonic)
            n = new FrameNoisePartPseudoHarmonic((FrameNoisePartPseudoHarmonic)existing.n);
        else if (existing.n instanceof FrameNoisePartWaveform)
            n = new FrameNoisePartWaveform((FrameNoisePartWaveform)existing.n);
        
        f0InHz = existing.f0InHz;
        maximumFrequencyOfVoicingInHz = existing.maximumFrequencyOfVoicingInHz;
        tAnalysisInSeconds = existing.tAnalysisInSeconds;   
    }
    
    public HntmSpeechFrame(DataInputStream dis, int noiseModel) throws IOException, EOFException
    {
        f0InHz = dis.readFloat();
        maximumFrequencyOfVoicingInHz = dis.readFloat();
        tAnalysisInSeconds = dis.readFloat();
        h = new FrameHarmonicPart(dis);

        if (noiseModel==HntmAnalyzerParams.LPC)
            n = new FrameNoisePartLpc(dis);
        else if (noiseModel==HntmAnalyzerParams.PSEUDO_HARMONIC)
            n = new FrameNoisePartPseudoHarmonic(dis);
        else if (noiseModel==HntmAnalyzerParams.WAVEFORM)
            n = new FrameNoisePartWaveform(dis); 
    }
    
    public boolean equals(HntmSpeechFrame other)
    {
        if (!h.equals(other.h)) return false;
        if (!n.equals(other.n)) return false;
        if (f0InHz!=other.f0InHz) return false;
        if (maximumFrequencyOfVoicingInHz!=other.maximumFrequencyOfVoicingInHz) return false;
        if (tAnalysisInSeconds!=other.tAnalysisInSeconds) return false;
        
        return true;
    }
    
    //Returns the size of this object in bytes
    public int getLength()
    {
        return 4*3 + h.getLength() + n.getLength();
    }
    
    public void write( DataOutput out ) throws IOException 
    {
        out.writeFloat(f0InHz);
        out.writeFloat(maximumFrequencyOfVoicingInHz);
        out.writeFloat(tAnalysisInSeconds);
        h.write(out);
        n.write(out);
    }
}

