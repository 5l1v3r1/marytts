/**
 * Copyright 2007 DFKI GmbH.
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

package de.dfki.lt.signalproc.filter;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.BufferedDoubleDataSource;
import de.dfki.lt.signalproc.util.DDSAudioInputStream;
import de.dfki.lt.signalproc.util.SignalProcUtils;

/**
 * @author oytun.turk
 *
 * This class implements the complementary filter bank used in 
 *    [Levine, et. al., 1999] for multiresolution sinusoidal modeling.
 *    The filter bank consists of a collection of filter channels 
 *    (See ComplementaryFilterChannel.java for details).
 *
 * [Levine, et. al., 1999] Levine, S. N., Verma, T. S., and Smith III, J. O., "Multiresolution sinusoidal
 *    modeling for wideband audio with modifications", in Proc. of the IEEE ICASSP 1998, 
 *    Volume 6, Issue , 12-15 May 1998, pp. 3585-3588.
 */
public class ComplementaryFilterBankAnalyser {
    public int numLevels;
    public int numBands; //We always have 2^numLevels subbands
    public int baseFilterOrder;
    
    protected ComplementaryFilterBankChannelAnalyser[] channelAnalysers;
    protected double originalEnergy;
    
    public ComplementaryFilterBankAnalyser(int numLevelsIn, int baseFilterOrderIn)
    {
        if (numLevelsIn>=0)
        {
            numLevels = numLevelsIn;
            numBands = (int)Math.pow(2.0, numLevels);
            channelAnalysers = new ComplementaryFilterBankChannelAnalyser[numLevels];
            baseFilterOrder = baseFilterOrderIn;
            
            int N = baseFilterOrder;
            for (int i=0; i<numLevels; i++)
            {
                channelAnalysers[i] = new ComplementaryFilterBankChannelAnalyser(N);
                N = (int)(N*0.5);
            }
        }
    }
    
    public Subband[] apply(double[] x, int samplingRateInHz)
    {
        Subband[] subbands = null;
        int i;
        
        originalEnergy = SignalProcUtils.energy(x);

        //Multiresolution analysis
        channelAnalysers[0].apply(x);
        for (i=1; i<numLevels; i++)
            channelAnalysers[i].apply(channelAnalysers[i-1].lpfOut);
        //
        
        //Rearrange results from lower to higher frequencies
        subbands = new Subband[numLevels+1];
        int currentSamplingRate = (int)(samplingRateInHz/Math.pow(2.0, numLevels));
        double startFreqInHz = 0.0;
        double endFreqInHz = 0.5*currentSamplingRate;
        int subbandInd = 0;
        subbands[subbandInd++] = new Subband(channelAnalysers[numLevels-1].lpfOut, currentSamplingRate, startFreqInHz, endFreqInHz);

        startFreqInHz = endFreqInHz;
        for (i=numLevels; i>=1; i--)
        {
            currentSamplingRate = 2*currentSamplingRate;
            endFreqInHz = 0.5*currentSamplingRate;
            subbands[subbandInd++] = new Subband(channelAnalysers[i-1].hpfOut, currentSamplingRate, startFreqInHz, endFreqInHz);
            startFreqInHz = endFreqInHz;
         }
        //
        
        return subbands;
    }  
    
    public static void main(String[] args) throws UnsupportedAudioFileException, IOException
    {
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(args[0]));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        double [] x = signal.getAllData();
        
        int numLevels = 4;
        int N = 512;
        ComplementaryFilterBankAnalyser analyser = new ComplementaryFilterBankAnalyser(numLevels, N);
        Subband[] subbands = analyser.apply(x, samplingRate);
        
        DDSAudioInputStream outputAudio;
        AudioFormat outputFormat;
        String outFileName;
        
        //Write highpass components 0 to numLevels-1
        for (int i=0; i<subbands.length; i++)
        {
            outputFormat = new AudioFormat(subbands[i].samplingRate, inputAudio.getFormat().getSampleSizeInBits(),  inputAudio.getFormat().getChannels(), true, true);
            outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(subbands[i].waveform), outputFormat);
            outFileName = args[0].substring(0, args[0].length()-4) + "_sb" + String.valueOf(i+1) + ".wav";
            AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        }
    }
}
