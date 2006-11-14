/**
 * Copyright 2004-2006 DFKI GmbH.
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

package de.dfki.lt.signalproc.process;

import java.io.File;
import java.util.Arrays;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import de.dfki.lt.signalproc.analysis.LPCAnalyser;
import de.dfki.lt.signalproc.analysis.LPCAnalyser.LPCoeffs;
import de.dfki.lt.signalproc.filter.FIRFilter;
import de.dfki.lt.signalproc.window.Window;
import de.dfki.lt.util.ArrayUtils;
import de.dfki.lt.signalproc.util.AudioDoubleDataSource;
import de.dfki.lt.signalproc.util.BufferedDoubleDataSource;
import de.dfki.lt.signalproc.util.DDSAudioInputStream;
import de.dfki.lt.signalproc.util.DoubleDataSource;
import de.dfki.lt.signalproc.util.SequenceDoubleDataSource;

/**
 * @author Marc Schr&ouml;der
 *
 */
public class LSFInterpolator extends LPCAnalysisResynthesis
{
    protected FrameProvider otherAudioFrames;
    protected double r;
    
    /**
     * Create an LSF-based interpolator.
     * @param otherAudioFrames the signal with which to interpolate the signal
     * going through this signal.
     * @param p the order of LPC analysis
     * @param r the interpolation ratio, between 0 and 1: <code>new = r * this + (1-r) * other</code>
     */
    public LSFInterpolator(FrameProvider otherAudioFrames, int p, double r)
    {
        super(p);
        if (r < 0 || r > 1) throw new IllegalArgumentException("Mixing ratio r must be between 0 and 1");
        this.otherAudioFrames = otherAudioFrames;
        this.r = r;
    }
    
    /**
     * Process the LPC coefficients in place. This implementation converts
     * the LPC coefficients into line spectral frequencies, and interpolates
     * between these and the corresponding frame in the "other" signal.
     * @param a the LPC coefficients
     */
    protected void processLPC(LPCoeffs coeffs, double[] residual) 
    {
        double[] frame = otherAudioFrames.getNextFrame();
        LPCoeffs otherCoeffs = LPCAnalyser.calcLPC(frame, p);
        double[] lsf = coeffs.getLSF();
        double[] otherlsf = otherCoeffs.getLSF();
        assert lsf.length == otherlsf.length;
        // now interpolate between the two:
        for (int i=0; i<lsf.length; i++)
            lsf[i] = (1-r)*lsf[i] + r*otherlsf[i];
        coeffs.setLSF(lsf);
        // Adapt residual gain to also interpolate average energy:
        double gainFactor = Math.sqrt((1-r)*coeffs.getGain()*coeffs.getGain() + r*otherCoeffs.getGain()*otherCoeffs.getGain())/coeffs.getGain();
//        System.out.println("Gain:" + coeffs.getGain() + ", otherGain:"+otherCoeffs.getGain()+", factor="+gainFactor);
        for (int i=0; i<residual.length; i++)
            residual[i] *= gainFactor;
        
    }


    public static void main(String[] args) throws Exception
    {
        long startTime = System.currentTimeMillis();
        double r;
        String file1;
        String file2;
        if (args.length >= 3) {
            r = Double.valueOf(args[0]).doubleValue();
            file1 = args[1];
            file2 = args[2];
        } else {
            r = 0.5;
            file1 = args[0];
            file2 = args[1];
        }
        AudioInputStream inputAudio = AudioSystem.getAudioInputStream(new File(file1));
        int samplingRate = (int)inputAudio.getFormat().getSampleRate();
        AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
        AudioInputStream otherAudio = AudioSystem.getAudioInputStream(new File(file2));
        DoubleDataSource otherSource = new AudioDoubleDataSource(otherAudio);
        int frameLength = Integer.getInteger("signalproc.lpcanalysisresynthesis.framelength", 512).intValue();
        int predictionOrder = Integer.getInteger("signalproc.lpcanalysisresynthesis.predictionorder", 20).intValue();
        DoubleDataSource padding1 = new BufferedDoubleDataSource(new double[3*frameLength/4]);
        DoubleDataSource paddedOtherSource = new SequenceDoubleDataSource(new DoubleDataSource[]{padding1, otherSource});
        FrameProvider newResidualAudioFrames = new FrameProvider(paddedOtherSource, Window.get(Window.HANN, frameLength, 0.5), frameLength, frameLength/4, samplingRate, true);
        FrameOverlapAddSource foas = new FrameOverlapAddSource(signal, Window.HANN, false, frameLength, samplingRate,
                new LSFInterpolator(newResidualAudioFrames, predictionOrder, r));
        DDSAudioInputStream outputAudio = new DDSAudioInputStream(new BufferedDoubleDataSource(foas), inputAudio.getFormat());
        String outFileName = file1.substring(0, file1.length()-4) + "_" + file2.substring(file2.lastIndexOf("\\")+1, file2.length()-4)+"_"+r+".wav";
        AudioSystem.write(outputAudio, AudioFileFormat.Type.WAVE, new File(outFileName));
        long endTime = System.currentTimeMillis();
        int audioDuration = (int) (AudioSystem.getAudioFileFormat(new File(file1)).getFrameLength() / (double)samplingRate * 1000);
        System.out.println("LSF-based interpolatin took "+ (endTime-startTime) + " ms for "+ audioDuration + " ms of audio");
        
    }

}
