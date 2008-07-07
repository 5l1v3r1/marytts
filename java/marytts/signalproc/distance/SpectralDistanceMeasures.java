/**
 * @author oytun.turk
 * 
 * A class for general purpose distance metrics to compare speech/audio signals objectively
 * 
 */

package marytts.signalproc.distance;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.signalproc.analysis.FrameBasedAnalyser;
import marytts.signalproc.analysis.LPCAnalyser;
import marytts.signalproc.analysis.LineSpectralFrequencies;
import marytts.signalproc.analysis.LPCAnalyser.LPCoeffs;
import marytts.signalproc.util.SignalProcUtils;
import marytts.signalproc.window.HammingWindow;
import marytts.util.FFTMixedRadix;
import marytts.util.MathUtils;
import marytts.util.audio.AudioDoubleDataSource;


public class SpectralDistanceMeasures {
  
    /*
     * Inverse harmonic weighting based LSF distance
     * Performs LP analysis by first preemphasizing and windowing speech frames <speechFrame1> and <speechFrame2>
     * Then, computes LSFs for the two speech frames
     * Finally, the perceptual distance between two lsf vectors are estimated.
     * Note that the input speech frames are not changed during distance computation
     * LP order is automatically decided depending on the sampling rate
     * 
     * speechFrame1: First speech frame (not windowed)
     * speechFrame2: Second speech frame (not windowed)
     * samplingRate: Sampling rate in Hz
     * 
     */
    public static double lsfDist(double [] speechFrame1, double [] speechFrame2, int samplingRate)
    {
        return lsfDist(speechFrame1, speechFrame2, samplingRate, SignalProcUtils.getLPOrder(samplingRate));
    }
    
    /*
     * Inverse harmonic weighting based LSF distance
     * Performs LP analysis by first preemphasizing and windowing speech frames <speechFrame1> and <speechFrame2>
     * Then, computes LSFs for the two speech frames
     * Finally, the perceptual distance between two lsf vectors are estimated.
     * Note that the input speech frames are not changed during distance computation
     * 
     * speechFrame1: First speech frame (not windowed)
     * speechFrame2: Second speech frame (not windowed)
     * samplingRate: Sampling rate in Hz
     * lpOrder: Desired LP analysis order
     * 
     */
    public static double lsfDist(double [] speechFrame1, double [] speechFrame2, int samplingRate, int lpOrder)
    {
        //Preemphasis
        double [] windowedSpeechFrame1 = new double[speechFrame1.length];
        System.arraycopy(speechFrame1, 0, windowedSpeechFrame1, 0, speechFrame1.length);
        windowedSpeechFrame1 = SignalProcUtils.applyPreemphasis(windowedSpeechFrame1, 0.97);
        
        double [] windowedSpeechFrame2 = new double[speechFrame2.length];
        System.arraycopy(speechFrame2, 0, windowedSpeechFrame2, 0, speechFrame2.length);
        windowedSpeechFrame2 = SignalProcUtils.applyPreemphasis(windowedSpeechFrame2, 0.97);
        //
        
        //Windowing
        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //
        
        //LP analysis
        LPCoeffs lpcs1 = LPCAnalyser.calcLPC(windowedSpeechFrame1, lpOrder);
        LPCoeffs lpcs2 = LPCAnalyser.calcLPC(windowedSpeechFrame2, lpOrder);
        //
        
        //LPC to LSF conversion
        double [] lsfs1 = LineSpectralFrequencies.lpc2lsfInHz(lpcs1.getOneMinusA(), samplingRate);
        double [] lsfs2 = LineSpectralFrequencies.lpc2lsfInHz(lpcs2.getOneMinusA(), samplingRate);
        //
        
        return getLsfDist(lsfs1, lsfs2, samplingRate);
    }
    
    public static double getLsfDist(double [] lsfs1, double [] lsfs2, int samplingRate)
    { 
        double [] lsfWgt = getInverseHarmonicLSFWeights(lsfs1);
        
        return getLsfDist(lsfs1, lsfs2, samplingRate, lsfWgt);
    }
    
    public static double getLsfDist(double [] lsfs1, double [] lsfs2, int samplingRate, double [] lsfWgt)
    {
        assert lsfs1.length == lsfs2.length;
        assert lsfs1.length == lsfWgt.length;
        
        double dist = 0.0;
        
        for (int i=0; i<lsfs1.length; i++)
            dist += 0.1*lsfWgt[i]*Math.abs(lsfs1[i]-lsfs2[i]);
        
        dist = dist*16000/samplingRate;
        dist = Math.min(20.0, dist/lsfs1.length);
        dist = 10*(dist+1e-36);
        
        return dist;
    }
    
    public static double [] getInverseHarmonicLSFWeights(double [] lsfs)
    {
        assert lsfs.length>1;
        
        int P = lsfs.length;
        
        int i;
      
        double [] lsfWgt = new double[P];
        
        lsfWgt[0] = 1.0/Math.abs(lsfs[1]-lsfs[0]);
        lsfWgt[P-1] = 0.5/Math.abs(lsfs[P-1]-lsfs[P-2]);

        for (i=1; i<=P-2; i++)
           lsfWgt[i] = 1.0/Math.min(Math.abs(lsfs[i]-lsfs[i-1]), Math.abs(lsfs[i+1]-lsfs[i]));

        //Emphasize low frequency LSFs 
        double tmp = 0.0;
        for (i=0; i<P; i++)
        {
           lsfWgt[i] = Math.exp(-0.05*i)*lsfWgt[i];
           tmp += lsfWgt[i];
        }
        
        //Normalize
        for (i=0; i<P; i++)
            lsfWgt[i] /= tmp;

        //Compress dynamic range
        tmp = 0.0;
        for (i=0; i<=P-1; i++)
        {
           lsfWgt[i] = Math.sqrt(lsfWgt[i]);
           tmp += lsfWgt[i];
        }
        
      //Normalize
        for (i=0; i<P; i++)
            lsfWgt[i] /= tmp;
        
        return lsfWgt;
    }
    
    public static double rmsLogSpectralDist(double[] speechFrame1, double[] speechFrame2, int fftSize, int lpOrder)
    {
        //Windowing
        double [] windowedSpeechFrame1 = new double[speechFrame1.length];
        System.arraycopy(speechFrame1, 0, windowedSpeechFrame1, 0, speechFrame1.length);
        
        double [] windowedSpeechFrame2 = new double[speechFrame2.length];
        System.arraycopy(speechFrame2, 0, windowedSpeechFrame2, 0, speechFrame2.length);

        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //

        double[] Xabs1 = LPCAnalyser.calcSpecFrame(speechFrame1, lpOrder, fftSize);
        double[] Xabs2 = LPCAnalyser.calcSpecFrame(speechFrame2, lpOrder, fftSize);
        
        int w;        
        double dist = 0.0;
        for (w=0; w<Xabs1.length; w++)
            dist += 10*Math.log(Xabs1[w]*Xabs1[w]) - 10*Math.log10(Xabs2[w]*Xabs2[w]);
        
        return dist;
    }

    public static double kullbackLeiblerSpectralDist(double[] speechFrame1, double[] speechFrame2, int fftSize, int lpOrder)
    {
        //Windowing
        double [] windowedSpeechFrame1 = new double[speechFrame1.length];
        System.arraycopy(speechFrame1, 0, windowedSpeechFrame1, 0, speechFrame1.length);

        double [] windowedSpeechFrame2 = new double[speechFrame2.length];
        System.arraycopy(speechFrame2, 0, windowedSpeechFrame2, 0, speechFrame2.length);

        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //

        double[] Xabs1 = LPCAnalyser.calcSpecFrame(speechFrame1, lpOrder, fftSize);
        double[] Xabs2 = LPCAnalyser.calcSpecFrame(speechFrame2, lpOrder, fftSize);

        int w;        
        double klDist = 0.0;
        for (w=0; w<Xabs1.length; w++)
            klDist += Xabs1[w]*Math.log(Xabs1[w]/(Xabs2[w]+1e-20)+1e-20);

        return klDist;
    }

    public static double kullbackLeiblerSymmetricSpectralDist(double[] speechFrame1, double[] speechFrame2, int fftSize, int lpOrder)
    {
        //Windowing
        double [] windowedSpeechFrame1 = new double[speechFrame1.length];
        System.arraycopy(speechFrame1, 0, windowedSpeechFrame1, 0, speechFrame1.length);

        double [] windowedSpeechFrame2 = new double[speechFrame2.length];
        System.arraycopy(speechFrame2, 0, windowedSpeechFrame2, 0, speechFrame2.length);

        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //

        double[] Xabs1 = LPCAnalyser.calcSpecFrame(speechFrame1, lpOrder, fftSize);
        double[] Xabs2 = LPCAnalyser.calcSpecFrame(speechFrame2, lpOrder, fftSize);

        int w;

        double klDist12 = 0.0;
        double klDist21 = 0.0;
        for (w=0; w<Xabs1.length; w++)
            klDist12 += Xabs1[w]*Math.log(Xabs1[w]/(Xabs2[w]+1e-20)+1e-20);
        for (w=0; w<Xabs2.length; w++)
            klDist21 += Xabs2[w]*Math.log(Xabs2[w]/(Xabs1[w]+1e-20)+1e-20);

        return 0.5*(klDist12+klDist21);
    }

    public static double itakuraSaitoDist(double[] speechFrame1, double[] speechFrame2, int fftSize, int lpOrder)
    {
        double[] preemphasizedFrame1 = SignalProcUtils.applyPreemphasis(speechFrame1, 0.97);
        double[] preemphasizedFrame2 = SignalProcUtils.applyPreemphasis(speechFrame2, 0.97);
        
        //Windowing
        double [] windowedSpeechFrame1 = new double[preemphasizedFrame1.length];
        System.arraycopy(preemphasizedFrame1, 0, windowedSpeechFrame1, 0, preemphasizedFrame1.length);

        double [] windowedSpeechFrame2 = new double[preemphasizedFrame2.length];
        System.arraycopy(preemphasizedFrame2, 0, windowedSpeechFrame2, 0, preemphasizedFrame2.length);

        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //
        
        int w;
        double[] Xabs1 = LPCAnalyser.calcSpecFrame(speechFrame1, lpOrder, fftSize);
        double[] Xabs2 = LPCAnalyser.calcSpecFrame(speechFrame2, lpOrder, fftSize);
        
        //Itakura-Saito distance using power spectrum: pf1/pf2 - log(pf1/pf2) - 1
        double dist = 0.0;
        for (w=0; w<Xabs1.length; w++)
            Xabs1[w] = Xabs1[w]*Xabs1[w];
        for (w=0; w<Xabs2.length; w++)
            Xabs2[w] = Xabs2[w]*Xabs2[w];
        for (w=0; w<Xabs1.length; w++)
            dist += Xabs1[w]/(Xabs2[w]+1e-20)-Math.log(Xabs1[w]/(Xabs2[w]+1e-20)+1e-20) - 1;
        
        return dist;
    }
    
    //This is in fact the symmetrical version of Itakura-Saito distance
    public static double coshDist(double[] speechFrame1, double[] speechFrame2, int fftSize, int lpOrder)
    {
        double[] preemphasizedFrame1 = SignalProcUtils.applyPreemphasis(speechFrame1, 0.97);
        double[] preemphasizedFrame2 = SignalProcUtils.applyPreemphasis(speechFrame2, 0.97);
        
        //Windowing
        double [] windowedSpeechFrame1 = new double[preemphasizedFrame1.length];
        System.arraycopy(preemphasizedFrame1, 0, windowedSpeechFrame1, 0, preemphasizedFrame1.length);

        double [] windowedSpeechFrame2 = new double[preemphasizedFrame2.length];
        System.arraycopy(preemphasizedFrame2, 0, windowedSpeechFrame2, 0, preemphasizedFrame2.length);

        HammingWindow w1 = new HammingWindow(speechFrame1.length);
        w1.apply(windowedSpeechFrame1, 0);
        HammingWindow w2 = new HammingWindow(speechFrame2.length);
        w2.apply(windowedSpeechFrame2, 0);
        //
        
        int w;
        double[] Xabs1 = LPCAnalyser.calcSpecFrame(speechFrame1, lpOrder, fftSize);
        double[] Xabs2 = LPCAnalyser.calcSpecFrame(speechFrame2, lpOrder, fftSize);
        
        //COSH distance using power spectrum: 
        // dcosh = 0.5(d12+d21) where
        // dij = Xi/Xj - log(Xi/Xj) - 1 and Xi, Xj are the power spectra under comparison
        double dist12 = 0.0;
        double dist21 = 0.0;
        for (w=0; w<Xabs1.length; w++)
            Xabs1[w] = Xabs1[w]*Xabs1[w];
        for (w=0; w<Xabs2.length; w++)
            Xabs2[w] = Xabs2[w]*Xabs2[w];
        for (w=0; w<Xabs1.length; w++)
            dist12 += Xabs1[w]/(Xabs2[w]+1e-20)-Math.log(Xabs1[w]/(Xabs2[w]+1e-20)+1e-20) - 1;
        for (w=0; w<Xabs2.length; w++)
            dist21 += Xabs2[w]/(Xabs1[w]+1e-20)-Math.log(Xabs2[w]/(Xabs1[w]+1e-20)+1e-20) - 1;
        
        return 0.5*(dist12+dist21);
    }
   
    public static void main(String[] args)
    {
        AudioInputStream inputAudio = null;
        try {
            inputAudio = AudioSystem.getAudioInputStream(new File(args[0]));
        } catch (UnsupportedAudioFileException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        if (inputAudio != null)
        {
            int samplingRate = (int)inputAudio.getFormat().getSampleRate();
            int ws = (int)(samplingRate*0.020);
            int ss = (int)(samplingRate*0.010);
            
            AudioDoubleDataSource signal = new AudioDoubleDataSource(inputAudio);
            double [] x = signal.getAllData();
            
            int numfrm = (int)((x.length-(double)ws)/ss-2.0);
            double [] frm1 = new double[ws]; 
            double [] frm2 = new double[ws];
            double lsfDist;
            
            for (int i=0; i<numfrm; i++)
            {
                System.arraycopy(x, i*ss, frm1, 0, ws);
                System.arraycopy(x, (i+1)*ss, frm2, 0, ws);
                
                lsfDist = SpectralDistanceMeasures.lsfDist(frm1, frm2, samplingRate);
                System.out.println("Distance between frame " + String.valueOf(i+1) + " and frame " + String.valueOf(i+2) + " = " + String.valueOf(lsfDist));
            }
        }
    }
}
