/**
 * Copyright 2009 DFKI GmbH.
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

package marytts.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author marc
 *
 */
public class StreamUtils {

    public static double[] readDoubleArray(DataInput stream, int len) 
    throws IOException {
        byte[] raw = new byte[len*Double.SIZE/8];
        stream.readFully(raw);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
        double[] data = new double[len];
        for (int i=0; i<len; i++) {
            data[i] = in.readDouble();
        }
        return data;
    }
    
    
    public static void writeDoubleArray(DataOutput stream, double[] data)
    throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        for (int i=0; i<data.length; i++) {
            out.writeDouble(data[i]);
        }
        out.close();
        byte[] raw = baos.toByteArray();
        assert raw.length == data.length * Double.SIZE / 8;
        stream.write(raw);
    }
}
