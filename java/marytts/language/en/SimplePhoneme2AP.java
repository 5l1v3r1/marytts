/**
 * Copyright 2000-2006 DFKI GmbH.
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
package marytts.language.en;

import java.util.Locale;

import marytts.datatypes.MaryDataType;
import marytts.modules.phonemiser.AllophoneSet;
import marytts.server.MaryProperties;


/**
 * Read a simple phoneme string and generate default acoustic parameters.
 *
 * @author Marc Schr&ouml;der
 */

public class SimplePhoneme2AP extends marytts.modules.SimplePhoneme2AP
{
    public SimplePhoneme2AP()
    {
        super(MaryDataType.SIMPLEPHONEMES,
              MaryDataType.ACOUSTPARAMS,
              Locale.ENGLISH
              );
    }

    public void startup() throws Exception
    {
        allophoneSet = AllophoneSet.getAllophoneSet(MaryProperties.needFilename("english.allophoneset"));
        super.startup();
    }
}

