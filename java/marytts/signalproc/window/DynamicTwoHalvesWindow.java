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

package marytts.signalproc.window;

import marytts.signalproc.display.FunctionGraph;
import marytts.signalproc.display.LogSpectrum;
import marytts.util.math.MathUtils;

/**
 * @author Marc Schr&ouml;der
 *
 */
public class DynamicTwoHalvesWindow extends DynamicWindow {
    protected double prescale;
    
    public DynamicTwoHalvesWindow(int windowType) {
        super(windowType);
        prescale = 1.;
    }
    
    public DynamicTwoHalvesWindow(int windowType, double prescale) {
        super(windowType);
        this.prescale = prescale;
    }

    /**
     * apply the left half of a window of the specified type to the data.
     * The left half will be as long as the given len. 
     */
    public void applyInlineLeftHalf(double[] data, int off, int len)
    {
        Window w = Window.get(windowType, 2*len, prescale);
        w.apply(data, off, data, off, 0, len);
    }

    /**
     * apply the right half of a window of the specified type to the data.
     * The right half will be as long as the given len. 
     */
    public void applyInlineRightHalf(double[] data, int off, int len)
    {
        Window w = Window.get(windowType, 2*len, prescale);
        w.apply(data, off, data, off, len, len);
    }

    public static void main(String[] args)
    {
        int samplingRate = Integer.getInteger("samplingrate", 1).intValue();
        int windowLengthMs = Integer.getInteger("windowlength.ms", 0).intValue();
        int windowLength = Integer.getInteger("windowlength.samples", 512).intValue();
        float asymmetry = 1.5f; // length of right window half relative to left half
        // If both are given, use window length in milliseconds:
        if(windowLengthMs != 0) windowLength = windowLengthMs * samplingRate / 1000;
        int leftHalfLength = windowLength/2;
        int rightHalfLength = (int) (leftHalfLength*asymmetry);
        windowLength = leftHalfLength + rightHalfLength;
        int fftSize = Math.max(4096, MathUtils.closestPowerOfTwoAbove(windowLength));
        int windowType = Window.HANNING;
        Window wLeft = Window.get(windowType, 2*leftHalfLength);
        Window wRight = Window.get(windowType, 2*rightHalfLength);
        double window[] = new double[windowLength];
        System.arraycopy(wLeft.window, 0, window, 0, leftHalfLength);
        System.arraycopy(wRight.window, rightHalfLength, window, leftHalfLength, rightHalfLength);
        
        FunctionGraph timeGraph = new FunctionGraph(0, 1./samplingRate, window);
        timeGraph.showInJFrame("Asymmetric "+wLeft.toString() + " in time domain", true, false);
        double[] fftSignal = new double[fftSize];
        // fftSignal should integrate to one, so normalise amplitudes:
        double sum = MathUtils.sum(window);
        for (int i=0; i<window.length; i++) {
            fftSignal[i] = window[i] / sum;
        }
        LogSpectrum freqGraph = new LogSpectrum(fftSignal, samplingRate);
        freqGraph.showInJFrame("Asymmetric "+wLeft.toString() + " log frequency response", true, false);
    }

    
}
