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
package marytts.signalproc.sinusoidal.hnm;

import marytts.util.math.ArrayUtils;

/**
 * LPC based noise modeling for a given speech frame
 * Full spectrum LP coefficients and LP gain are used
 * Synthesis handles noise generation for upper frequencies(i.e. frequencies larger than maximum voicing freq.)
 * 
 * @author oytun.turk
 *
 */
public class FrameNoisePartLpc extends FrameNoisePart {
    
    public double[] lpCoeffs;
    public double gain; //Sqrt of prediction error
    
    public FrameNoisePartLpc()
    {
        super();
    }
    
    public FrameNoisePartLpc(FrameNoisePartLpc existing)
    {
        super();
        lpCoeffs = ArrayUtils.copy(existing.lpCoeffs);
        gain = existing.gain;
    }
    
    public FrameNoisePartLpc(double[] lpCoeffsIn, double gainIn)
    {
        super();
        
        if (lpCoeffsIn!=null)
        {
            lpCoeffs = new double[lpCoeffsIn.length];
            System.arraycopy(lpCoeffsIn, 0, lpCoeffs, 0, lpCoeffsIn.length);
        }
        else
            lpCoeffs = null;
            
        gain = gainIn;
    }

}

