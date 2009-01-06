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

package marytts.signalproc.analysis;

/**
 * 
 * A wrapper class to store pitch marks as integer sample indices
 * 
 * @author Oytun T&uumlrk
 */
public class PitchMarks {

    public int[] pitchMarks;
    public float[] f0s;
    public int totalZerosToPadd;
    
    //count=total pitch marks
    public PitchMarks(int count, int[] pitchMarksIn, float[] f0sIn, int totalZerosToPaddIn)
    {
        if (count>1)
        {
            pitchMarks = new int[count];
            f0s = new float[count-1];
        
            System.arraycopy(pitchMarksIn, 0, pitchMarks, 0, Math.min(pitchMarksIn.length, count));
            System.arraycopy(f0sIn, 0, f0s, 0, Math.min(f0sIn.length, count-1));
        }
        else
        {
            pitchMarks = null;
            f0s = null;
        }
        
        totalZerosToPadd = Math.max(0, totalZerosToPaddIn);
    }
}
